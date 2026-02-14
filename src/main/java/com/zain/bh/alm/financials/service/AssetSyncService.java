package com.zain.bh.alm.financials.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zain.bh.alm.financials.entity.AuditLog;
import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.entity.ITInventory;
import com.zain.bh.alm.financials.entity.Node;
import com.zain.bh.alm.financials.entity.PassiveInventory;
import com.zain.bh.alm.financials.entity.UnmappedActiveInventory;
import com.zain.bh.alm.financials.entity.UnmappedITInventory;
import com.zain.bh.alm.financials.entity.UnmappedPassiveInventory;
import com.zain.bh.alm.financials.entity.WFFinancialApprovalRequest;
import com.zain.bh.alm.financials.repository.AuditLogRepository;
import com.zain.bh.alm.financials.repository.FinancialReportRepository;
import com.zain.bh.alm.financials.repository.ITInventoryRepository;
import com.zain.bh.alm.financials.repository.NodeRepository;
import com.zain.bh.alm.financials.repository.PassiveInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedActiveInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedITInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedPassiveInventoryRepository;
import com.zain.bh.alm.financials.repository.WFFinancialApprovalRequestRepository;

@Service
public class AssetSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetSyncService.class);
    private static final int BATCH_SIZE = 500;
    private static final int MISSING_ASSET_GRACE_PERIOD_DAYS = 14;

    private final FinancialReportRepository financialReportRepository;
    private final NodeRepository activeInventoryRepository;
    private final PassiveInventoryRepository passiveInventoryRepository;
    private final ITInventoryRepository itInventoryRepository;
    private final UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;
    private final UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;
    private final UnmappedITInventoryRepository unmappedITInventoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final WFFinancialApprovalRequestRepository approvalWorkflowRepository;
    private final UnmappedInventoryService unmappedInventoryService;
    private final NotificationService notificationService;

    public AssetSyncService(FinancialReportRepository financialReportRepository,
                           NodeRepository activeInventoryRepository,
                           PassiveInventoryRepository passiveInventoryRepository,
                           ITInventoryRepository itInventoryRepository,
                           UnmappedActiveInventoryRepository unmappedActiveInventoryRepository,
                           UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository,
                           UnmappedITInventoryRepository unmappedITInventoryRepository,
                           AuditLogRepository auditLogRepository,
                           WFFinancialApprovalRequestRepository approvalWorkflowRepository,
                           UnmappedInventoryService unmappedInventoryService,
                           NotificationService notificationService) {
        this.financialReportRepository = financialReportRepository;
        this.activeInventoryRepository = activeInventoryRepository;
        this.passiveInventoryRepository = passiveInventoryRepository;
        this.itInventoryRepository = itInventoryRepository;
        this.unmappedActiveInventoryRepository = unmappedActiveInventoryRepository;
        this.unmappedPassiveInventoryRepository = unmappedPassiveInventoryRepository;
        this.unmappedITInventoryRepository = unmappedITInventoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.approvalWorkflowRepository = approvalWorkflowRepository;
        this.unmappedInventoryService = unmappedInventoryService;
        this.notificationService = notificationService;
    }

    /**
     * Scheduled job to sync all assets daily at 1:00 AM
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void scheduledAssetSync() {
        LOGGER.info("Starting scheduled asset synchronization");
        try {
            CompletableFuture<Void> missingSync = syncMissingAssetsAsync();
            CompletableFuture<Void> activeSync = syncActiveAssetsAsync();
            CompletableFuture<Void> passiveSync = syncPassiveAssetsAsync();
            CompletableFuture<Void> itSync = syncItAssetsAsync();
            CompletableFuture<Void> unmappedSync = rebuildUnmappedInventoriesAsync();
            CompletableFuture.allOf(missingSync, activeSync, passiveSync, itSync, unmappedSync).join();
            LOGGER.info("Completed scheduled asset synchronization");
        } catch (Exception e) {
            LOGGER.error("Error during scheduled asset synchronization: ", e);
            notificationService.sendNotification(
                    "Asset Sync Error",
                    "Daily asset sync failed: " + e.getMessage()
            );
        }
    }

    /**
     * Async method to sync Active assets with batching
     */
    @Async
    @Transactional
    public CompletableFuture<Void> syncActiveAssetsAsync() {
        LOGGER.info("Starting async sync for Active assets");
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        long totalRecords = activeInventoryRepository.count();
        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

        for (int page = 0; page < totalPages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<Node> batch = activeInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream()
                    .map(Node::getSerialNumber)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            processBatchSync(serials, "ACTIVE");
            LOGGER.debug("Processed Active batch {}/{}", page + 1, totalPages);
        }
        LOGGER.info("Completed async sync for Active assets");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Async method to sync Passive assets with batching
     */
    @Async
    @Transactional
    public CompletableFuture<Void> syncPassiveAssetsAsync() {
        LOGGER.info("Starting async sync for Passive assets");
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        long totalRecords = passiveInventoryRepository.count();
        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

        for (int page = 0; page < totalPages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<PassiveInventory> batch = passiveInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream()
                    .map(PassiveInventory::getSerial)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            processBatchSync(serials, "PASSIVE");
            LOGGER.debug("Processed Passive batch {}/{}", page + 1, totalPages);
        }
        LOGGER.info("Completed async sync for Passive assets");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Async method to sync IT assets with batching
     */
    @Async
    @Transactional
    public CompletableFuture<Void> syncItAssetsAsync() {
        LOGGER.info("Starting async sync for IT assets");
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        long totalRecords = itInventoryRepository.count();
        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

        for (int page = 0; page < totalPages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<ITInventory> batch = itInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream()
                    .map(ITInventory::getHostSerialNumber)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            processBatchSync(serials, "IT");
            LOGGER.debug("Processed IT batch {}/{}", page + 1, totalPages);
        }
        LOGGER.info("Completed async sync for IT assets");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Async method to sync missing assets and check decommissioning
     */
    @Async
    @Transactional
    public CompletableFuture<Void> syncMissingAssetsAsync() {
        LOGGER.info("Starting async sync for missing assets");
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        long totalRecords = financialReportRepository.count();
        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

        for (int page = 0; page < totalPages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<FinancialReport> batch = financialReportRepository.findAll(pageable).getContent();
            batch.forEach(this::processFinancialReportAsset);
        }
        LOGGER.info("Completed async sync for missing assets");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Rebuild unmapped inventories from scratch
     */
    @Async
    @Transactional
    public CompletableFuture<Void> rebuildUnmappedInventoriesAsync() {
        LOGGER.info("Rebuilding unmapped inventories");

        unmappedActiveInventoryRepository.deleteAll();
        unmappedPassiveInventoryRepository.deleteAll();
        unmappedITInventoryRepository.deleteAll();

        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        long totalActive = activeInventoryRepository.count();
        int totalActivePages = (int) Math.ceil((double) totalActive / BATCH_SIZE);
        for (int page = 0; page < totalActivePages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<Node> batch = activeInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream()
                    .map(Node::getSerialNumber)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            processBatchForUnmapped(serials, "ACTIVE");
        }

        long totalPassive = passiveInventoryRepository.count();
        int totalPassivePages = (int) Math.ceil((double) totalPassive / BATCH_SIZE);
        for (int page = 0; page < totalPassivePages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<PassiveInventory> batch = passiveInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream()
                    .map(PassiveInventory::getSerial)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            processBatchForUnmapped(serials, "PASSIVE");
        }

        long totalIt = itInventoryRepository.count();
        int totalItPages = (int) Math.ceil((double) totalIt / BATCH_SIZE);
        for (int page = 0; page < totalItPages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<ITInventory> batch = itInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream()
                    .map(ITInventory::getHostSerialNumber)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            processBatchForUnmapped(serials, "IT");
        }

        LOGGER.info("Completed rebuilding unmapped inventories");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Process a batch of assets for synchronization
     */
    private void processBatchSync(List<String> identifiers, String type) {
        List<FinancialReport> frAssets = financialReportRepository.findByAssetSerialNumberIn(identifiers);
        Set<String> frSerials = frAssets.stream()
                .map(FinancialReport::getAssetSerialNumber)
                .collect(Collectors.toSet());

        // Process assets in FR
        frAssets.forEach(this::processFinancialReportAsset);

        // Process assets not in FR (map to unmapped)
        identifiers.stream()
                .filter(id -> !frSerials.contains(id))
                .forEach(id -> handleAssetNotInFinancialReport(id, type));
    }

    /**
     * Process a batch for unmapped inventory rebuilding
     */
    private void processBatchForUnmapped(List<String> identifiers, String type) {
        List<FinancialReport> frAssets = financialReportRepository.findByAssetSerialNumberIn(identifiers);
        Set<String> frSerials = frAssets.stream()
                .map(FinancialReport::getAssetSerialNumber)
                .collect(Collectors.toSet());

        identifiers.stream()
                .filter(id -> !frSerials.contains(id))
                .forEach(id -> handleAssetNotInFinancialReport(id, type));
    }

    /**
     * Process Financial Report asset for synchronization and missing asset checks
     */
    private void processFinancialReportAsset(FinancialReport asset) {
        if (asset.getAssetSerialNumber() == null || asset.getNodeType() == null) {
            LOGGER.warn("Skipping financial report asset with null serialNumber or nodeType: ID={}", asset.getId());
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
                financialReportRepository.save(asset);
                logAudit(asset, previousStatus, newStatus, "Status updated based on days since insert");
            }
        }

        // Check for missing assets every 14 days
        LocalDateTime lastChangeDate = changeDateRaw != null
                ? changeDateRaw.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : insertDate;
        if (ChronoUnit.DAYS.between(lastChangeDate, now) >= 14) {
            boolean foundInInventory = checkAssetInInventories(asset.getAssetSerialNumber(), asset.getNodeType());
            if (!foundInInventory && !"DECOMMISSIONED".equals(asset.getStatusFlag())) {
                // Mark as potentially missing if not already marked
                if (asset.getRetirementDate() == null) {
                    asset.setRetirementDate(Timestamp.valueOf(now));
                    financialReportRepository.save(asset);
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
                        financialReportRepository.save(asset);
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
                    financialReportRepository.save(asset);
                    logAudit(asset, "POTENTIALLY_MISSING", previousStatus,
                            "Asset previously marked as missing has been found - tracking period reset");
                }
                // Remove from unmapped inventory
                removeFromUnmappedInventory(asset.getAssetSerialNumber(), asset.getNodeType());
                // Trigger workflow for DECOMMISSIONED assets with non-zero netCost
                if ("DECOMMISSIONED".equals(asset.getStatusFlag()) && asset.getNetCost() != null && !BigDecimal.ZERO.equals(asset.getNetCost())) {
                    triggerApprovalWorkflow(asset, "pending addition");
                }
            }
            asset.setChangeDate(Timestamp.valueOf(now));
            financialReportRepository.save(asset);
        }
    }

    /**
     * Remove asset from unmapped inventory
     */
    private void removeFromUnmappedInventory(String serialNumber, String nodeType) {
        switch (nodeType.toUpperCase()) {
            case "ACTIVE":
                List<UnmappedActiveInventory> activeRecords = unmappedActiveInventoryRepository.findAllBySerialNumber(serialNumber);
                if (activeRecords.size() > 1) {
                    LOGGER.warn("Multiple unmapped ACTIVE records found for asset {}, deleting all", serialNumber);
                }
                activeRecords.forEach(record -> {
                    unmappedActiveInventoryRepository.delete(record);
                    logAudit(null, serialNumber, "UNMAPPED", "MAPPED",
                            nodeType, "Asset " + serialNumber + " removed from unmapped ACTIVE inventory");
                });
                break;
            case "PASSIVE":
                // Check for multiple records using findBySerial and findByObjectId
                List<UnmappedPassiveInventory> passiveRecords = new ArrayList<>();
                Optional<UnmappedPassiveInventory> bySerial = unmappedPassiveInventoryRepository.findBySerial(serialNumber);
                Optional<UnmappedPassiveInventory> byObjectId = unmappedPassiveInventoryRepository.findByObjectId(serialNumber);
                bySerial.ifPresent(passiveRecords::add);
                byObjectId.ifPresent(record -> {
                    if (!passiveRecords.contains(record)) {
                        passiveRecords.add(record);
                    }
                });
                if (passiveRecords.size() > 1) {
                    LOGGER.warn("Multiple unmapped PASSIVE records found for asset {}, deleting all", serialNumber);
                }
                if (!passiveRecords.isEmpty()) {
                    // Delete by serial and objectId to cover all matching records
                    unmappedPassiveInventoryRepository.deleteBySerial(serialNumber);
                    unmappedPassiveInventoryRepository.deleteByObjectId(serialNumber);
                    logAudit(null, serialNumber, "UNMAPPED", "MAPPED", nodeType,
                            "Asset " + serialNumber + " removed from unmapped PASSIVE inventory");
                }
                break;
            case "IT":
                Optional<UnmappedITInventory> itRecord = unmappedITInventoryRepository.findByHardwareSerialNumber(serialNumber);
                itRecord.ifPresent(record -> {
                    unmappedITInventoryRepository.delete(record);
                    logAudit(null, serialNumber, "UNMAPPED", "MAPPED",
                            nodeType, "Asset " + serialNumber + " removed from unmapped IT inventory");
                });
                break;
            default:
                LOGGER.warn("Unknown nodeType {} for asset {}, skipping unmapped deletion", nodeType, serialNumber);
        }
    }

    /**
     * Handle asset not found in Financial Report
     */
    private void handleAssetNotInFinancialReport(String identifier, String type) {
        boolean alreadyUnmapped = false;
        switch (type.toUpperCase()) {
            case "ACTIVE":
                alreadyUnmapped = !unmappedActiveInventoryRepository.findAllBySerialNumber(identifier).isEmpty();
                if (!alreadyUnmapped) {
                    unmappedInventoryService.mapActiveInventoryBySerialNumber(identifier, "SYSTEM");
                    logAudit(null, identifier, "UNKNOWN", "UNMAPPED", type,
                            "Asset " + identifier + " added to unmapped ACTIVE inventory");
                }
                break;
            case "PASSIVE":
                alreadyUnmapped = unmappedPassiveInventoryRepository.findBySerialOrObjectId(identifier, identifier).isPresent();
                if (!alreadyUnmapped) {
                    unmappedInventoryService.mapPassiveInventoryByIdentifier(identifier, "SYSTEM");
                    logAudit(null, identifier, "UNKNOWN", "UNMAPPED", type,
                            "Asset " + identifier + " added to unmapped PASSIVE inventory");
                }
                break;
            case "IT":
                alreadyUnmapped = unmappedITInventoryRepository.findByHardwareSerialNumber(identifier).isPresent();
                if (!alreadyUnmapped) {
                    unmappedInventoryService.mapITInventoryByIdentifier(identifier, "SYSTEM");
                    logAudit(null, identifier, "UNKNOWN", "UNMAPPED", type,
                            "Asset " + identifier + " added to unmapped IT inventory");
                }
                break;
            default:
                LOGGER.error("Unknown asset type {} for identifier {}", type, identifier);
                return;
        }
        if (alreadyUnmapped) {
            LOGGER.info("Asset {} already exists in unmapped {} inventory", identifier, type);
        }
    }

    /**
     * Check if asset exists in Active, Passive, or IT inventory
     */
    private boolean checkAssetInInventories(String identifier, String nodeType) {
        if (identifier == null || nodeType == null) {
            return false;
        }
        switch (nodeType.toUpperCase()) {
            case "ACTIVE":
                return !activeInventoryRepository.findBySerialNumber(identifier).isEmpty();
            case "PASSIVE":
                return !passiveInventoryRepository.findByObjectIdOrSerialNumber(identifier, identifier).isEmpty();
            case "IT":
                return !itInventoryRepository.findByObjectIdOrHostSerialNumber(identifier, identifier).isEmpty();
            default:
                LOGGER.warn("Unknown nodeType {} for identifier {}", nodeType, identifier);
                return false;
        }
    }

    /**
     * Trigger approval workflow with specific original status
     */
    private void triggerApprovalWorkflow(FinancialReport asset, String originalStatus) {
        Optional<WFFinancialApprovalRequest> existingWorkflow = approvalWorkflowRepository.findByAssetIdAndUpdatedStatus(
                asset.getId().toString(), "Pending Addition");
        if (existingWorkflow.isPresent()) {
            LOGGER.info("Workflow already exists for asset {} with status Pending Addition", asset.getAssetSerialNumber());
            return;
        }

        WFFinancialApprovalRequest workflow = new WFFinancialApprovalRequest();
        workflow.setAssetId(asset.getAssetName().toString());
        workflow.setObjectType(asset.getNodeType());
        workflow.setOriginalStatus(originalStatus);
        workflow.setUpdatedStatus("Pending Addition");
        Integer maxProcessId = approvalWorkflowRepository.findMaxProcessId();
        workflow.setProcessId(maxProcessId != null ? maxProcessId + 1 : 1);
        workflow.setComments(getWorkflowComments(originalStatus));
        workflow.setInsertedBy("SYSTEM");
        workflow.setInsertDate(LocalDateTime.now());
        approvalWorkflowRepository.save(workflow);

        asset.setFinancialApprovalStatus("Pending");
        financialReportRepository.save(asset);

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
    private void logAudit(FinancialReport asset, String previousStatus, String newStatus, String notes) {
        logAudit(asset, asset != null ? asset.getAssetSerialNumber() : null, previousStatus, newStatus, asset != null ? asset.getNodeType() : null, notes);
    }

    private void logAudit(FinancialReport asset, String serialNumber, String previousStatus, String newStatus, String nodeType, String notes) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAssetId(asset != null ? String.valueOf(asset.getId()) : null);
        auditLog.setSerialNumber(serialNumber);
        auditLog.setPreviousStatus(previousStatus);
        auditLog.setNewStatus(newStatus);
        auditLog.setChangeDate(LocalDateTime.now());
        auditLog.setNodeType(nodeType);
        auditLog.setNotes(notes);
        auditLogRepository.save(auditLog);
    }

    /**
     * Send notification about missing or decommissioned assets
     */
    private void sendMissingAssetNotification(FinancialReport asset, boolean isDecommissioned) {
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

        notificationService.sendNotification(subject, message);
    }

    /**
     * Synchronizes active assets between inventory and financial report
     */
    @Transactional
    public void syncActiveAssets() {
        LOGGER.info("Syncing active assets");
        List<Node> activeAssets = activeInventoryRepository.findAll();
        for (Node asset : activeAssets) {
            if (asset.getSerialNumber() != null) {
                syncActiveAsset(asset.getSerialNumber());
            } else {
                LOGGER.warn("Skipping active asset with null serialNumber");
            }
        }
    }

    /**
     * Synchronizes passive assets between inventory and financial report
     */
    @Transactional
    public void syncPassiveAssets() {
        LOGGER.info("Syncing passive assets");
        List<PassiveInventory> passiveAssets = passiveInventoryRepository.findAll();
        for (PassiveInventory asset : passiveAssets) {
            String objectId = asset.getObjectId();
            String objectIdStr = (objectId != null) ? String.valueOf(objectId) : null;
            syncPassiveOrItAsset(objectIdStr, asset.getSerial(), "PASSIVE");
        }
    }

    /**
     * Synchronizes IT assets between inventory and financial report
     */
    @Transactional
    public void syncItAssets() {
        LOGGER.info("Syncing IT assets");
        List<ITInventory> itAssets = itInventoryRepository.findAll();
        for (ITInventory asset : itAssets) {
            syncPassiveOrItAsset(asset.getObjectId(), asset.getHostSerialNumber(), "IT");
        }
    }

    /**
     * Syncs active assets identified by serial number
     */
    @Transactional
    public void syncActiveAsset(String serialNumber) {
        if (serialNumber == null) {
            LOGGER.warn("Skipping active asset with null serialNumber");
            return;
        }
        Optional<FinancialReport> financialReportOpt = financialReportRepository.findByAssetSerialNumber(serialNumber);
        processFinancialReportMatch(financialReportOpt, null, serialNumber, "ACTIVE");
    }

    /**
     * Syncs passive or IT assets identified by objectId and serialNumber
     */
    @Transactional
    public void syncPassiveOrItAsset(String objectId, String serialNumber, String nodeType) {
        if (objectId == null && serialNumber == null) {
            LOGGER.warn("Skipping {} asset with both null objectId and serialNumber", nodeType);
            return;
        }
        Optional<FinancialReport> financialReportOpt =
                financialReportRepository.findByAssetNameOrAssetSerialNumberExact(objectId, serialNumber);
        processFinancialReportMatch(financialReportOpt, objectId, serialNumber, nodeType);
    }

    /**
     * Processes a match (or non-match) from the financial report system
     */
    private void processFinancialReportMatch(Optional<FinancialReport> financialReportOpt,
                                             String objectId, String serialNumber, String nodeType) {
        if (financialReportOpt.isPresent()) {
            processFinancialReportAsset(financialReportOpt.get());
        } else {
            LOGGER.info("Asset not found in Financial Report: {}", serialNumber != null ? serialNumber : objectId);
            handleAssetNotInFinancialReport(serialNumber, nodeType);
        }
    }
}
