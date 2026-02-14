package com.zain.bh.alm.financials.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.entity.UnmappedActiveInventory;
import com.zain.bh.alm.financials.entity.UnmappedITInventory;
import com.zain.bh.alm.financials.entity.UnmappedPassiveInventory;
import com.zain.bh.alm.financials.repository.FinancialReportRepository;
import com.zain.bh.alm.financials.repository.ITInventoryRepository;
import com.zain.bh.alm.financials.repository.NodeRepository;
import com.zain.bh.alm.financials.repository.PassiveInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedActiveInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedITInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedPassiveInventoryRepository;

@Service
public class AssetProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetProcessingService.class);

    private final FinancialReportRepository financialReportRepository;
    private final NodeRepository activeInventoryRepository;
    private final PassiveInventoryRepository passiveInventoryRepository;
    private final ITInventoryRepository itInventoryRepository;
    private final UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;
    private final UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;
    private final UnmappedITInventoryRepository unmappedITInventoryRepository;
    private final AuditLogService auditLogService;
    private final AssetWorkflowService assetWorkflowService;
    private final UnmappedInventoryService unmappedInventoryService;

    public AssetProcessingService(FinancialReportRepository financialReportRepository,
                                  NodeRepository activeInventoryRepository,
                                  PassiveInventoryRepository passiveInventoryRepository,
                                  ITInventoryRepository itInventoryRepository,
                                  UnmappedActiveInventoryRepository unmappedActiveInventoryRepository,
                                  UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository,
                                  UnmappedITInventoryRepository unmappedITInventoryRepository,
                                  AuditLogService auditLogService,
                                  AssetWorkflowService assetWorkflowService,
                                  UnmappedInventoryService unmappedInventoryService) {
        this.financialReportRepository = financialReportRepository;
        this.activeInventoryRepository = activeInventoryRepository;
        this.passiveInventoryRepository = passiveInventoryRepository;
        this.itInventoryRepository = itInventoryRepository;
        this.unmappedActiveInventoryRepository = unmappedActiveInventoryRepository;
        this.unmappedPassiveInventoryRepository = unmappedPassiveInventoryRepository;
        this.unmappedITInventoryRepository = unmappedITInventoryRepository;
        this.auditLogService = auditLogService;
        this.assetWorkflowService = assetWorkflowService;
        this.unmappedInventoryService = unmappedInventoryService;
    }

    public void processBatchSync(List<String> identifiers, String type) {
        List<FinancialReport> frAssets = financialReportRepository.findByAssetSerialNumberIn(identifiers);
        Set<String> frSerials = frAssets.stream().map(FinancialReport::getAssetSerialNumber)
                .collect(Collectors.toSet());

        frAssets.forEach(this::processFRAsset);

        identifiers.stream().filter(id -> !frSerials.contains(id))
                .forEach(id -> handleAssetNotInFR(id, type));
    }

    public void processBatchForUnmapped(List<String> identifiers, String type) {
        List<FinancialReport> frAssets = financialReportRepository.findByAssetSerialNumberIn(identifiers);
        Set<String> frSerials = frAssets.stream().map(FinancialReport::getAssetSerialNumber)
                .collect(Collectors.toSet());

        identifiers.stream().filter(id -> !frSerials.contains(id))
                .forEach(id -> handleAssetNotInFR(id, type));
    }

    public void processFRAsset(FinancialReport asset) {
        LocalDateTime now = LocalDateTime.now();
        Date insertDateRaw = asset.getInsertDate();
        Date changeDateRaw = asset.getChangeDate();
        LocalDateTime insertDate = insertDateRaw != null
                ? insertDateRaw.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : changeDateRaw.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        long daysSinceInsert = ChronoUnit.DAYS.between(insertDate, now);

        if (!"DECOMMISSIONED".equals(asset.getStatusFlag()) && asset.getFinancialApprovalStatus() == null) {
            String newStatus = daysSinceInsert < 30 ? "NEW" : "EXISTING";
            if (!newStatus.equals(asset.getStatusFlag())) {
                String previousStatus = asset.getStatusFlag();
                asset.setStatusFlag(newStatus);
                asset.setChangeDate(new Timestamp(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
                financialReportRepository.save(asset);
                auditLogService.logAudit(asset, previousStatus, newStatus, "Status updated based on days since insert");
            }
        }

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
                financialReportRepository.save(asset);
                auditLogService.logAudit(asset, previousStatus, "DECOMMISSIONED",
                        "Asset not found in inventories after 14-day check");
            } else if (foundInInventory) {
                removeFromUnmappedInventory(asset);
                if ("DECOMMISSIONED".equals(asset.getStatusFlag()) && asset.getNetCost() != null
                        && !BigDecimal.ZERO.equals(asset.getNetCost())) {
                    assetWorkflowService.triggerApprovalWorkflow(asset, "pending addition");
                }
            }
            asset.setChangeDate(new Timestamp(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
            financialReportRepository.save(asset);
        }
    }

    private void removeFromUnmappedInventory(FinancialReport asset) {
        String serialNumber = asset.getAssetSerialNumber();
        String nodeType = asset.getNodeType().toUpperCase();
        switch (nodeType) {
        case "ACTIVE":
            Optional<UnmappedActiveInventory> activeRecord = unmappedActiveInventoryRepository
                    .findBySerialNumber(serialNumber);
            long activeCount = unmappedActiveInventoryRepository.findAllBySerialNumber(serialNumber).size();
            if (activeCount > 1) {
                LOGGER.warn("Multiple unmapped ACTIVE records found for asset {}, deleting first only", serialNumber);
            }
            activeRecord.ifPresent(record -> {
                unmappedActiveInventoryRepository.delete(record);
                auditLogService.logAudit(asset, "UNMAPPED", "MAPPED",
                        "Asset " + serialNumber + " removed from unmapped ACTIVE inventory");
            });
            break;
        case "PASSIVE":
            Optional<UnmappedPassiveInventory> passiveRecord = unmappedPassiveInventoryRepository
                    .findBySerialOrObjectId(serialNumber, serialNumber);
            boolean hasSerial = unmappedPassiveInventoryRepository.findBySerial(serialNumber).isPresent();
            boolean hasObjectId = unmappedPassiveInventoryRepository.findByObjectId(serialNumber).isPresent();
            if (hasSerial && hasObjectId) {
                LOGGER.warn(
                        "Multiple unmapped PASSIVE records found for asset {} (matching both serial and objectId), deleting first only",
                        serialNumber);
            }
            passiveRecord.ifPresent(record -> {
                unmappedPassiveInventoryRepository.delete(record);
                auditLogService.logAudit(asset, "UNMAPPED", "MAPPED",
                        "Asset " + serialNumber + " removed from unmapped PASSIVE inventory");
            });
            break;
        case "IT":
            Optional<UnmappedITInventory> itRecord = unmappedITInventoryRepository
                    .findByHardwareSerialNumber(serialNumber);
            itRecord.ifPresent(record -> {
                unmappedITInventoryRepository.delete(record);
                auditLogService.logAudit(asset, "UNMAPPED", "MAPPED",
                        "Asset " + serialNumber + " removed from unmapped IT inventory");
            });
            break;
        default:
            LOGGER.warn("Unknown nodeType {} for asset {}, skipping unmapped deletion", nodeType, serialNumber);
            break;
        }
    }

    private void handleAssetNotInFR(String identifier, String type) {
        boolean alreadyUnmapped = false;
        switch (type.toUpperCase()) {
        case "ACTIVE":
            alreadyUnmapped = unmappedActiveInventoryRepository.findAllBySerialNumber(identifier).stream().findFirst()
                    .isPresent();
            if (!alreadyUnmapped) {
                unmappedInventoryService.mapActiveInventoryBySerialNumber(identifier, "SYSTEM");
                auditLogService.logUnmappedAudit(identifier, type,
                        "Asset " + identifier + " added to unmapped ACTIVE inventory");
            }
            break;
        case "PASSIVE":
            alreadyUnmapped = unmappedPassiveInventoryRepository.findBySerialOrObjectId(identifier, identifier).stream()
                    .findFirst().isPresent();
            if (!alreadyUnmapped) {
                unmappedInventoryService.mapPassiveInventoryByIdentifier(identifier, "SYSTEM");
                auditLogService.logUnmappedAudit(identifier, type,
                        "Asset " + identifier + " added to unmapped PASSIVE inventory");
            }
            break;
        case "IT":
            alreadyUnmapped = unmappedITInventoryRepository.findByHardwareSerialNumber(identifier).stream().findFirst()
                    .isPresent();
            if (!alreadyUnmapped) {
                unmappedInventoryService.mapITInventoryByIdentifier(identifier, "SYSTEM");
                auditLogService.logUnmappedAudit(identifier, type,
                        "Asset " + identifier + " added to unmapped IT inventory");
            }
            break;
        default:
            LOGGER.error("Unknown asset type {} for identifier {}", type, identifier);
            return;
        }
    }

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

    public String determineAssetType(String identifier) {
        if (!activeInventoryRepository.findBySerialNumber(identifier).isEmpty()) {
            return "ACTIVE";
        }
        if (!passiveInventoryRepository.findByObjectIdOrSerialNumber(identifier, identifier).isEmpty()) {
            return "PASSIVE";
        }
        if (!itInventoryRepository.findByObjectIdOrHostSerialNumber(identifier, identifier).isEmpty()) {
            return "IT";
        }
        LOGGER.warn("Asset {} not found in any inventory, defaulting to ACTIVE", identifier);
        return "ACTIVE";
    }
}
