package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.ActiveInventory;
import com.telkom.co.ke.almoptics.models.*;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for tracking assets that are in tb_FinancialReport
 * but not found in any inventory system. These are considered potentially
 * missing assets and are tracked for 14 days before being marked as decommissioned.
 */
@Service
public class MissingAssetCheckService {

    private static final Logger logger = LoggerFactory.getLogger(MissingAssetCheckService.class);
    private static final int MISSING_ASSET_GRACE_PERIOD_DAYS = 14;

    @Autowired
    private FinancialReportRepo financialReportRepo;

    @Autowired
    private ActiveInventoryRepository activeInventoryRepository;

    @Autowired
    private PassiveInventoryRepository passiveInventoryRepository;

    @Autowired
    private ItInventoryRepository itInventoryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationService notificationService;

    /**
     * Scheduled job to identify and track missing assets
     * Runs daily at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledMissingAssetCheck() {
        logger.info("Starting scheduled missing asset check");
        checkForMissingAssets();
        updateMissingAssetStatus();
        logger.info("Completed scheduled missing asset check");
    }

    /**
     * Checks for assets that exist in tb_FinancialReport but not in any inventory
     */
    @Transactional
    public void checkForMissingAssets() {
        logger.info("Checking for assets that are in FR but not in inventory");

        // Get all assets from tb_FinancialReport that are not already marked as DECOMMISSIONED
        List<tb_FinancialReport> financialReports =
                financialReportRepo.findByStatusFlagNot("DECOMMISSIONED");

        for (tb_FinancialReport financialReport : financialReports) {
            String siteId = financialReport.getSiteId(); // Replaced getObjectId() with getSiteId()
            String serialNumber = financialReport.getAssetSerialNumber();

            // Skip if either siteId or serialNumber is null
            if (siteId == null || serialNumber == null) {
                logger.warn("Active asset not in FR: siteId={}, serialNumber={}", siteId, serialNumber);
                continue;
            }

            // Check if asset exists in any inventory
            // Retrieve inventoryId from ActiveInventory using serialNumber
            List<ActiveInventory> activeInventories = activeInventoryRepository.findBySerialNumber(serialNumber);
            int id = activeInventories.isEmpty() ? -1 : activeInventories.get(0).getId(); // Take first ID or -1 if not found

            // Check if asset exists in any inventory
            boolean assetExists = assetExistsInInventory(siteId, id, serialNumber);

            if (!assetExists) {
                // Asset not found in any inventory - mark as potentially missing
                handlePotentiallyMissingAsset(financialReport);
            } else if (financialReport.getRetirementDate() != null) { // Fixed from getMissingDate()
                // Asset was missing but now found - reset the retirement date
                resetMissingAsset(financialReport);
            }
        }
    }



    /**
     * Checks if an asset exists in any inventory system
     *
     * @param objectId Asset object ID
     * @param serialNumber Asset serial number
     * @return true if asset exists in any inventory, false otherwise
     */
    private boolean assetExistsInInventory(String objectId, int inventoryId, String serialNumber) {
        // Check active inventory

        if (!activeInventoryRepository.findBySerialNumber(serialNumber).isEmpty()) {
            return true;
        }

        // Check passive inventory
        if (!passiveInventoryRepository.findByObjectIdOrSerialNumber(objectId, serialNumber).isEmpty()) {
            return true;
        }

        // Check IT inventory
        if (!itInventoryRepository.findByObjectIdOrHostSerialNumber(objectId, serialNumber).isEmpty()) {
            return true;
        }

        return false;
    }

    /**
     * Handles an asset that is potentially missing
     * Sets missing_date if not already set
     *
     */
    private void handlePotentiallyMissingAsset(tb_FinancialReport financialReport) {
        // If retirementDate (or writeOffDate) is not set, mark asset as missing
        if (financialReport.getRetirementDate() == null) { // Changed from getMissingDate()
            Date now = new Date(); // Use Date instead of LocalDateTime
            financialReport.setRetirementDate(now); // Set retirement date as missing date
            financialReportRepo.save(financialReport);

            // Log the missing status
            createAuditLog(
                    financialReport.getSiteId(), // Replaced getObjectId() with getSiteId()
                    financialReport.getAssetSerialNumber(),
                    financialReport.getNodeType(),
                    financialReport.getStatusFlag(),
                    "POTENTIALLY_MISSING",
                    "Asset not found in any inventory - starting 14-day tracking period"
            );

            logger.info("Asset marked as potentially missing: {}", financialReport.getSiteId());

            // Send notification about potentially missing asset
            sendMissingAssetNotification(financialReport, false);
        }
    }

    /**
     * Resets the missing status of an asset that was previously missing but now found
     *
     *
     */
    private void resetMissingAsset(tb_FinancialReport financialReport) {
        // Clear the retirement date (previously used as missing date)
        financialReport.setRetirementDate(null); // Changed from setMissingDate(null)
        financialReportRepo.save(financialReport);

        // Log the reset
        createAuditLog(
                financialReport.getSiteId(), // Replaced getObjectId() with getSiteId()
                financialReport.getAssetSerialNumber(),
                financialReport.getNodeType(),
                "POTENTIALLY_MISSING",
                financialReport.getStatusFlag(),
                "Asset previously marked as missing has been found - tracking period reset"
        );

        logger.info("Previously missing asset has been found: {}", financialReport.getSiteId());
    }


    /**
     * Checks missing assets and updates status if they've been missing for more than 14 days
     */
    @Transactional
    public void updateMissingAssetStatus() {
        logger.info("Checking assets that have been missing for more than 14 days");

        // Get all assets with a retirement date set
        List<tb_FinancialReport> missingAssets =
                financialReportRepo.findByRetirementDateIsNotNull(); // Fixed from findByDecommissionedDateIsNotNull()

        LocalDateTime now = LocalDateTime.now();

        for (tb_FinancialReport asset : missingAssets) {
            if (asset.getRetirementDate() != null) { // Changed from getMissingDate()
                // Convert Date to LocalDateTime
                LocalDateTime retirementDate = asset.getRetirementDate().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();

                // Calculate days missing
                long daysMissing = ChronoUnit.DAYS.between(retirementDate, now);

                // If missing for more than 14 days, mark as DECOMMISSIONED
                if (daysMissing >= MISSING_ASSET_GRACE_PERIOD_DAYS) {
                    markAsDecommissioned(asset);
                }
            }
        }
    }



    /**
     * Marks an asset as DECOMMISSIONED after being missing for the grace period
     *
     */
    private void markAsDecommissioned(tb_FinancialReport financialReport) {
        String previousStatus = financialReport.getStatusFlag();

        // Update status to DECOMMISSIONED
        financialReport.setStatusFlag("DECOMMISSIONED");
        financialReport.setRetirementDate(new Date()); // Changed from setDecommissionedDate()

        // Keep retirement date for reference
        financialReportRepo.save(financialReport);

        // Log the decommissioning
        createAuditLog(
                financialReport.getSiteId(), // Replaced getObjectId() with getSiteId()
                financialReport.getAssetSerialNumber(),
                financialReport.getNodeType(),
                previousStatus,
                "DECOMMISSIONED",
                "Asset marked as DECOMMISSIONED after being missing for " +
                        MISSING_ASSET_GRACE_PERIOD_DAYS + " days"
        );

        logger.info("Asset marked as DECOMMISSIONED after being missing for {} days: {}",
                MISSING_ASSET_GRACE_PERIOD_DAYS, financialReport.getSiteId());

        // Send notification about decommissioned asset
        sendMissingAssetNotification(financialReport, true);
    }


    /**
     * Creates an audit log entry
     *
     * @param objectId Asset object ID
     * @param serialNumber Asset serial number
     * @param nodeType Type of asset (ACTIVE, PASSIVE, IT)
     * @param previousStatus Previous status
     * @param newStatus New status
     * @param notes Additional notes about the status change
     */
    private void createAuditLog(String objectId, String serialNumber, String nodeType,
                                String previousStatus, String newStatus, String notes) {
        AuditLog auditLog = new AuditLog();
        //auditLog.setObjectId(objectId);
        auditLog.setSerialNumber(serialNumber);
        auditLog.setPreviousStatus(previousStatus);
        auditLog.setNewStatus(newStatus);
       //auditLog.setChangeDate(LocalDateTime.now());
        auditLog.setNodeType(nodeType);
        auditLog.setNotes(notes);

        auditLogRepository.save(auditLog);
    }

    /**
     * Sends notification about missing or decommissioned assets
     *
     * @param isDecommissioned Whether the asset is being decommissioned (true) or just marked missing (false)
     */
    private void sendMissingAssetNotification(tb_FinancialReport financialReport, boolean isDecommissioned) {
        String subject;
        String message;

        if (isDecommissioned) {
            subject = "ASSET DECOMMISSIONED: " + financialReport.getSiteId(); // Replaced getObjectId()
            message = "Asset " + financialReport.getSiteId() + // Replaced getObjectId()
                    " (S/N: " + financialReport.getAssetSerialNumber() + ") " +
                    "has been automatically marked as DECOMMISSIONED after being missing for " +
                    MISSING_ASSET_GRACE_PERIOD_DAYS + " days.\n\n" +
                    "Asset Type: " + financialReport.getNodeType() + "\n" +
                    "First Missing Date: " + financialReport.getRetirementDate() + "\n" + // Replaced getMissingDate()
                    "Decommissioned Date: " + financialReport.getRetirementDate(); // Replaced getDecommissionedDate()
        } else {
            subject = "ASSET POTENTIALLY MISSING: " + financialReport.getSiteId(); // Replaced getObjectId()
            message = "Asset " + financialReport.getSiteId() + // Replaced getObjectId()
                    " (S/N: " + financialReport.getAssetSerialNumber() + ") " +
                    "has been marked as potentially missing. " +
                    "The asset is in tb_FinancialReport but was not found in any inventory.\n\n" +
                    "Asset Type: " + financialReport.getNodeType() + "\n" +
                    "Missing Date: " + financialReport.getRetirementDate() + "\n\n" + // Replaced getMissingDate()
                    "If the asset is not found within " + MISSING_ASSET_GRACE_PERIOD_DAYS +
                    " days, it will be automatically marked as DECOMMISSIONED.";
        }

        notificationService.sendNotification(subject, message);
    }


}