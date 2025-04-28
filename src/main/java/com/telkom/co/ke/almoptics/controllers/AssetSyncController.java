package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.entities.*;
import com.telkom.co.ke.almoptics.models.ApprovalWorkflow;
import com.telkom.co.ke.almoptics.models.AuditLog;
import com.telkom.co.ke.almoptics.repository.*;
import com.telkom.co.ke.almoptics.services.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.math.BigDecimal;

/**
 * Controller for Asset Synchronization operations
 * Provides API endpoints for sync, status checks, and approval workflows
 */
@RestController
@RequestMapping("/api/asset-sync")
public class AssetSyncController {

    private static final Logger logger = LoggerFactory.getLogger(AssetSyncController.class);
    private static final int BATCH_SIZE = 500;

    @Autowired
    private AssetSyncService assetSyncService;

    @Autowired
    private UnmappedInventoryService unmappedInventoryService;

    @Autowired
    private MissingAssetCheckService missingAssetCheckService;

    @Autowired
    private FinancialReportRepo financialReportRepo;

    @Autowired
    private UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;

    @Autowired
    private UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;

    @Autowired
    private UnmappedITInventoryRepository unmappedITInventoryRepository;

    @Autowired
    private ApprovalWorkflowRepository approvalWorkflowRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ActiveInventoryRepository activeInventoryRepository;

    @Autowired
    private PassiveInventoryRepository passiveInventoryRepository;

    @Autowired
    private ItInventoryRepository itInventoryRepository;

    @Autowired
    private ApprovalWorkflowService approvalWorkflowService;

    /**
     * Trigger a full synchronization of all assets asynchronously
     */
    @PostMapping("/trigger-full-sync")
    public ResponseEntity<Map<String, Object>> triggerFullSync() {
        logger.info("Manual trigger: Full asset synchronization started");
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            CompletableFuture<Void> missingSync = syncMissingAssetsAsync();
            CompletableFuture<Void> activeSync = syncActiveAssetsAsync();
            CompletableFuture<Void> passiveSync = syncPassiveAssetsAsync();
            CompletableFuture<Void> itSync = syncItAssetsAsync();
            CompletableFuture<Void> unmappedSync = rebuildUnmappedInventoriesAsync();

            CompletableFuture.allOf(missingSync, activeSync, passiveSync, itSync, unmappedSync).join();

            response.put("status", "success");
            response.put("message", "Full asset synchronization completed successfully");
            logger.info("Full asset synchronization completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during full sync: ", e);
            response.put("status", "error");
            response.put("message", "Error during synchronization: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Sync only Active inventory
     */
    @PostMapping("/sync-active")
    public ResponseEntity<Map<String, Object>> syncActive() {
        logger.info("Manual trigger: Active inventory synchronization started");
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            CompletableFuture<Void> activeSync = syncActiveAssetsAsync();
            activeSync.join();

            response.put("status", "success");
            response.put("message", "Active inventory synchronization completed");
            logger.info("Active inventory synchronization completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during active sync: ", e);
            response.put("status", "error");
            response.put("message", "Error during active synchronization: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Sync only Passive inventory
     */
    @PostMapping("/sync-passive")
    public ResponseEntity<Map<String, Object>> syncPassive() {
        logger.info("Manual trigger: Passive inventory synchronization started");
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            CompletableFuture<Void> passiveSync = syncPassiveAssetsAsync();
            passiveSync.join();

            response.put("status", "success");
            response.put("message", "Passive inventory synchronization completed");
            logger.info("Passive inventory synchronization completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during passive sync: ", e);
            response.put("status", "error");
            response.put("message", "Error during passive synchronization: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Sync only IT inventory
     */
    @PostMapping("/sync-it")
    public ResponseEntity<Map<String, Object>> syncIt() {
        logger.info("Manual trigger: IT inventory synchronization started");
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            CompletableFuture<Void> itSync = syncItAssetsAsync();
            itSync.join();

            response.put("status", "success");
            response.put("message", "IT inventory synchronization completed");
            logger.info("IT inventory synchronization completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during IT sync: ", e);
            response.put("status", "error");
            response.put("message", "Error during IT synchronization: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Async method to sync Active assets with batching
     */
    @Async
    @Transactional
    public CompletableFuture<Void> syncActiveAssetsAsync() {
        logger.info("Starting async sync for Active assets");
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        long totalRecords = activeInventoryRepository.count();
        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

        for (int page = 0; page < totalPages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<ActiveInventory> batch = activeInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream().map(ActiveInventory::getSerialNumber)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            processBatchSync(serials, "ACTIVE");
            logger.debug("Processed Active batch {}/{}", page + 1, totalPages);
        }
        logger.info("Completed async sync for Active assets");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Async method to sync Passive assets with batching
     */
    @Async
    @Transactional
    public CompletableFuture<Void> syncPassiveAssetsAsync() {
        logger.info("Starting async sync for Passive assets");
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        long totalRecords = passiveInventoryRepository.count();
        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

        for (int page = 0; page < totalPages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<PassiveInventory> batch = passiveInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream().map(PassiveInventory::getSerial)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            processBatchSync(serials, "PASSIVE");
            logger.debug("Processed Passive batch {}/{}", page + 1, totalPages);
        }
        logger.info("Completed async sync for Passive assets");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Async method to sync IT assets with batching
     */
    @Async
    @Transactional
    public CompletableFuture<Void> syncItAssetsAsync() {
        logger.info("Starting async sync for IT assets");
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        long totalRecords = itInventoryRepository.count();
        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

        for (int page = 0; page < totalPages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<ItInventory> batch = itInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream().map(ItInventory::getHostSerialNumber)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            processBatchSync(serials, "IT");
            logger.debug("Processed IT batch {}/{}", page + 1, totalPages);
        }
        logger.info("Completed async sync for IT assets");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Rebuild unmapped inventories from scratch
     */
    @Async
    @Transactional
    public CompletableFuture<Void> rebuildUnmappedInventoriesAsync() {
        logger.info("Rebuilding unmapped inventories");

        unmappedActiveInventoryRepository.deleteAll();
        unmappedPassiveInventoryRepository.deleteAll();
        unmappedITInventoryRepository.deleteAll();

        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        long totalActive = activeInventoryRepository.count();
        int totalActivePages = (int) Math.ceil((double) totalActive / BATCH_SIZE);
        for (int page = 0; page < totalActivePages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<ActiveInventory> batch = activeInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream().map(ActiveInventory::getSerialNumber)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            processBatchForUnmapped(serials, "ACTIVE");
        }

        long totalPassive = passiveInventoryRepository.count();
        int totalPassivePages = (int) Math.ceil((double) totalPassive / BATCH_SIZE);
        for (int page = 0; page < totalPassivePages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<PassiveInventory> batch = passiveInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream().map(PassiveInventory::getSerial)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            processBatchForUnmapped(serials, "PASSIVE");
        }

        long totalIt = itInventoryRepository.count();
        int totalItPages = (int) Math.ceil((double) totalIt / BATCH_SIZE);
        for (int page = 0; page < totalItPages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<ItInventory> batch = itInventoryRepository.findAll(pageable).getContent();
            List<String> serials = batch.stream().map(ItInventory::getHostSerialNumber)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            processBatchForUnmapped(serials, "IT");
        }

        logger.info("Completed rebuilding unmapped inventories");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Async method to sync missing assets and check decommissioning
     */
    @Async
    @Transactional
    public CompletableFuture<Void> syncMissingAssetsAsync() {
        logger.info("Starting async sync for missing assets");
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        long totalRecords = financialReportRepo.count();
        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

        for (int page = 0; page < totalPages; page++) {
            pageable = PageRequest.of(page, BATCH_SIZE);
            List<tb_FinancialReport> batch = financialReportRepo.findAll(pageable).getContent();
            batch.forEach(this::processFRAsset);
        }
        logger.info("Completed async sync for missing assets");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Process a batch of assets for synchronization
     */
    private void processBatchSync(List<String> identifiers, String type) {
        List<tb_FinancialReport> frAssets = financialReportRepo.findByAssetSerialNumberIn(identifiers);
        Set<String> frSerials = frAssets.stream()
                .map(tb_FinancialReport::getAssetSerialNumber)
                .collect(Collectors.toSet());

        // Process assets in FR
        frAssets.forEach(this::processFRAsset);

        // Process assets not in FR (map to unmapped)
        identifiers.stream()
                .filter(id -> !frSerials.contains(id))
                .forEach(id -> handleAssetNotInFR(id, type));
    }

    /**
     * Process a batch for unmapped inventory rebuilding
     */
    private void processBatchForUnmapped(List<String> identifiers, String type) {
        List<tb_FinancialReport> frAssets = financialReportRepo.findByAssetSerialNumberIn(identifiers);
        Set<String> frSerials = frAssets.stream()
                .map(tb_FinancialReport::getAssetSerialNumber)
                .collect(Collectors.toSet());

        identifiers.stream()
                .filter(id -> !frSerials.contains(id))
                .forEach(id -> handleAssetNotInFR(id, type));
    }

    /**
     * Process Financial Report asset
     */
    private void processFRAsset(tb_FinancialReport asset) {
        LocalDateTime now = LocalDateTime.now();
        Date insertDateRaw = asset.getInsertDate();
        Date changeDateRaw = asset.getChangeDate();
        LocalDateTime insertDate = insertDateRaw != null
                ? insertDateRaw.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : changeDateRaw.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        long daysSinceInsert = ChronoUnit.DAYS.between(insertDate, now);

        // Update NEW/EXISTING status if not DECOMMISSIONED or in workflow
        if (!"DECOMMISSIONED".equals(asset.getStatusFlag()) && asset.getFinancialApprovalStatus() == null) {
            String newStatus = daysSinceInsert < 30 ? "NEW" : "EXISTING";
            if (!newStatus.equals(asset.getStatusFlag())) {
                String previousStatus = asset.getStatusFlag();
                asset.setStatusFlag(newStatus);
                asset.setChangeDate(new Timestamp(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
                financialReportRepo.save(asset);
                logAudit(asset, previousStatus, newStatus, "Status updated based on days since insert");
            }
        }

        // Check every 14 days for presence in inventories; auto-DECOMMISSIONED if missing
        LocalDateTime lastChangeDate = changeDateRaw != null
                ? changeDateRaw.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : insertDate;
        if (ChronoUnit.DAYS.between(lastChangeDate, now) >= 14) {
            boolean foundInInventory = checkAssetInInventories(asset.getAssetSerialNumber(), asset.getNodeType());
            if (!foundInInventory && !"DECOMMISSIONED".equals(asset.getStatusFlag())) {
                String previousStatus = asset.getStatusFlag();
                asset.setStatusFlag("DECOMMISSIONED");
                asset.setChangeDate(new Timestamp(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
                asset.setRetirementDate(new Timestamp(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
                financialReportRepo.save(asset);
                logAudit(asset, previousStatus, "DECOMMISSIONED", "Asset not found in inventories after 14-day check");
            } else if (foundInInventory) {
                // Delete from unmapped inventory based on nodeType
                String serialNumber = asset.getAssetSerialNumber();
                String nodeType = asset.getNodeType().toUpperCase();
                switch (nodeType) {
                    case "ACTIVE":
                        Optional<UnmappedActiveInventory> activeRecord = unmappedActiveInventoryRepository.findBySerialNumber(serialNumber);
                        // Check for multiple records
                        long activeCount = unmappedActiveInventoryRepository.findAllBySerialNumber(serialNumber).size();
                        if (activeCount > 1) {
                            logger.warn("Multiple unmapped ACTIVE records found for asset {}, deleting first only", serialNumber);
                        }
                        activeRecord.ifPresent(record -> {
                            unmappedActiveInventoryRepository.delete(record);
                            logAudit(asset, "UNMAPPED", "MAPPED", "Asset " + serialNumber + " removed from unmapped ACTIVE inventory");
                        });
                        break;
                    case "PASSIVE":
                        Optional<UnmappedPassiveInventory> passiveRecord = unmappedPassiveInventoryRepository.findBySerialOrObjectId(serialNumber, serialNumber);
                        // Check for multiple records
                        boolean hasSerial = unmappedPassiveInventoryRepository.findBySerial(serialNumber).isPresent();
                        boolean hasObjectId = unmappedPassiveInventoryRepository.findByObjectId(serialNumber).isPresent();
                        if (hasSerial && hasObjectId) {
                            logger.warn("Multiple unmapped PASSIVE records found for asset {} (matching both serial and objectId), deleting first only", serialNumber);
                        }
                        passiveRecord.ifPresent(record -> {
                            unmappedPassiveInventoryRepository.delete(record);
                            logAudit(asset, "UNMAPPED", "MAPPED", "Asset " + serialNumber + " removed from unmapped PASSIVE inventory");
                        });
                        break;
                    case "IT":
                        Optional<UnmappedITInventory> itRecord = unmappedITInventoryRepository.findByHardwareSerialNumber(serialNumber);
                        itRecord.ifPresent(record -> {
                            unmappedITInventoryRepository.delete(record);
                            logAudit(asset, "UNMAPPED", "MAPPED", "Asset " + serialNumber + " removed from unmapped IT inventory");
                        });
                        break;
                    default:
                        logger.warn("Unknown nodeType {} for asset {}, skipping unmapped deletion", nodeType, serialNumber);
                        break;
                }
                // Trigger workflow for DECOMMISSIONED assets with non-zero netCost
                if ("DECOMMISSIONED".equals(asset.getStatusFlag()) && asset.getNetCost() != null && !BigDecimal.ZERO.equals(asset.getNetCost())) {
                    triggerApprovalWorkflow(asset, "pending addition"); // Decommissioned in FR + Inventory with non-zero netCost
                }
            }
            asset.setChangeDate(new Timestamp(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
            financialReportRepo.save(asset);
        }
    }


    /**
     * Handle asset not found in FR (new data uploaded)
     */
    private void handleAssetNotInFR(String identifier, String type) {
        boolean alreadyUnmapped = false;
        switch (type.toUpperCase()) {
            case "ACTIVE":
                alreadyUnmapped = unmappedActiveInventoryRepository.findAllBySerialNumber(identifier)
                        .stream().findFirst().isPresent();
                if (!alreadyUnmapped) {
                    unmappedInventoryService.mapActiveInventoryBySerialNumber(identifier, "SYSTEM");
                    logger.info("Mapped asset {} to unmapped ACTIVE inventory", identifier);
                    logUnmappedAudit(identifier, type, "Asset " + identifier + " added to unmapped ACTIVE inventory");
                }
                break;
            case "PASSIVE":
                alreadyUnmapped = unmappedPassiveInventoryRepository.findBySerialOrObjectId(identifier, identifier)
                        .stream().findFirst().isPresent();
                if (!alreadyUnmapped) {
                    unmappedInventoryService.mapPassiveInventoryByIdentifier(identifier, "SYSTEM");
                    logger.info("Mapped asset {} to unmapped PASSIVE inventory", identifier);
                    logUnmappedAudit(identifier, type, "Asset " + identifier + " added to unmapped PASSIVE inventory");
                }
                break;
            case "IT":
                alreadyUnmapped = unmappedITInventoryRepository.findByHardwareSerialNumber(identifier)
                        .stream().findFirst().isPresent();
                if (!alreadyUnmapped) {
                    unmappedInventoryService.mapITInventoryByIdentifier(identifier, "SYSTEM");
                    logger.info("Mapped asset {} to unmapped IT inventory", identifier);
                    logUnmappedAudit(identifier, type, "Asset " + identifier + " added to unmapped IT inventory");
                }
                break;
            default:
                logger.error("Unknown asset type {} for identifier {}", type, identifier);
                return;
        }
        if (alreadyUnmapped) {
            logger.info("Asset {} already exists in unmapped {} inventory", identifier, type);
        }
    }

    private void logUnmappedAudit(String serialNumber, String nodeType, String notes) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAssetId(null);
        auditLog.setSerialNumber(serialNumber);
        auditLog.setPreviousStatus("UNKNOWN");
        auditLog.setNewStatus("UNMAPPED");
        auditLog.setChangeDate(LocalDateTime.now());
        auditLog.setNodeType(nodeType);
        auditLog.setNotes(notes);
        auditLogRepository.save(auditLog);
    }

    /**
     * Check if asset exists in Active, Passive, or IT inventory
     */
    private boolean checkAssetInInventories(String identifier, String nodeType) {
        switch (nodeType.toUpperCase()) {
            case "ACTIVE":
                return !activeInventoryRepository.findBySerialNumber(identifier).isEmpty();
            case "PASSIVE":
                return !passiveInventoryRepository.findByObjectIdOrSerialNumber(identifier, identifier).isEmpty();
            case "IT":
                return !itInventoryRepository.findByObjectIdOrHostSerialNumber(identifier, identifier).isEmpty();
            default:
                return false;
        }
    }

    /**
     * Trigger synchronization for a specific asset by identifier
     */
    @PostMapping("/sync-asset/{identifier}")
    public ResponseEntity<Map<String, Object>> syncSpecificAsset(@PathVariable String identifier) {
        logger.info("Manual trigger: Sync specific asset: {}", identifier);
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now());

            Optional<tb_FinancialReport> frAsset = financialReportRepo.findByAssetSerialNumber(identifier);
            if (frAsset.isPresent()) {
                tb_FinancialReport asset = frAsset.get();
                processFRAsset(asset);
                response.put("status", "success");
                response.put("message", "Asset synchronization completed");
                response.put("assetDetails", mapAssetDetails(asset));
                return ResponseEntity.ok(response);
            } else {
                String assetType = determineAssetType(identifier);
                logger.info("Asset {} not in financial report, determined type: {}", identifier, assetType);
                handleAssetNotInFR(identifier, assetType);
                response.put("status", "warning");
                response.put("message", "Asset not found in financial report, mapped to unmapped inventory");
                response.put("serialNumber", identifier);
                response.put("assetType", assetType);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("Error during asset synchronization: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("timestamp", LocalDateTime.now());
            errorResponse.put("status", "error");
            errorResponse.put("message", "An error occurred during asset synchronization: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Determine asset type based on presence in inventories
     */
    private String determineAssetType(String identifier) {
        if (!activeInventoryRepository.findBySerialNumber(identifier).isEmpty()) {
            logger.info("Asset {} determined as ACTIVE", identifier);
            return "ACTIVE";
        }
        if (!passiveInventoryRepository.findByObjectIdOrSerialNumber(identifier, identifier).isEmpty()) {
            logger.info("Asset {} determined as PASSIVE", identifier);
            return "PASSIVE";
        }
        if (!itInventoryRepository.findByObjectIdOrHostSerialNumber(identifier, identifier).isEmpty()) {
            logger.warn("Asset {} determined as IT", identifier);
            return "IT";
        }
        logger.warn("Asset {} not found in any inventory, defaulting to ACTIVE", identifier);
        return "ACTIVE";
    }

    /**
     * Check the current status of a specific asset
     */
    @GetMapping("/check-status/{identifier}")
    public ResponseEntity<Map<String, Object>> checkAssetStatus(@PathVariable String identifier) {
        logger.info("Checking status for asset: {}", identifier);
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now());

            Optional<tb_FinancialReport> frAsset = financialReportRepo.findByAssetSerialNumber(identifier);
            if (frAsset.isPresent()) {
                tb_FinancialReport asset = frAsset.get();
                processFRAsset(asset);
                response.put("status", "success");
                response.put("foundIn", "tb_FinancialReport");
                response.put("assetDetails", mapAssetDetails(asset));

                List<ApprovalWorkflow> workflows = approvalWorkflowRepository.findByAssetId(String.valueOf(asset.getId()));
                if (!workflows.isEmpty()) {
                    response.put("workflows", workflows);
                }

                List<AuditLog> auditLogs = auditLogRepository.findByAssetIdOrSerialNumber(
                        String.valueOf(asset.getId()), asset.getAssetSerialNumber(),
                        PageRequest.of(0, 10, Sort.by("changeDate").descending()));
                response.put("recentAuditLogs", auditLogs);

                return ResponseEntity.ok(response);
            } else {
                response.put("status", "error");
                response.put("message", "Asset not found in Financial Report, check unmapped inventory");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("Error checking asset status: ", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error checking asset status: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get a paginated list of unmapped assets
     */
    @GetMapping("/unmapped-assets")
    public ResponseEntity<Map<String, Object>> getUnmappedAssets(
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("Getting unmapped assets of type: {}, page: {}, size: {}", type, page, size);
        try {
            Map<String, Object> response = new HashMap<>();
            Pageable pageable = PageRequest.of(page, size, Sort.by("insertDate").descending());

            switch (type.toUpperCase()) {
                case "ACTIVE":
                    Page<UnmappedActiveInventory> activeAssets = unmappedActiveInventoryRepository.findAll(pageable);
                    response.put("assets", activeAssets.getContent());
                    response.put("type", "ACTIVE");
                    response.put("totalItems", activeAssets.getTotalElements());
                    response.put("totalPages", activeAssets.getTotalPages());
                    response.put("currentPage", activeAssets.getNumber());
                    break;

                case "PASSIVE":
                    Page<UnmappedPassiveInventory> passiveAssets = unmappedPassiveInventoryRepository.findAll(pageable);
                    response.put("assets", passiveAssets.getContent());
                    response.put("type", "PASSIVE");
                    response.put("totalItems", passiveAssets.getTotalElements());
                    response.put("totalPages", passiveAssets.getTotalPages());
                    response.put("currentPage", passiveAssets.getNumber());
                    break;

                case "IT":
                    Page<UnmappedITInventory> itAssets = unmappedITInventoryRepository.findAll(pageable);
                    response.put("assets", itAssets.getContent());
                    response.put("type", "IT");
                    response.put("totalItems", itAssets.getTotalElements());
                    response.put("totalPages", itAssets.getTotalPages());
                    response.put("currentPage", itAssets.getNumber());
                    break;

                case "ALL":
                default:
                    List<Object> combinedAssets = new ArrayList<>();
                    Page<UnmappedActiveInventory> active = unmappedActiveInventoryRepository.findAll(pageable);
                    active.getContent().forEach(asset -> {
                        Map<String, Object> assetMap = new HashMap<>();
                        assetMap.put("id", asset.getId());
                        assetMap.put("serialNumber", asset.getSerialNumber());
                        assetMap.put("assetType", "ACTIVE");
                        assetMap.put("discoveredDate", asset.getInsertDate());
                        combinedAssets.add(assetMap);
                    });
                    Page<UnmappedPassiveInventory> passive = unmappedPassiveInventoryRepository.findAll(pageable);
                    passive.getContent().forEach(asset -> {
                        Map<String, Object> assetMap = new HashMap<>();
                        assetMap.put("id", asset.getObjectId());
                        assetMap.put("objectId", asset.getObjectId());
                        assetMap.put("serialNumber", asset.getSerial());
                        assetMap.put("discoveredDate", asset.getEntryDate());
                        assetMap.put("assetType", "PASSIVE");
                        combinedAssets.add(assetMap);
                    });
                    Page<UnmappedITInventory> it = unmappedITInventoryRepository.findAll(pageable);
                    it.getContent().forEach(asset -> {
                        Map<String, Object> assetMap = new HashMap<>();
                        assetMap.put("id", asset.getElementId());
                        assetMap.put("objectId", asset.getElementId());
                        assetMap.put("serialNumber", asset.getHostSerialNumber());
                        assetMap.put("discoveredDate", asset.getAssetInsertDate());
                        assetMap.put("assetType", "IT");
                        combinedAssets.add(assetMap);
                    });

                    combinedAssets.sort((a, b) -> {
                        Date dateA = (Date) ((Map<String, Object>) a).get("discoveredDate");
                        Date dateB = (Date) ((Map<String, Object>) b).get("discoveredDate");
                        return dateB.compareTo(dateA);
                    });

                    response.put("assets", combinedAssets);
                    response.put("type", "ALL");

                    long totalActive = unmappedActiveInventoryRepository.count();
                    long totalPassive = unmappedPassiveInventoryRepository.count();
                    long totalIt = unmappedITInventoryRepository.count();
                    long totalItems = totalActive + totalPassive + totalIt;

                    response.put("totalItems", totalItems);
                    response.put("totalActive", totalActive);
                    response.put("totalPassive", totalPassive);
                    response.put("totalIt", totalIt);
                    response.put("currentPage", page);
                    response.put("totalPages", (int) Math.ceil(totalItems / (double) size));
                    break;
            }

            response.put("status", "success");
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving unmapped assets: ", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error retrieving unmapped assets: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get pending approvals for assets
     */
    @GetMapping("/pending-approvals")
    public ResponseEntity<Map<String, Object>> getPendingApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("Getting pending approvals, page: {}, size: {}", page, size);
        try {
            Map<String, Object> response = new HashMap<>();
            Pageable pageable = PageRequest.of(page, size, Sort.by("insertDate").descending());

            Page<ApprovalWorkflow> approvalsPage = approvalWorkflowRepository.findByUpdatedStatus("PENDING", pageable);

            response.put("status", "success");
            response.put("timestamp", LocalDateTime.now());
            response.put("approvals", approvalsPage.getContent());
            response.put("totalItems", approvalsPage.getTotalElements());
            response.put("totalPages", approvalsPage.getTotalPages());
            response.put("currentPage", approvalsPage.getNumber());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving pending approvals: ", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error retrieving pending approvals: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Trigger approval workflow with specific original status
     */
    private void triggerApprovalWorkflow(tb_FinancialReport asset, String originalStatus) {
        // Check for existing workflow to avoid duplicates
        Optional<ApprovalWorkflow> existingWorkflow = approvalWorkflowRepository.findByAssetIdAndUpdatedStatus(
                asset.getId().toString(), "Pending Addition");
        if (existingWorkflow.isPresent()) {
            logger.info("Workflow already exists for asset {} with status Pending Addition", asset.getAssetSerialNumber());
            return;
        }

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setAssetId(asset.getId().toString());
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
        financialReportRepo.save(asset);

        logger.info("Triggered approval workflow for asset {} with original status: {}",
                asset.getAssetSerialNumber(), originalStatus);
        logAudit(asset, asset.getStatusFlag(), originalStatus,
                "Workflow triggered: " + originalStatus);
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
        AuditLog auditLog = new AuditLog();
        auditLog.setAssetId(asset != null ? String.valueOf(asset.getId()) : null);
        auditLog.setSerialNumber(asset != null ? asset.getAssetSerialNumber() : null);
        auditLog.setPreviousStatus(previousStatus);
        auditLog.setNewStatus(newStatus);
        auditLog.setChangeDate(LocalDateTime.now());
        auditLog.setNodeType(asset != null ? asset.getNodeType() : null);
        auditLog.setNotes(notes);
        auditLogRepository.save(auditLog);
    }

    /**
     * Map financial report asset details to a simplified object
     */
    private Map<String, Object> mapAssetDetails(tb_FinancialReport asset) {
        Map<String, Object> details = new HashMap<>();
        details.put("id", asset.getId());
        details.put("siteId", asset.getSiteId());
        details.put("assetSerialNumber", asset.getAssetSerialNumber());
        details.put("statusFlag", asset.getStatusFlag());
        details.put("financialApprovalStatus", asset.getFinancialApprovalStatus());
        details.put("nodeType", asset.getNodeType());
        details.put("changeDate", asset.getChangeDate());
        details.put("retirementDate", asset.getRetirementDate());
        details.put("netCost", asset.getNetCost());
        details.put("writeOffDate", asset.getWriteOffDate());
        return details;
    }
}