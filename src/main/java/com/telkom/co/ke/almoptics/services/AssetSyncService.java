package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.*;
import com.telkom.co.ke.almoptics.models.ApprovalWorkflow;
import com.telkom.co.ke.almoptics.models.AuditLog;
import com.telkom.co.ke.almoptics.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
/**
 * Service responsible for synchronizing assets between inventory and financial report systems.
 * Handles detection of new, existing, decommissioned, unmapped, and missing assets.
 */
@Service
public class AssetSyncService {

    private static final Logger logger = LoggerFactory.getLogger(AssetSyncService.class);
    private static final int BATCH_SIZE = 200; // Reduced batch size to prevent timeouts
    private static final int MISSING_ASSET_GRACE_PERIOD_DAYS = 14;
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 2000;
    private volatile boolean isSyncRunning = false;

    @Autowired
    private FinancialReportRepo financialReportRepo;

    @Autowired
    private ActiveInventoryRepository activeInventoryRepository;

    @Autowired
    private PassiveInventoryRepository passiveInventoryRepository;

    @Autowired
    private ItInventoryRepository itInventoryRepository;

    @Autowired
    private UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;

    @Autowired
    private UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;

    @Autowired
    private UnmappedITInventoryRepository unmappedITInventoryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ApprovalWorkflowRepository approvalWorkflowRepository;

    @Autowired
    private ApprovalWorkflowService approvalWorkflowService;

    @Autowired
    private UnmappedInventoryService unmappedInventoryService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;
//    @Autowired
//    private NotificationService notificationService;

    /**
     * Custom retry logic for database operations
     */

    private <T> T retryOperation(Supplier<T> operation, String operationName) {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                return operation.get();
            } catch (Exception e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    logger.error("Failed {} after {} attempts: {}", operationName, MAX_RETRIES, e.getMessage());
                    throw e;
                }
                long backoff = BASE_BACKOFF_MS * (long) Math.pow(2, attempt);
                logger.warn("Attempt {}/{} failed for {}, retrying after {}ms: {}", attempt, MAX_RETRIES, operationName, backoff, e.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry backoff", ie);
                }
            }
        }
        return null; // Unreachable
    }

    /**
     * Scheduled job to sync all assets daily at 1:00 AM
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void scheduledAssetSync() {
        logger.info("Starting scheduled asset synchronization");
        try {
            CompletableFuture<Void> missingSync = syncMissingAssetsAsync();
            CompletableFuture<Void> activeSync = syncActiveAssetsAsync();
            CompletableFuture<Void> passiveSync = syncPassiveAssetsAsync();
            CompletableFuture<Void> itSync = syncItAssetsAsync();
            CompletableFuture<Void> unmappedSync = rebuildUnmappedInventoriesAsync();
            CompletableFuture.allOf(missingSync, activeSync, passiveSync, itSync, unmappedSync).join();
            logger.info("Completed scheduled asset synchronization");
        } catch (Exception e) {
            logger.error("Error during scheduled asset synchronization: {}", e.getMessage());
//            notificationService.sendNotification(
//                    "Asset Sync Error",
//                    "Daily asset sync failed: " + e.getMessage()
//            );
            throw e;
        }
    }

    /**
     * Async method to sync Active assets with batching
     */
    @Async
    @Transactional(timeout = 300, readOnly = true)
    public CompletableFuture<Void> syncActiveAssetsAsync() {
        logger.info("Starting async sync for Active assets");
        return retryOperation(() -> {
            Pageable pageable = PageRequest.of(0, BATCH_SIZE);  // Start at page 0
            Slice<ActiveInventory> slice = activeInventoryRepository.findAll(pageable);  // Use Slice, not Page
            while (slice.hasContent()) {
                List<ActiveInventory> batch = slice.getContent();
                List<String> serials = batch.stream()
                        .map(ActiveInventory::getSerialNumber)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                processBatchSync(serials, "ACTIVE");
                if (!slice.hasNext()) {
                    break;  // No more data
                }
                pageable = slice.nextPageable();  // Advance to next batch
                slice = activeInventoryRepository.findAll(pageable);
            }
            logger.info("Completed async sync for Active assets");
            return CompletableFuture.completedFuture(null);
        }, "syncActiveAssetsAsync");
    }

    /**
     * Async method to sync Passive assets with batching
     */
    @Async
    @Transactional(timeout = 300, readOnly = true)  // Keep transactional; add readOnly if no writes in this method; timeout as safety net
    public CompletableFuture<Void> syncPassiveAssetsAsync() {
        logger.info("Starting async sync for Passive assets");
        return retryOperation(() -> {
            Pageable pageable = PageRequest.of(0, BATCH_SIZE);  // Start at page 0
            Slice<PassiveInventory> slice = passiveInventoryRepository.findAll(pageable);  // Use Slice instead of Page
            while (slice.hasContent()) {  // Loop until no more content
                List<PassiveInventory> batch = slice.getContent();
                List<String> identifiers = batch.stream()
                        .flatMap(asset -> Stream.of(asset.getSerial(), asset.getObjectId()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                processBatchSync(identifiers, "PASSIVE");  // Your existing batch processing
                if (!slice.hasNext()) {
                    break;  // No more data
                }
                pageable = slice.nextPageable();  // Get next batch pageable
                slice = passiveInventoryRepository.findAll(pageable);
            }
            logger.info("Completed async sync for Passive assets");
            return CompletableFuture.completedFuture(null);
        }, "syncPassiveAssetsAsync");
    }
    /**
     * Async method to sync IT assets with batching
     */
    @Async
    @Transactional(timeout = 120)
    public CompletableFuture<Void> syncItAssetsAsync() {
        logger.info("Starting async sync for IT assets");
        return retryOperation(() -> {
            Pageable pageable = PageRequest.of(0, BATCH_SIZE);
            long totalRecords = itInventoryRepository.count();
            int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

            for (int page = 0; page < totalPages; page++) {
                pageable = PageRequest.of(page, BATCH_SIZE);
                List<ItInventory> batch = itInventoryRepository.findAll(pageable).getContent();
                List<String> identifiers = batch.stream()
                        .flatMap(asset -> Stream.of(asset.getHostSerialNumber(), asset.getObjectId()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                processBatchSync(identifiers, "IT");
            }
            logger.info("Completed async sync for IT assets");
            return CompletableFuture.completedFuture(null);
        }, "syncItAssetsAsync");
    }

    /**
     * Async method to sync missing assets and check decommissioning
     */
    @Async
    @Transactional(timeout = 120)
    public CompletableFuture<Void> syncMissingAssetsAsync() {
        logger.info("Starting async sync for missing assets");
        return retryOperation(() -> {
            Pageable pageable = PageRequest.of(0, BATCH_SIZE);
            long totalRecords = financialReportRepo.count();
            int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

            for (int page = 0; page < totalPages; page++) {
                pageable = PageRequest.of(page, BATCH_SIZE);
                List<tb_FinancialReport> batch = financialReportRepo.findAll(pageable).getContent();
                batch.forEach(this::processFinancialReportAsset);
            }
            logger.info("Completed async sync for missing assets");
            return CompletableFuture.completedFuture(null);
        }, "syncMissingAssetsAsync");
    }

    /**
     * Rebuild unmapped inventories from scratch
     */
    /**
     * Rebuild unmapped inventories from scratch
     */
    @Async
    @Transactional(timeout = 120)
    public CompletableFuture<Void> rebuildUnmappedInventoriesAsync() {
        logger.info("Rebuilding unmapped inventories from scratch");
        return retryOperation(() -> {
            // Clear all existing unmapped records using native queries
            logger.info("Clearing existing unmapped inventory records");
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();

                // Delete from tb_UnmappedActive_Inventory
                retryOperation(() -> {
                    entityManager.createNativeQuery("DELETE FROM tb_unmappednode").executeUpdate();
                    return null;
                }, "deleteAllUnmappedActive");

                // Delete from tb_UnmappedPassive_Inventory
                retryOperation(() -> {
                    entityManager.createNativeQuery("DELETE FROM tb_unmappedPassive_Inventory").executeUpdate();
                    return null;
                }, "deleteAllUnmappedPassive");

                // Delete from tb_UnmappedIT_Inventory
                retryOperation(() -> {
                    entityManager.createNativeQuery("DELETE FROM tb_unmappedIT_INVENTORY").executeUpdate();
                    return null;
                }, "deleteAllUnmappedIT");

                transaction.commit();
                logAudit(null, null, "CLEAR", "CLEAR", null, "Cleared all unmapped inventory tables before rebuild");
            } catch (Exception e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                logger.error("Error clearing unmapped inventory tables: {}", e.getMessage());
                throw e;
            } finally {
                entityManager.close();
            }

            // Rebuild unmapped inventories
            Pageable pageable = PageRequest.of(0, BATCH_SIZE);
            long totalActive = activeInventoryRepository.count();
            int totalActivePages = (int) Math.ceil((double) totalActive / BATCH_SIZE);
            for (int page = 0; page < totalActivePages; page++) {
                pageable = PageRequest.of(page, BATCH_SIZE);
                List<ActiveInventory> batch = activeInventoryRepository.findAll(pageable).getContent();
                List<String> serials = batch.stream()
                        .map(ActiveInventory::getSerialNumber)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                processBatchForUnmapped(serials, "ACTIVE");
            }

            long totalPassive = passiveInventoryRepository.count();
            int totalPassivePages = (int) Math.ceil((double) totalPassive / BATCH_SIZE);
            for (int page = 0; page < totalPassivePages; page++) {
                pageable = PageRequest.of(page, BATCH_SIZE);
                List<PassiveInventory> batch = passiveInventoryRepository.findAll(pageable).getContent();
                List<String> identifiers = batch.stream()
                        .flatMap(asset -> Stream.of(asset.getSerial(), asset.getObjectId()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                processBatchSync(identifiers, "PASSIVE");
            }

            long totalIt = itInventoryRepository.count();
            int totalItPages = (int) Math.ceil((double) totalIt / BATCH_SIZE);
            for (int page = 0; page < totalItPages; page++) {
                pageable = PageRequest.of(page, BATCH_SIZE);
                List<ItInventory> batch = itInventoryRepository.findAll(pageable).getContent();
                List<String> identifiers = batch.stream()
                        .flatMap(asset -> Stream.of(asset.getHostSerialNumber(), asset.getObjectId()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                processBatchSync(identifiers, "IT");
            }

            logger.info("Completed rebuilding unmapped inventories");
            return CompletableFuture.completedFuture(null);
        }, "rebuildUnmappedInventoriesAsync");
    }

    /**
     * Process a batch of assets for synchronization
     */
    private void processBatchSync(List<String> identifiers, String type) {
        List<tb_FinancialReport> frAssets;
        if ("ACTIVE".equalsIgnoreCase(type)) {
            frAssets = retryOperation(
                    () -> financialReportRepo.findByAssetSerialNumberIn(identifiers),
                    "findByAssetSerialNumberIn"
            );
        } else {
            frAssets = retryOperation(
                    () -> financialReportRepo.findByAssetNameOrAssetSerialNumberIn(identifiers),
                    "findByAssetNameOrAssetSerialNumberIn"
            );
        }
        Set<String> frIdentifiers = frAssets.stream()
                .flatMap(fr -> Stream.of(fr.getAssetSerialNumber(), fr.getAssetName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Process assets in financial report
        frAssets.forEach(this::processFinancialReportAsset);

        // Process assets not in financial report (add to unmapped)
        identifiers.stream()
                .filter(id -> !frIdentifiers.contains(id))
                .forEach(id -> handleAssetNotInFinancialReport(id, type));
    }

    /**
     * Process a batch for unmapped inventory rebuilding
     */
    private void processBatchForUnmapped(List<String> identifiers, String type) {
        List<tb_FinancialReport> frAssets;
        if ("ACTIVE".equalsIgnoreCase(type)) {
            frAssets = retryOperation(
                    () -> financialReportRepo.findByAssetSerialNumberIn(identifiers),
                    "findByAssetSerialNumberIn"
            );
        } else {
            frAssets = retryOperation(
                    () -> financialReportRepo.findByAssetNameOrAssetSerialNumberIn(identifiers),
                    "findByAssetNameOrAssetSerialNumberIn"
            );
        }
        Set<String> frIdentifiers = frAssets.stream()
                .flatMap(fr -> Stream.of(fr.getAssetSerialNumber(), fr.getAssetName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        identifiers.stream()
                .filter(id -> !frIdentifiers.contains(id))
                .forEach(id -> handleAssetNotInFinancialReport(id, type));
    }

    /**
     * Process Financial Report asset for synchronization and missing asset checks
     */
    private void processFinancialReportAsset(tb_FinancialReport asset) {
        if (asset.getAssetSerialNumber() == null || asset.getNodeType() == null) {
            logger.warn("Skipping financial report asset with null serialNumber or nodeType: ID={}", asset.getId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Date insertDateRaw = asset.getInsertDate();
        Date changeDateRaw = asset.getChangeDate();
        LocalDateTime insertDate = insertDateRaw != null
                ? insertDateRaw.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : (changeDateRaw != null
                ? changeDateRaw.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : now);
        long daysSinceInsert = ChronoUnit.DAYS.between(insertDate, now);

        // Update NEW/EXISTING status if not DECOMMISSIONED or in workflow
        if (!"DECOMMISSIONED".equals(asset.getStatusFlag()) && asset.getFinancialApprovalStatus() == null) {
            String newStatus = daysSinceInsert < 30 ? "NEW" : "EXISTING";
            if (!newStatus.equals(asset.getStatusFlag())) {
                String previousStatus = asset.getStatusFlag();
                asset.setStatusFlag(newStatus);
                asset.setChangeDate(Timestamp.valueOf(now));
                retryOperation(() -> {
                    financialReportRepo.save(asset);
                    return null;
                }, "saveFinancialReport");
                logAudit(asset, previousStatus, newStatus, "Status updated based on days since insert");
            }
        }

        // Remove from unmapped inventory if present
//        removeFromUnmappedInventory(asset.getAssetSerialNumber(), asset.getNodeType());

        removeFromUnmappedInventory(asset.getAssetSerialNumber(), asset.getAssetName());
        // Check for missing assets every 14 days
        LocalDateTime lastChangeDate = changeDateRaw != null
                ? changeDateRaw.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : insertDate;
        if (ChronoUnit.DAYS.between(lastChangeDate, now) >= 14) {
            boolean foundInInventory = checkAssetInInventories(asset.getAssetSerialNumber(), asset.getAssetName());
//            boolean foundInInventory = checkAssetInInventories(asset.getAssetSerialNumber(), asset.getNodeType());
            if (!foundInInventory && !"DECOMMISSIONED".equals(asset.getStatusFlag())) {
                // Mark as potentially missing if not already marked
                if (asset.getRetirementDate() == null) {
                    asset.setRetirementDate(Timestamp.valueOf(now));
                    retryOperation(() -> {
                        financialReportRepo.save(asset);
                        return null;
                    }, "saveFinancialReport");
                    logAudit(asset, asset.getStatusFlag(), "POTENTIALLY_MISSING",
                            "Asset not found in inventories - starting 14-day tracking period");
                    sendMissingAssetNotification(asset, false);
                } else {
                    // Check if missing for more than 14 days
                    LocalDateTime retirementDate = asset.getRetirementDate().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDateTime();
                    long daysMissing = ChronoUnit.DAYS.between(retirementDate, now);
                    if (daysMissing >= MISSING_ASSET_GRACE_PERIOD_DAYS) {
                        String previousStatus = asset.getStatusFlag();
                        asset.setStatusFlag("DECOMMISSIONED");
                        asset.setChangeDate(Timestamp.valueOf(now));
                        retryOperation(() -> {
                            financialReportRepo.save(asset);
                            return null;
                        }, "saveFinancialReport");
                        logAudit(asset, previousStatus, "DECOMMISSIONED",
                                "Asset marked as DECOMMISSIONED after being missing for " + MISSING_ASSET_GRACE_PERIOD_DAYS + " days");
                        sendMissingAssetNotification(asset, true);
                    }
                }
            } else if (foundInInventory) {
                // Asset found in inventory, reset retirement date if set
                if (asset.getRetirementDate() != null) {
                    String previousStatus = asset.getStatusFlag();
                    asset.setRetirementDate(null);
                    asset.setChangeDate(Timestamp.valueOf(now));
                    retryOperation(() -> {
                        financialReportRepo.save(asset);
                        return null;
                    }, "saveFinancialReport");
                    logAudit(asset, "POTENTIALLY_MISSING", previousStatus,
                            "Asset previously marked as missing has been found - tracking period reset");
                }
                // Trigger workflow for DECOMMISSIONED assets with non-zero netCost
                if ("DECOMMISSIONED".equals(asset.getStatusFlag()) && asset.getNetCost() != null && !BigDecimal.ZERO.equals(asset.getNetCost())) {
                    triggerApprovalWorkflow(asset, "pending addition");
                }
            }
            asset.setChangeDate(Timestamp.valueOf(now));
            retryOperation(() -> {
                financialReportRepo.save(asset);
                return null;
            }, "saveFinancialReport");
        }
    }

    /**
     * Remove asset from unmapped inventory
     */
    private void removeFromUnmappedInventory(String serialNumber, String objectId) {
        String identifier = serialNumber != null ? serialNumber : objectId;
        if (identifier == null) {
            logger.warn("Skipping removal from unmapped inventory: both serialNumber and objectId are null");
            return;
        }

        // Check and delete from Active unmapped inventory
        if (serialNumber != null && unmappedActiveInventoryRepository.findBySerialNumber(serialNumber).isPresent()) {
            retryOperation(() -> {
                unmappedActiveInventoryRepository.deleteBySerialNumber(serialNumber);
                return null;
            }, "deleteUnmappedActive");
            logAudit(null, serialNumber, "UNMAPPED", "MAPPED", "ACTIVE",
                    "Asset " + serialNumber + " removed from unmapped ACTIVE inventory");
        }

        // Check and delete from Passive unmapped inventory
        if (unmappedPassiveInventoryRepository.findBySerialOrObjectId(serialNumber, objectId).isPresent()) {
            retryOperation(() -> {
                if (serialNumber != null) {
                    unmappedPassiveInventoryRepository.deleteBySerial(serialNumber);
                }
                if (objectId != null) {
                    unmappedPassiveInventoryRepository.deleteByObjectId(objectId);
                }
                return null;
            }, "deleteUnmappedPassive");
            logAudit(null, identifier, "UNMAPPED", "MAPPED", "PASSIVE",
                    "Asset " + identifier + " removed from unmapped PASSIVE inventory");
        }

        // Check and delete from IT unmapped inventory
        if (unmappedITInventoryRepository.findByHardwareSerialNumberOrElementId(serialNumber, objectId).isPresent()) {
            retryOperation(() -> {
                if (serialNumber != null) {
                    unmappedITInventoryRepository.deleteByHardwareSerialNumber(serialNumber);
                }
                if (objectId != null) {
                    unmappedITInventoryRepository.deleteByElementId(objectId);
                }
                return null;
            }, "deleteUnmappedIT");
            logAudit(null, identifier, "UNMAPPED", "MAPPED", "IT",
                    "Asset " + identifier + " removed from unmapped IT inventory");
        }
    }

    /**
     * Handle asset not found in Financial Report
     */
    private void handleAssetNotInFinancialReport(String identifier, String type) {
        if (identifier == null) {
            logger.warn("Skipping null identifier for {} asset", type);
            return;
        }
        boolean hasDuplicates = checkForInventoryDuplicates(identifier, type);
        if (hasDuplicates) {
            logger.info("Skipping duplicate identifier {} for {} asset", identifier, type);
            return;
        }

        boolean alreadyUnmapped = false;
        switch (type.toUpperCase()) {
            case "ACTIVE":
                alreadyUnmapped = unmappedActiveInventoryRepository.findBySerialNumber(identifier).isPresent();
                if (!alreadyUnmapped) {
                    unmappedInventoryService.mapActiveInventoryBySerialNumber(identifier, "SYSTEM");
                    logAudit(null, identifier, "UNKNOWN", "UNMAPPED", "ACTIVE",
                            "Asset " + identifier + " added to unmapped ACTIVE inventory");
                }
                break;
            case "PASSIVE":
                alreadyUnmapped = unmappedPassiveInventoryRepository.findBySerialOrObjectId(identifier, identifier).isPresent();
                if (!alreadyUnmapped) {
                    Optional<UnmappedPassiveInventory> unmappedOpt = unmappedInventoryService.mapPassiveInventoryByIdentifier(identifier, "SYSTEM");
                    if (unmappedOpt.isPresent()) {
                        logAudit(null, identifier, "UNKNOWN", "UNMAPPED", "PASSIVE",
                                "Asset " + identifier + " added to unmapped PASSIVE inventory");
                    } else {
                        logger.warn("Failed to map passive inventory for identifier: {}", identifier);
                    }
                }
                break;
            case "IT":
                alreadyUnmapped = unmappedITInventoryRepository.findByHardwareSerialNumberOrElementId(identifier, identifier).isPresent();
                if (!alreadyUnmapped) {
                    Optional<ItInventory> itAsset = itInventoryRepository.findByHostSerialNumber(identifier)
                            .or(() -> itInventoryRepository.findByObjectId(identifier));
                    if (itAsset.isPresent()) {
                        unmappedInventoryService.mapITInventoryByIdentifier(identifier, "SYSTEM");
                        logAudit(null, identifier, "UNKNOWN", "UNMAPPED", "IT",
                                "Asset " + identifier + " added to unmapped IT inventory");
                    } else {
                        logger.warn("No ItInventory found for identifier {}", identifier);
                    }
                }
                break;
            default:
                logger.error("Unknown asset type {} for identifier {}", type, identifier);
                return;
        }
    }

    /**
     * Check for duplicate serial numbers in main inventory tables
     */
    private boolean checkForInventoryDuplicates(String identifier, String type) {
        switch (type.toUpperCase()) {
            case "ACTIVE":
                return retryOperation(
                        () -> activeInventoryRepository.findBySerialNumber(identifier).size() > 1,
                        "checkActiveDuplicates"
                );
            case "PASSIVE":
                return retryOperation(
                        () -> passiveInventoryRepository.findByObjectIdOrSerialNumber(identifier, identifier).size() > 1,
                        "checkPassiveDuplicates"
                );
            case "IT":
                return retryOperation(
                        () -> itInventoryRepository.findByObjectIdOrHostSerialNumber(identifier, identifier).size() > 1,
                        "checkITDuplicates"
                );
            default:
                logger.warn("Unknown type {} for duplicate check", type);
                return false;
        }
    }

    /**
     * Check if asset exists in Active, Passive, or IT inventory
     */
    private boolean checkAssetInInventories(String serialNumber, String objectId) {
        if (serialNumber == null && objectId == null) {
            return false;
        }
        // Check Active inventory
        if (serialNumber != null && retryOperation(
                () -> !activeInventoryRepository.findBySerialNumber(serialNumber).isEmpty(),
                "checkActiveInventory"
        )) {
            return true;
        }
        // Check Passive inventory
        if (retryOperation(
                () -> !passiveInventoryRepository.findByObjectIdOrSerialNumber(objectId, serialNumber).isEmpty(),
                "checkPassiveInventory"
        )) {
            return true;
        }
        // Check IT inventory
        if (retryOperation(
                () -> !itInventoryRepository.findByObjectIdOrHostSerialNumber(objectId, serialNumber).isEmpty(),
                "checkITInventory"
        )) {
            return true;
        }
        return false;
    }


    /**
     * Trigger approval workflow with specific original status
     */
    private void triggerApprovalWorkflow(tb_FinancialReport asset, String originalStatus) {
        Optional<ApprovalWorkflow> existingWorkflow = retryOperation(
                () -> approvalWorkflowRepository.findByAssetIdAndUpdatedStatus(String.valueOf(asset.getId()), "Pending Addition"),
                "findApprovalWorkflow"
        );
        if (existingWorkflow.isPresent()) {
            return; // Silently skip existing workflow
        }

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setAssetId(String.valueOf(asset.getId()));
        workflow.setObjectType(asset.getNodeType());
        workflow.setOriginalStatus(originalStatus);
        workflow.setUpdatedStatus("Pending Addition");
        Integer maxProcessId = retryOperation(
                () -> approvalWorkflowRepository.findMaxProcessId(),
                "findMaxProcessId"
        );
        workflow.setProcessId(maxProcessId != null ? maxProcessId + 1 : 1);
        workflow.setComments(getWorkflowComments(originalStatus));
        workflow.setInsertedBy("SYSTEM");
        workflow.setInsertDate(LocalDateTime.now());
        retryOperation(() -> {
            approvalWorkflowRepository.save(workflow);
            return null;
        }, "saveApprovalWorkflow");

        asset.setFinancialApprovalStatus("Pending");
        retryOperation(() -> {
            financialReportRepo.save(asset);
            return null;
        }, "saveFinancialReport");

        logAudit(asset, asset.getStatusFlag(), originalStatus, "Workflow triggered: " + originalStatus);
    }

    /**
     * Get comments for workflow based on original status
     */
    private String getWorkflowComments(String originalStatus) {
        switch (originalStatus) {
            case "pending addition":
                return "Asset found in inventory but marked DECOMMISSIONED or new data uploaded, awaiting addition";
            case "pending modification":
                return "Existing asset data updated, awaiting approval";
            case "pending deletion":
                return "Asset net cost is zero, awaiting deletion approval";
            case "pending movement":
                return "Asset marked for write-off, awaiting movement approval";
            default:
                return "Workflow initiated";
        }
    }

    /**
     * Log audit entry
     */
    private void logAudit(tb_FinancialReport asset, String previousStatus, String newStatus, String notes) {
        logAudit(asset, asset != null ? asset.getAssetSerialNumber() : null, previousStatus, newStatus,
                asset != null ? asset.getNodeType() : null, notes);
    }


    private void logAudit(tb_FinancialReport asset, String serialNumber, String previousStatus, String newStatus,
                          String nodeType, String notes) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAssetId(asset != null ? String.valueOf(asset.getId()) : null);
        auditLog.setSerialNumber(serialNumber);
        auditLog.setPreviousStatus(previousStatus);
        auditLog.setNewStatus(newStatus);
        auditLog.setChangeDate(LocalDateTime.now());
        auditLog.setNodeType(nodeType);
        auditLog.setNotes(notes);
        retryOperation(() -> {
            auditLogRepository.save(auditLog);
            return null;
        }, "saveAuditLog");
    }


    /**
     * Send notification about missing or decommissioned assets
     */
    private void sendMissingAssetNotification(tb_FinancialReport asset, boolean isDecommissioned) {
        String subject;
        String message;

        if (isDecommissioned) {
            subject = "ASSET DECOMMISSIONED: " + asset.getAssetSerialNumber();
            message = "Asset " + asset.getAssetSerialNumber() +
                    " has been automatically marked as DECOMMISSIONED after being missing for " +
                    MISSING_ASSET_GRACE_PERIOD_DAYS + " days.\n\n" +
                    "Asset Type: " + asset.getNodeType() + "\n" +
                    "First Missing Date: " + asset.getRetirementDate() + "\n" +
                    "Decommissioned Date: " + asset.getChangeDate();
        } else {
            subject = "ASSET POTENTIALLY MISSING: " + asset.getAssetSerialNumber();
            message = "Asset " + asset.getAssetSerialNumber() +
                    " has been marked as potentially missing. " +
                    "The asset is in tb_FinancialReport but was not found in any inventory.\n\n" +
                    "Asset Type: " + asset.getNodeType() + "\n" +
                    "Missing Date: " + asset.getRetirementDate() + "\n\n" +
                    "If the asset is not found within " + MISSING_ASSET_GRACE_PERIOD_DAYS +
                    " days, it will be automatically marked as DECOMMISSIONED.";
        }

//        notificationService.sendNotification(subject, message);
    }

    /**
     * Synchronizes active assets between inventory and financial report
     */
    @Transactional(timeout = 120)
    public void syncActiveAssets() {
        logger.info("Syncing active assets");
        List<ActiveInventory> activeAssets = retryOperation(
                () -> activeInventoryRepository.findAll(),
                "findAllActiveInventory"
        );
        for (ActiveInventory asset : activeAssets) {
            if (asset.getSerialNumber() != null) {
                syncActiveAsset(asset.getSerialNumber());
            } else {
                logger.warn("Skipping active asset with null serialNumber");
            }
        }
    }

    /**
     * Synchronizes passive assets between inventory and financial report
     */
    @Transactional(timeout = 120)
    public void syncPassiveAssets() {
        logger.info("Syncing passive assets");
        List<PassiveInventory> passiveAssets = retryOperation(
                () -> passiveInventoryRepository.findAll(),
                "findAllPassiveInventory"
        );
        for (PassiveInventory asset : passiveAssets) {
            String objectId = asset.getObjectId();
            String objectIdStr = (objectId != null) ? String.valueOf(objectId) : null;
            syncPassiveOrItAsset(objectIdStr, asset.getSerial(), "PASSIVE");
        }
    }

    /**
     * Synchronizes IT assets between inventory and financial report
     */
    @Transactional(timeout = 120)
    public void syncItAssets() {
        logger.info("Syncing IT assets");
        List<ItInventory> itAssets = retryOperation(
                () -> itInventoryRepository.findAll(),
                "findAllITInventory"
        );
        for (ItInventory asset : itAssets) {
            syncPassiveOrItAsset(asset.getObjectId(), asset.getHostSerialNumber(), "IT");
        }
    }

    /**
     * Syncs active assets identified by serial number
     */
    @Transactional(timeout = 120)
    public void syncActiveAsset(String serialNumber) {
        if (serialNumber == null) {
            logger.warn("Skipping active asset with null serialNumber");
            return;
        }
        Optional<tb_FinancialReport> financialReportOpt = retryOperation(
                () -> financialReportRepo.findByAssetSerialNumber(serialNumber),
                "findByAssetSerialNumber"
        );
        processFinancialReportMatch(financialReportOpt, null, serialNumber, "ACTIVE");
    }

    /**
     * Syncs passive or IT assets identified by objectId and serialNumber
     */
    @Transactional(timeout = 120)
    public void syncPassiveOrItAsset(String objectId, String serialNumber, String nodeType) {
        if (objectId == null && serialNumber == null) {
            logger.warn("Skipping {} asset with both null objectId and serialNumber", nodeType);
            return;
        }

        Optional<tb_FinancialReport> financialReportOpt = retryOperation(
                () -> financialReportRepo.findByAssetNameOrAssetSerialNumber(objectId, serialNumber),
                "findByAssetNameOrAssetSerialNumber"
        );

        processFinancialReportMatch(financialReportOpt, objectId, serialNumber, nodeType);
    }

    /**
     * Processes a match (or non-match) from the financial report system
     */
    private void processFinancialReportMatch(Optional<tb_FinancialReport> financialReportOpt,
                                             String objectId, String serialNumber, String nodeType) {
        if (financialReportOpt.isPresent()) {
            logger.info("Found financial report match for {} asset: objectId={}, serialNumber={}",
                    nodeType, objectId, serialNumber);
            processFinancialReportAsset(financialReportOpt.get());
        } else {
            logger.warn("No financial report match for {} asset: objectId={}, serialNumber={}",
                    nodeType, objectId, serialNumber);
            String identifier = serialNumber != null ? serialNumber : objectId;
            if (identifier != null) {
                handleAssetNotInFinancialReport(identifier, nodeType);
            } else {
                logger.warn("Skipping unmapped addition for {} asset with both null identifiers", nodeType);
            }
        }
    }

}