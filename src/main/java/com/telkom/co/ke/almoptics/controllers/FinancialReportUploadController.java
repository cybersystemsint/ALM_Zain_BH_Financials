package com.telkom.co.ke.almoptics.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telkom.co.ke.almoptics.entities.tb_ApprovalWorkflow;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.repository.UnmappedActiveInventoryRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedITInventoryRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedLicenseRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedPassiveInventoryRepository;
import com.telkom.co.ke.almoptics.services.ApprovalWorkflowService;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
import com.telkom.co.ke.almoptics.services.InventorySyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/finance")
public class FinancialReportUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FinancialReportController.class);

    @Autowired
    private FinancialReportService financialReportService;

    @Autowired
    private ApprovalWorkflowService approvalWorkflowService;

    @Autowired
    private UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;

    @Autowired
    private UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;

    @Autowired
    private UnmappedITInventoryRepository unmappedITInventoryRepository;

    @Autowired
    private UnmappedLicenseRepository unmappedLicenseRepository;

    @Autowired
    private InventorySyncService inventorySyncService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Validates the identifier (AssetSerialNumber or AssetName) of a financial report.
     * @param report The financial report to validate.
     * @param recordNumber The record number for error messaging.
     * @param identifiersInRequest Set of identifiers to check for duplicates.
     * @return Error message if validation fails, null if valid.
     */
    private String validateIdentifier(tb_FinancialReport report, int recordNumber, Set<String> identifiersInRequest) {
        String identifier = report.getAssetSerialNumber() != null && !report.getAssetSerialNumber().isEmpty() ?
                report.getAssetSerialNumber() : report.getAssetName();

        if (identifier == null || identifier.trim().isEmpty()) {
            return "Record " + recordNumber + ": Invalid or missing identifier";
        }

        if (!identifiersInRequest.add(identifier)) {
            return "Record " + recordNumber + ": Duplicate identifier in upload batch: " + identifier;
        }
        return null;
    }

    /**
     * Validates mandatory fields of a financial report.
     * @param report The financial report to validate.
     * @param recordNumber The record number for error messaging.
     * @return Error message if validation fails, null if valid.
     */
    private String validateMandatoryFields(tb_FinancialReport report, int recordNumber) {
        Map<String, Object> mandatoryFields = new HashMap<>();
        mandatoryFields.put("NodeType", report.getNodeType());
        mandatoryFields.put("AssetName", report.getAssetName());
        mandatoryFields.put("AssetType", report.getAssetType());
        mandatoryFields.put("InstallationDate", report.getInstallationDate());
        mandatoryFields.put("InitialCost", report.getInitialCost());
        mandatoryFields.put("SalvageValue", report.getSalvageValue());
        mandatoryFields.put("PONumber", report.getPoNumber());
        mandatoryFields.put("PODate", report.getPoDate());
        mandatoryFields.put("L1", report.getL1());
        mandatoryFields.put("L2", report.getL2());
        mandatoryFields.put("L3", report.getL3());
        mandatoryFields.put("L4", report.getL4());
        mandatoryFields.put("AccumulatedDepreciationCode", report.getAccumulatedDepreciationCode());
        mandatoryFields.put("DepreciationCode", report.getDepreciationCode());
        mandatoryFields.put("UsefulLifeMonths", report.getUsefulLifeMonths());
        mandatoryFields.put("VendorName", report.getVendorName());
        mandatoryFields.put("VendorNumber", report.getVendorNumber());
        mandatoryFields.put("ProjectNumber", report.getProjectNumber());
        mandatoryFields.put("DateOfService", report.getDateOfService());
        mandatoryFields.put("PoLineNumber", report.getPoLineNumber());

        for (Map.Entry<String, Object> entry : mandatoryFields.entrySet()) {
            String fieldName = entry.getKey();
            Object fieldValue = entry.getValue();

            if (fieldValue instanceof String) {
                String value = (String) fieldValue;
                if (value == null || value.trim().isEmpty()) {
                    return "Record " + recordNumber + ": Missing or invalid mandatory field: " + fieldName;
                }
            } else if (fieldName.equals("UsefulLifeMonths") && (fieldValue == null || (Integer) fieldValue <= 0)) {
                return "Record " + recordNumber + ": Missing or invalid mandatory field: " + fieldName + " (must be a positive integer)";
            } else if (fieldValue instanceof BigDecimal) {
                BigDecimal value = (BigDecimal) fieldValue;
                if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
                    return "Record " + recordNumber + ": Missing or invalid mandatory field: " + fieldName + " (must be non-negative)";
                }
            } else if (fieldValue == null) {
                return "Record " + recordNumber + ": Missing or invalid mandatory field: " + fieldName;
            }
        }
        return null;
    }

    /**
     * Synchronizes inventory data for a financial report.
     * @param report The financial report to sync.
     * @param recordNumber The record number for warning messaging.
     * @param warnings List to store any warnings.
     * @return The updated financial report.
     */
    private tb_FinancialReport syncInventoryData(tb_FinancialReport report, int recordNumber, List<String> warnings) {
        String identifier = report.getAssetSerialNumber() != null && !report.getAssetSerialNumber().isEmpty() ?
                report.getAssetSerialNumber() : report.getAssetName();
        try {
            return inventorySyncService.populateFieldsFromInventory(report);
        } catch (Exception e) {
            logger.warn("Failed to sync inventory for identifier {}: {}", identifier, e.getMessage());
            warnings.add("Record " + recordNumber + ": Failed to sync inventory data for identifier " + identifier + ": " + e.getMessage());
            return report;
        }
    }

    /**
     * Checks if a record has pending workflows.
     * @param identifier The asset identifier.
     * @param recordNumber The record number for warning messaging.
     * @param warnings List to store any warnings.
     * @return True if there are pending workflows, false otherwise.
     */
    private boolean hasPendingWorkflows(String identifier, int recordNumber, List<String> warnings) {
        try {
            List<tb_ApprovalWorkflow> existingWorkflows = approvalWorkflowService.findByAssetId(identifier);
            return existingWorkflows != null && existingWorkflows.stream()
                    .anyMatch(w -> w.getUPDATED_STATUS() != null && w.getUPDATED_STATUS().toLowerCase().startsWith("pending"));
        } catch (Exception e) {
            logger.error("Error querying workflows for identifier: {}", identifier, e);
            warnings.add("Record " + recordNumber + ": Unable to check workflow status for identifier " + identifier + " due to server error");
            return true; // Skip record on error to be safe
        }
    }

    /**
     * Checks if an existing record's assetName exists in NELicense.
     * @param existingReport The existing financial report.
     * @param recordNumber The record number for warning messaging.
     * @param warnings List to store any warnings.
     * @return True if assetName is in NELicense, false otherwise.
     */
    private boolean isAssetInNELicense(tb_FinancialReport existingReport, int recordNumber, List<String> warnings) {
        try {
            return unmappedLicenseRepository.findByElementID(existingReport.getAssetName()).isPresent();
        } catch (Exception e) {
            logger.error("Error querying NELicense for assetName: {}", existingReport.getAssetName(), e);
            warnings.add("Record " + recordNumber + ": Unable to check NELicense for assetName " +
                    existingReport.getAssetName() + " due to server error");
            return false; // Skip record on error
        }
    }

    /**
     * Checks if a new record's identifier exists in unmapped inventories.
     * @param identifier The asset identifier.
     * @param recordNumber The record number for warning messaging.
     * @param warnings List to store any warnings.
     * @return True if found in any unmapped inventory, false otherwise.
     */
    private boolean isInUnmappedInventory(String identifier, int recordNumber, List<String> warnings) {
        try {
            return unmappedActiveInventoryRepository.findBySerialNumber(identifier).isPresent() ||
                    unmappedPassiveInventoryRepository.findBySerial(identifier).isPresent() ||
                    unmappedPassiveInventoryRepository.findByObjectId(identifier).isPresent() ||
                    unmappedPassiveInventoryRepository.findByElementType(identifier).isPresent() ||
                    unmappedITInventoryRepository.findByHostSerialNumber(identifier).isPresent() ||
                    unmappedITInventoryRepository.findByHardwareSerialNumber(identifier).isPresent() ||
                    unmappedITInventoryRepository.findByElementId(identifier).isPresent() ||
                    unmappedITInventoryRepository.findByHostName(identifier).isPresent() ||
                    unmappedLicenseRepository.findByElementID(identifier).isPresent();
        } catch (Exception e) {
            logger.error("Error querying unmapped inventory for identifier: {}", identifier, e);
            warnings.add("Record " + recordNumber + ": Unable to check unmapped inventory for identifier " + identifier + " due to server error");
            return false; // Skip record on error
        }
    }

    /**
     * Serializes the original state of an existing report for modification.
     * @param existingReport The existing financial report.
     * @param identifier The asset identifier.
     * @param recordNumber The record number for warning messaging.
     * @param warnings List to store any warnings.
     * @return The serialized original state as a JSON string, or null if serialization fails.
     */
    private String serializeOriginalState(tb_FinancialReport existingReport, String identifier, int recordNumber, List<String> warnings) {
        try {
            Map<String, Object> originalState = new HashMap<>();
            originalState.put("siteId", existingReport.getSiteId());
            originalState.put("zone", existingReport.getZone());
            originalState.put("nodeType", existingReport.getNodeType());
            originalState.put("assetName", existingReport.getAssetName());
            originalState.put("assetType", existingReport.getAssetType());
            originalState.put("assetCategory", existingReport.getAssetCategory());
            originalState.put("model", existingReport.getModel());
            originalState.put("partNumber", existingReport.getPartNumber());
            originalState.put("assetSerialNumber", existingReport.getAssetSerialNumber());
            originalState.put("installationDate", existingReport.getInstallationDate());
            originalState.put("initialCost", existingReport.getInitialCost());
            originalState.put("monthlyDepreciationAmount", existingReport.getMonthlyDepreciationAmount());
            originalState.put("accumulatedDepreciation", existingReport.getAccumulatedDepreciation());
            originalState.put("netCost", existingReport.getNetCost());
            originalState.put("salvageValue", existingReport.getSalvageValue());
            originalState.put("poNumber", existingReport.getPoNumber());
            originalState.put("poDate", existingReport.getPoDate());
            originalState.put("faCategory", existingReport.getFaCategory());
            originalState.put("l1", existingReport.getL1());
            originalState.put("l2", existingReport.getL2());
            originalState.put("l3", existingReport.getL3());
            originalState.put("l4", existingReport.getL4());
            originalState.put("accumulatedDepreciationCode", existingReport.getAccumulatedDepreciationCode());
            originalState.put("depreciationCode", existingReport.getDepreciationCode());
            originalState.put("usefulLifeMonths", existingReport.getUsefulLifeMonths());
            originalState.put("vendorName", existingReport.getVendorName());
            originalState.put("vendorNumber", existingReport.getVendorNumber());
            originalState.put("projectNumber", existingReport.getProjectNumber());
            originalState.put("description", existingReport.getDescription());
            originalState.put("oracleAssetId", existingReport.getOracleAssetId());
            originalState.put("dateOfService", existingReport.getDateOfService());
            originalState.put("technologySupported", existingReport.getTechnologySupported());
            originalState.put("oldFarCategory", existingReport.getOldFarCategory());
            originalState.put("costCenterData", existingReport.getCostCenterData());
            originalState.put("nepAssetId", existingReport.getNepAssetId());
            originalState.put("deleted", existingReport.getDeleted());
            originalState.put("adjustment", existingReport.getAdjustment());
            originalState.put("writeOffDate", existingReport.getWriteOffDate() != null ? existingReport.getWriteOffDate().toString() : null);
            originalState.put("tag", existingReport.getTag());
            originalState.put("hostSerialNumber", existingReport.getHostSerialNumber());
            originalState.put("taskId", existingReport.getTaskId());
            originalState.put("poLineNumber", existingReport.getPoLineNumber());
            originalState.put("releaseNumber", existingReport.getReleaseNumber());
            originalState.put("spectrumLicenseDate", existingReport.getSpectrumLicenseDate() != null ? existingReport.getSpectrumLicenseDate().toString() : null);
            originalState.put("itemBarCode", existingReport.getItemBarCode());
            originalState.put("rfid", existingReport.getRfid());
            originalState.put("invoiceNumber", existingReport.getInvoiceNumber());

            return objectMapper.writeValueAsString(originalState);
        } catch (Exception e) {
            logger.error("Failed to serialize original state for identifier: {}", identifier, e);
            warnings.add("Record " + recordNumber + ": Failed to serialize original state for identifier: " + identifier);
            return null;
        }
    }

    /**
     * Updates an existing report with non-null values from the uploaded report.
     * @param existingReport The existing financial report.
     * @param report The uploaded financial report.
     */
    private void updateExistingReport(tb_FinancialReport existingReport, tb_FinancialReport report) {
        existingReport.setSiteId(report.getSiteId() != null ? report.getSiteId() : existingReport.getSiteId());
        existingReport.setZone(report.getZone() != null ? report.getZone() : existingReport.getZone());
        existingReport.setNodeType(report.getNodeType() != null ? report.getNodeType() : existingReport.getNodeType());
        existingReport.setAssetName(report.getAssetName() != null ? report.getAssetName() : existingReport.getAssetName());
        existingReport.setAssetType(report.getAssetType() != null ? report.getAssetType() : existingReport.getAssetType());
        existingReport.setAssetCategory(report.getAssetCategory() != null ? report.getAssetCategory() : existingReport.getAssetCategory());
        existingReport.setModel(report.getModel() != null ? report.getModel() : existingReport.getModel());
        existingReport.setPartNumber(report.getPartNumber() != null ? report.getPartNumber() : existingReport.getPartNumber());
        existingReport.setAssetSerialNumber(report.getAssetSerialNumber() != null ? report.getAssetSerialNumber() : existingReport.getAssetSerialNumber());
        existingReport.setInstallationDate(report.getInstallationDate() != null ? report.getInstallationDate() : existingReport.getInstallationDate());
        existingReport.setInitialCost(report.getInitialCost() != null ? report.getInitialCost() : existingReport.getInitialCost());
        existingReport.setMonthlyDepreciationAmount(report.getMonthlyDepreciationAmount() != null ? report.getMonthlyDepreciationAmount() : existingReport.getMonthlyDepreciationAmount());
        existingReport.setAccumulatedDepreciation(report.getAccumulatedDepreciation() != null ? report.getAccumulatedDepreciation() : existingReport.getAccumulatedDepreciation());
        existingReport.setNetCost(report.getNetCost() != null ? report.getNetCost() : existingReport.getNetCost());
        existingReport.setSalvageValue(report.getSalvageValue() != null ? report.getSalvageValue() : existingReport.getSalvageValue());
        existingReport.setPoNumber(report.getPoNumber() != null ? report.getPoNumber() : existingReport.getPoNumber());
        existingReport.setPoDate(report.getPoDate() != null ? report.getPoDate() : existingReport.getPoDate());
        existingReport.setFaCategory(report.getFaCategory() != null ? report.getFaCategory() : existingReport.getFaCategory());
        existingReport.setL1(report.getL1() != null ? report.getL1() : existingReport.getL1());
        existingReport.setL2(report.getL2() != null ? report.getL2() : existingReport.getL2());
        existingReport.setL3(report.getL3() != null ? report.getL3() : existingReport.getL3());
        existingReport.setL4(report.getL4() != null ? report.getL4() : existingReport.getL4());
        existingReport.setAccumulatedDepreciationCode(report.getAccumulatedDepreciationCode() != null ? report.getAccumulatedDepreciationCode() : existingReport.getAccumulatedDepreciationCode());
        existingReport.setDepreciationCode(report.getDepreciationCode() != null ? report.getDepreciationCode() : existingReport.getDepreciationCode());
        existingReport.setUsefulLifeMonths(report.getUsefulLifeMonths() != null ? report.getUsefulLifeMonths() : existingReport.getUsefulLifeMonths());
        existingReport.setVendorName(report.getVendorName() != null ? report.getVendorName() : existingReport.getVendorName());
        existingReport.setVendorNumber(report.getVendorNumber() != null ? report.getVendorNumber() : existingReport.getVendorNumber());
        existingReport.setProjectNumber(report.getProjectNumber() != null ? report.getProjectNumber() : existingReport.getProjectNumber());
        existingReport.setDescription(report.getDescription() != null ? report.getDescription() : existingReport.getDescription());
        existingReport.setOracleAssetId(report.getOracleAssetId() != null ? report.getOracleAssetId() : existingReport.getOracleAssetId());
        existingReport.setDateOfService(report.getDateOfService() != null ? report.getDateOfService() : existingReport.getDateOfService());
        existingReport.setTechnologySupported(report.getTechnologySupported() != null ? report.getTechnologySupported() : existingReport.getTechnologySupported());
        existingReport.setOldFarCategory(report.getOldFarCategory() != null ? report.getOldFarCategory() : existingReport.getOldFarCategory());
        existingReport.setCostCenterData(report.getCostCenterData() != null ? report.getCostCenterData() : existingReport.getCostCenterData());
        existingReport.setNepAssetId(report.getNepAssetId() != null ? report.getNepAssetId() : existingReport.getNepAssetId());
        existingReport.setDeleted(report.getDeleted() != null ? report.getDeleted() : existingReport.getDeleted());
        existingReport.setAdjustment(report.getAdjustment() != null ? report.getAdjustment() : existingReport.getAdjustment());
        existingReport.setWriteOffDate(report.getWriteOffDate() != null ? report.getWriteOffDate() : existingReport.getWriteOffDate());
        existingReport.setTag(report.getTag() != null ? report.getTag() : existingReport.getTag());
        existingReport.setHostSerialNumber(report.getHostSerialNumber() != null ? report.getHostSerialNumber() : existingReport.getHostSerialNumber());
        existingReport.setTaskId(report.getTaskId() != null ? report.getTaskId() : existingReport.getTaskId());
        existingReport.setPoLineNumber(report.getPoLineNumber() != null ? report.getPoLineNumber() : existingReport.getPoLineNumber());
        existingReport.setReleaseNumber(report.getReleaseNumber() != null ? report.getReleaseNumber() : existingReport.getReleaseNumber());
        existingReport.setSpectrumLicenseDate(report.getSpectrumLicenseDate() != null ? report.getSpectrumLicenseDate() : existingReport.getSpectrumLicenseDate());
        existingReport.setItemBarCode(report.getItemBarCode() != null ? report.getItemBarCode() : existingReport.getItemBarCode());
        existingReport.setRfid(report.getRfid() != null ? report.getRfid() : existingReport.getRfid());
        existingReport.setInvoiceNumber(report.getInvoiceNumber() != null ? report.getInvoiceNumber() : existingReport.getInvoiceNumber());
    }

    /**
     * Processes a single financial report record.
     * @param report The financial report to process.
     * @param recordNumber The record number for logging and warnings.
     * @param username The username of the user performing the upload.
     * @param counters Map to track processed, created, updated, and skipped counts.
     * @param workflowIds List to store workflow IDs.
     * @param warnings List to store any warnings.
     * @return True if the record was processed successfully, false if skipped.
     */
    private boolean processRecord(tb_FinancialReport report, int recordNumber, String username,
                                  Map<String, Integer> counters, List<Integer> workflowIds, List<String> warnings) {
        String identifier = report.getAssetSerialNumber() != null && !report.getAssetSerialNumber().isEmpty() ?
                report.getAssetSerialNumber() : report.getAssetName();
        logger.info("Processing record {} with identifier: {}", recordNumber, identifier);

        // Check for pending workflows
        if (hasPendingWorkflows(identifier, recordNumber, warnings)) {
            logger.info("Skipping record {} for identifier {} due to pending workflow", recordNumber, identifier);
            warnings.add("Record " + recordNumber + ": Identifier " + identifier +
                    " has an existing workflow pending approval process and will be skipped.");
            counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
            return false;
        }

        // Check if record exists in Financial Report
        Optional<tb_FinancialReport> existingReportOpt;
        try {
            existingReportOpt = financialReportService.findBySerialNumber(identifier)
                    .or(() -> financialReportService.findByAssetName(identifier));
        } catch (Exception e) {
            logger.error("Error querying financial report for identifier: {}", identifier, e);
            warnings.add("Record " + recordNumber + ": Unable to check financial report for identifier " + identifier + " due to server error");
            counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
            return false;
        }

        // For existing records, check if assetName exists in NELicense
        if (existingReportOpt.isPresent()) {
            tb_FinancialReport existingReport = existingReportOpt.get();
            if (!isAssetInNELicense(existingReport, recordNumber, warnings)) {
                logger.info("Skipping record {} for identifier {}: assetName {} not found in NELicense",
                        recordNumber, identifier, existingReport.getAssetName());
                warnings.add("Record " + recordNumber + ": AssetName " + existingReport.getAssetName() +
                        " not found in NELicense. Record cannot be modified.");
                counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
                return false;
            }

            // Check if no changes
            if (!hasChanges(existingReport, report)) {
                warnings.add("Record " + recordNumber + ": Asset " + identifier +
                        " exists in Financial Report with no changes detected.");
                counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
                return false;
            }

            // Check if initial cost is null or zero (deletion)
            boolean isDeletion = report.getInitialCost() == null ||
                    report.getInitialCost().compareTo(BigDecimal.ZERO) == 0;

            if (isDeletion) {
                // Handle deletion
                existingReport.setInitialCost(BigDecimal.ZERO);
                existingReport.setChangedBy(username);
                existingReport.setChangeDate(new Date());
                try {
                    tb_FinancialReport savedReport = financialReportService.save(existingReport);
                    tb_ApprovalWorkflow workflow = approvalWorkflowService.createDeletionWorkflow(
                            savedReport, savedReport.getNodeType(), "pending deletion", username);
                    if (savedReport == null || workflow == null || workflow.getID() == null) {
                        logger.error("Failed to process deletion for identifier: {}", identifier);
                        warnings.add("Record " + recordNumber + ": Failed to process deletion for identifier " + identifier);
                        counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
                        return false;
                    }
                    workflowIds.add(workflow.getID());
                    counters.put("recordsUpdated", counters.get("recordsUpdated") + 1);
                    return true;
                } catch (Exception e) {
                    logger.error("Error processing deletion for identifier: {}", identifier, e);
                    warnings.add("Record " + recordNumber + ": Failed to process deletion for identifier " + identifier);
                    counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
                    return false;
                }
            } else {
                // Handle modification
                String originalState = serializeOriginalState(existingReport, identifier, recordNumber, warnings);
                if (originalState == null) {
                    counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
                    return false;
                }
                existingReport.setOriginalState(originalState);
                updateExistingReport(existingReport, report);
                existingReport.setFinancialApprovalStatus("Pending L1 Approval");
                existingReport.setChangedBy(username);
                existingReport.setChangeDate(new Date());
                try {
                    tb_FinancialReport savedReport = financialReportService.save(existingReport);
                    tb_ApprovalWorkflow workflow = approvalWorkflowService.createApprovalWorkflow(
                            savedReport, savedReport.getNodeType(), "pending modification", username);
                    if (savedReport == null || workflow == null || workflow.getID() == null) {
                        logger.error("Failed to process modification for identifier: {}", identifier);
                        warnings.add("Record " + recordNumber + ": Failed to process modification for identifier " + identifier);
                        counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
                        return false;
                    }
                    workflowIds.add(workflow.getID());
                    counters.put("recordsUpdated", counters.get("recordsUpdated") + 1);
                    return true;
                } catch (Exception e) {
                    logger.error("Error processing modification for identifier: {}", identifier, e);
                    warnings.add("Record " + recordNumber + ": Failed to process modification for identifier " + identifier);
                    counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
                    return false;
                }
            }
        } else {
            // Check unmapped inventory for new records
            if (!isInUnmappedInventory(identifier, recordNumber, warnings)) {
                warnings.add("Record " + recordNumber + ": Identifier " + identifier +
                        " not found in unmapped inventory. Record cannot be accepted.");
                counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
                return false;
            }

            // New asset
            report.setInsertedBy(username);
            report.setInsertDate(new Date());
            report.setStatusFlag("NEW");
            report.setFinancialApprovalStatus("Pending L1 Approval");
            try {
                tb_FinancialReport savedReport = financialReportService.save(report);
                tb_ApprovalWorkflow workflow = approvalWorkflowService.createApprovalWorkflow(
                        savedReport, savedReport.getNodeType(), "pending addition", username);
                if (savedReport == null || workflow == null || workflow.getID() == null) {
                    logger.error("Failed to process new record for identifier: {}", identifier);
                    warnings.add("Record " + recordNumber + ": Failed to process new record for identifier " + identifier);
                    counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
                    return false;
                }
                workflowIds.add(workflow.getID());
                counters.put("recordsCreated", counters.get("recordsCreated") + 1);
                return true;
            } catch (Exception e) {
                logger.error("Error processing new record for identifier: {}", identifier, e);
                warnings.add("Record " + recordNumber + ": Failed to process new record for identifier " + identifier);
                counters.put("recordsSkipped", counters.get("recordsSkipped") + 1);
                return false;
            }
        }
    }

    @Transactional
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFinancialReports(
            @RequestBody List<tb_FinancialReport> reports,
            Principal principal) {
        Map<String, Object> response = new HashMap<>();
        if (reports == null || reports.isEmpty()) {
            response.put("message", "No data provided for upload!");
            response.put("status", "error");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        try {
            String username = principal != null ? principal.getName() : "system";
            Set<String> identifiersInRequest = new HashSet<>();
            List<Integer> workflowIds = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            Map<String, Integer> counters = new HashMap<>();
            counters.put("recordsProcessed", 0);
            counters.put("recordsCreated", 0);
            counters.put("recordsUpdated", 0);
            counters.put("recordsSkipped", 0);

            // Validate and sync inventory data for all records
            for (int i = 0; i < reports.size(); i++) {
                tb_FinancialReport report = reports.get(i);
                int recordNumber = i + 1;

                // Validate identifier
                String identifierError = validateIdentifier(report, recordNumber, identifiersInRequest);
                if (identifierError != null) {
                    response.put("message", identifierError);
                    response.put("status", "error");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                // Validate mandatory fields
                String mandatoryFieldError = validateMandatoryFields(report, recordNumber);
                if (mandatoryFieldError != null) {
                    response.put("message", mandatoryFieldError);
                    response.put("status", "error");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                // Sync inventory data (optional)
                reports.set(i, syncInventoryData(report, recordNumber, warnings));
            }

            // Process records
            for (int i = 0; i < reports.size(); i++) {
                counters.put("recordsProcessed", counters.get("recordsProcessed") + 1);
                processRecord(reports.get(i), i + 1, username, counters, workflowIds, warnings);
            }

            // Prepare response
            response.put("recordsProcessed", counters.get("recordsProcessed"));
            response.put("recordsCreated", counters.get("recordsCreated"));
            response.put("recordsUpdated", counters.get("recordsUpdated"));
            response.put("recordsSkipped", counters.get("recordsSkipped"));
            response.put("workflowIds", workflowIds);
            response.put("message", (counters.get("recordsCreated") + counters.get("recordsUpdated") > 0) ?
                    "Successfully uploaded financial reports" : "No records processed");
            response.put("status", (counters.get("recordsCreated") + counters.get("recordsUpdated") > 0) ?
                    "success" : "success_with_warnings");

            if (!warnings.isEmpty()) {
                response.put("warnings", warnings);
                response.put("status", "success_with_warnings");
            }

            logger.info("Upload completed: processed={}, created={}, updated={}, skipped={}",
                    counters.get("recordsProcessed"), counters.get("recordsCreated"),
                    counters.get("recordsUpdated"), counters.get("recordsSkipped"));
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Unexpected error processing uploaded financial reports", e);
            response.put("message", "Upload failed due to an unexpected error: " + e.getMessage());
            response.put("status", "error");
            response.put("errorDetails", e.getClass().getName() + ": " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Helper method to compare fields between existing and incoming reports
    private boolean hasChanges(tb_FinancialReport existing, tb_FinancialReport incoming) {
        return !(Objects.equals(existing.getNodeType(), incoming.getNodeType()) &&
                Objects.equals(existing.getAssetName(), incoming.getAssetName()) &&
                Objects.equals(existing.getAssetSerialNumber(), incoming.getAssetSerialNumber()) &&
                Objects.equals(existing.getAssetType(), incoming.getAssetType()) &&
                Objects.equals(existing.getAssetCategory(), incoming.getAssetCategory()) &&
                Objects.equals(existing.getModel(), incoming.getModel()) &&
                Objects.equals(existing.getPartNumber(), incoming.getPartNumber()) &&
                Objects.equals(existing.getInstallationDate(), incoming.getInstallationDate()) &&
                Objects.equals(existing.getInitialCost(), incoming.getInitialCost()) &&
                Objects.equals(existing.getSalvageValue(), incoming.getSalvageValue()) &&
                Objects.equals(existing.getPoNumber(), incoming.getPoNumber()) &&
                Objects.equals(existing.getPoDate(), incoming.getPoDate()) &&
                Objects.equals(existing.getFaCategory(), incoming.getFaCategory()) &&
                Objects.equals(existing.getL1(), incoming.getL1()) &&
                Objects.equals(existing.getL2(), incoming.getL2()) &&
                Objects.equals(existing.getL3(), incoming.getL3()) &&
                Objects.equals(existing.getL4(), incoming.getL4()) &&
                Objects.equals(existing.getAccumulatedDepreciationCode(), incoming.getAccumulatedDepreciationCode()) &&
                Objects.equals(existing.getDepreciationCode(), incoming.getDepreciationCode()) &&
                Objects.equals(existing.getUsefulLifeMonths(), incoming.getUsefulLifeMonths()) &&
                Objects.equals(existing.getVendorName(), incoming.getVendorName()) &&
                Objects.equals(existing.getVendorNumber(), incoming.getVendorNumber()) &&
                Objects.equals(existing.getProjectNumber(), incoming.getProjectNumber()) &&
                Objects.equals(existing.getDateOfService(), incoming.getDateOfService()) &&
                Objects.equals(existing.getOldFarCategory(), incoming.getOldFarCategory()) &&
                Objects.equals(existing.getCostCenterData(), incoming.getCostCenterData()) &&
                Objects.equals(existing.getAdjustment(), incoming.getAdjustment()) &&
                Objects.equals(existing.getTaskId(), incoming.getTaskId()) &&
                Objects.equals(existing.getPoLineNumber(), incoming.getPoLineNumber()) &&
                Objects.equals(existing.getMonthlyDepreciationAmount(), incoming.getMonthlyDepreciationAmount()) &&
                Objects.equals(existing.getAccumulatedDepreciation(), incoming.getAccumulatedDepreciation()) &&
                Objects.equals(existing.getNetCost(), incoming.getNetCost()) &&
                Objects.equals(existing.getDescription(), incoming.getDescription()) &&
                Objects.equals(existing.getOracleAssetId(), incoming.getOracleAssetId()) &&
                Objects.equals(existing.getTechnologySupported(), incoming.getTechnologySupported()) &&
                Objects.equals(existing.getNepAssetId(), incoming.getNepAssetId()) &&
                Objects.equals(existing.getDeleted(), incoming.getDeleted()) &&
                Objects.equals(existing.getWriteOffDate(), incoming.getWriteOffDate()) &&
                Objects.equals(existing.getTag(), incoming.getTag()) &&
                Objects.equals(existing.getHostSerialNumber(), incoming.getHostSerialNumber()) &&
                Objects.equals(existing.getReleaseNumber(), incoming.getReleaseNumber()) &&
                Objects.equals(existing.getSpectrumLicenseDate(), incoming.getSpectrumLicenseDate()) &&
                Objects.equals(existing.getItemBarCode(), incoming.getItemBarCode()) &&
                Objects.equals(existing.getRfid(), incoming.getRfid()) &&
                Objects.equals(existing.getInvoiceNumber(), incoming.getInvoiceNumber()));
    }

}