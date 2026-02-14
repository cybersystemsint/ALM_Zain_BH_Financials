package com.zain.bh.alm.financials.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zain.bh.alm.financials.entity.AuditLog;
import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.entity.Node;
import com.zain.bh.alm.financials.repository.AuditLogRepository;
import com.zain.bh.alm.financials.repository.FinancialReportRepository;
import com.zain.bh.alm.financials.repository.ITInventoryRepository;
import com.zain.bh.alm.financials.repository.NodeRepository;
import com.zain.bh.alm.financials.repository.PassiveInventoryRepository;

/**
 * Service responsible for tracking assets that are in tb_FinancialReport
 * but not found in any inventory system. These are considered potentially
 * missing assets and are tracked for 14 days before being marked as decommissioned.
 */
@Service
public class MissingAssetCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MissingAssetCheckService.class);
    private static final int MISSING_ASSET_GRACE_PERIOD_DAYS = 14;

    private final FinancialReportRepository financialReportRepository;
    private final NodeRepository activeInventoryRepository;
    private final PassiveInventoryRepository passiveInventoryRepository;
    private final ITInventoryRepository itInventoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;

    public MissingAssetCheckService(FinancialReportRepository financialReportRepository,
            NodeRepository activeInventoryRepository, PassiveInventoryRepository passiveInventoryRepository,
            ITInventoryRepository itInventoryRepository, AuditLogRepository auditLogRepository,
            NotificationService notificationService) {
        this.financialReportRepository = financialReportRepository;
        this.activeInventoryRepository = activeInventoryRepository;
        this.passiveInventoryRepository = passiveInventoryRepository;
        this.itInventoryRepository = itInventoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledMissingAssetCheck() {
        LOGGER.info("Starting scheduled missing asset check");
        checkForMissingAssets();
        updateMissingAssetStatus();
        LOGGER.info("Completed scheduled missing asset check");
    }

    @Transactional
    public void checkForMissingAssets() {
        LOGGER.info("Checking for assets that are in FR but not in inventory");
        List<FinancialReport> financialReports = financialReportRepository.findByStatusFlagNot("DECOMMISSIONED");

        for (FinancialReport financialReport : financialReports) {
            String siteId = financialReport.getSiteId();
            String serialNumber = financialReport.getAssetSerialNumber();

            if (siteId == null || serialNumber == null) {
                LOGGER.warn("Active asset not in FR: siteId={}, serialNumber={}", siteId, serialNumber);
                continue;
            }

            List<Node> activeInventories = activeInventoryRepository.findBySerialNumber(serialNumber);
            int id = activeInventories.isEmpty() ? -1 : activeInventories.get(0).getId();
            boolean assetExists = assetExistsInInventory(siteId, id, serialNumber);

            if (!assetExists) {
                handlePotentiallyMissingAsset(financialReport);
            } else if (financialReport.getRetirementDate() != null) {
                resetMissingAsset(financialReport);
            }
        }
    }

    private boolean assetExistsInInventory(String objectId, int inventoryId, String serialNumber) {
        if (!activeInventoryRepository.findBySerialNumber(serialNumber).isEmpty()) {
            return true;
        }
        if (!passiveInventoryRepository.findByObjectIdOrSerialNumber(objectId, serialNumber).isEmpty()) {
            return true;
        }
        if (!itInventoryRepository.findByObjectIdOrHostSerialNumber(objectId, serialNumber).isEmpty()) {
            return true;
        }
        return false;
    }

    private void handlePotentiallyMissingAsset(FinancialReport financialReport) {
        if (financialReport.getRetirementDate() == null) {
            Date now = new Date();
            financialReport.setRetirementDate(now);
            financialReportRepository.save(financialReport);
            createAuditLog(financialReport.getSiteId(), financialReport.getAssetSerialNumber(),
                    financialReport.getNodeType(), financialReport.getStatusFlag(), "POTENTIALLY_MISSING",
                    "Asset not found in any inventory - starting 14-day tracking period");
            LOGGER.info("Asset marked as potentially missing: {}", financialReport.getSiteId());
            sendMissingAssetNotification(financialReport, false);
        }
    }

    private void resetMissingAsset(FinancialReport financialReport) {
        financialReport.setRetirementDate(null);
        financialReportRepository.save(financialReport);
        createAuditLog(financialReport.getSiteId(), financialReport.getAssetSerialNumber(),
                financialReport.getNodeType(), "POTENTIALLY_MISSING", financialReport.getStatusFlag(),
                "Asset previously marked as missing has been found - tracking period reset");
        LOGGER.info("Previously missing asset has been found: {}", financialReport.getSiteId());
    }
    @Transactional
    public void updateMissingAssetStatus() {
        LOGGER.info("Checking assets that have been missing for more than 14 days");
        List<FinancialReport> missingAssets = financialReportRepository.findByRetirementDateIsNotNull();
        LocalDateTime now = LocalDateTime.now();

        for (FinancialReport asset : missingAssets) {
            if (asset.getRetirementDate() != null) {
                LocalDateTime retirementDate = asset.getRetirementDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                long daysMissing = ChronoUnit.DAYS.between(retirementDate, now);
                if (daysMissing >= MISSING_ASSET_GRACE_PERIOD_DAYS) {
                    markAsDecommissioned(asset);
                }
            }
        }
    }
    private void markAsDecommissioned(FinancialReport financialReport) {
        String previousStatus = financialReport.getStatusFlag();
        financialReport.setStatusFlag("DECOMMISSIONED");
        financialReport.setRetirementDate(new Date());
        financialReportRepository.save(financialReport);
        createAuditLog(financialReport.getSiteId(), financialReport.getAssetSerialNumber(),
                financialReport.getNodeType(), previousStatus, "DECOMMISSIONED",
                "Asset marked as DECOMMISSIONED after being missing for " + MISSING_ASSET_GRACE_PERIOD_DAYS + " days");
        LOGGER.info("Asset marked as DECOMMISSIONED after being missing for {} days: {}",
                MISSING_ASSET_GRACE_PERIOD_DAYS, financialReport.getSiteId());
        sendMissingAssetNotification(financialReport, true);
    }
    private void createAuditLog(String objectId, String serialNumber, String nodeType,
            String previousStatus, String newStatus, String notes) {
        AuditLog auditLog = new AuditLog();
        auditLog.setSerialNumber(serialNumber);
        auditLog.setPreviousStatus(previousStatus);
        auditLog.setNewStatus(newStatus);
        auditLog.setNodeType(nodeType);
        auditLog.setNotes(notes);
        auditLogRepository.save(auditLog);
    }

    private void sendMissingAssetNotification(FinancialReport financialReport, boolean isDecommissioned) {
        String subject;
        String message;

        if (isDecommissioned) {
            subject = "ASSET DECOMMISSIONED: " + financialReport.getSiteId();
            message = "Asset " + financialReport.getSiteId() + " (S/N: " + financialReport.getAssetSerialNumber() + ") " +
                    "has been automatically marked as DECOMMISSIONED after being missing for " +
                    MISSING_ASSET_GRACE_PERIOD_DAYS + " days.\n\n" +
                    "Asset Type: " + financialReport.getNodeType() + "\n" +
                    "First Missing Date: " + financialReport.getRetirementDate() + "\n" +
                    "Decommissioned Date: " + financialReport.getRetirementDate();
        } else {
            subject = "ASSET POTENTIALLY MISSING: " + financialReport.getSiteId();
            message = "Asset " + financialReport.getSiteId() + " (S/N: " + financialReport.getAssetSerialNumber() + ") " +
                    "has been marked as potentially missing. " +
                    "The asset is in tb_FinancialReport but was not found in any inventory.\n\n" +
                    "Asset Type: " + financialReport.getNodeType() + "\n" +
                    "Missing Date: " + financialReport.getRetirementDate() + "\n\n" +
                    "If the asset is not found within " + MISSING_ASSET_GRACE_PERIOD_DAYS +
                    " days, it will be automatically marked as DECOMMISSIONED.";
        }

        notificationService.sendNotification(subject, message);
    }
}
