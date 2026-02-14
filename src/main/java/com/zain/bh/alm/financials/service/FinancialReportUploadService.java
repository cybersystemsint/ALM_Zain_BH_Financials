package com.zain.bh.alm.financials.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zain.bh.alm.financials.dto.ApprovalWorkflowDTO;
import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.repository.FinancialReportRepository;
import com.zain.bh.alm.financials.repository.UnmappedActiveInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedITInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedPassiveInventoryRepository;

@Service
public class FinancialReportUploadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialReportUploadService.class);
    private static final int BATCH_SIZE = 50;

    private final FinancialReportService financialReportService;
    private final FinancialReportRepository financialReportRepository;
    private final ApprovalWorkflowService approvalWorkflowService;
    private final UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;
    private final UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;
    private final UnmappedITInventoryRepository unmappedITInventoryRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final FinancialReportValidationService validationService;
    private final FinancialReportStateService stateService;

    public FinancialReportUploadService(FinancialReportService financialReportService,
            FinancialReportRepository financialReportRepository, ApprovalWorkflowService approvalWorkflowService,
            UnmappedActiveInventoryRepository unmappedActiveInventoryRepository,
            UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository,
            UnmappedITInventoryRepository unmappedITInventoryRepository, ObjectMapper objectMapper,
            EntityManager entityManager, FinancialReportValidationService validationService,
            FinancialReportStateService stateService) {
        this.financialReportService = financialReportService;
        this.financialReportRepository = financialReportRepository;
        this.approvalWorkflowService = approvalWorkflowService;
        this.unmappedActiveInventoryRepository = unmappedActiveInventoryRepository;
        this.unmappedPassiveInventoryRepository = unmappedPassiveInventoryRepository;
        this.unmappedITInventoryRepository = unmappedITInventoryRepository;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.validationService = validationService;
        this.stateService = stateService;
    }

    public Map<String, Object> uploadReports(List<FinancialReport> reports, String username) {
        Set<String> identifiersInRequest = new HashSet<>();
        List<Integer> workflowIds = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int recordsProcessed = 0;
        int recordsAccepted = 0;
        int recordsSkipped = 0;

        for (int i = 0; i < reports.size(); i++) {
            FinancialReport report = reports.get(i);
            int recordNumber = i + 1;
            String identifier = getIdentifier(report);

            String validationError = validationService.validateUploadRecord(report, identifier, recordNumber, identifiersInRequest);
            if (validationError != null) {
                throw new IllegalArgumentException(validationError);
            }
            identifiersInRequest.add(identifier);
        }

        for (FinancialReport report : reports) {
            recordsProcessed++;
            String identifier = getIdentifier(report);

            boolean inUnmapped = isInUnmappedInventory(identifier);
            Optional<FinancialReport> existingReportOpt = findExistingReport(identifier);
            List<ApprovalWorkflowDTO> existingWorkflows = approvalWorkflowService.findByAssetId(identifier);
            boolean hasPendingWorkflow = existingWorkflows.stream().anyMatch(w -> w.getUpdatedStatus().startsWith("Pending"));

            if (!inUnmapped) {
                warnings.add("Record " + recordsProcessed + ": Identifier " + identifier + " not found in unmapped inventory. Record cannot be accepted.");
                recordsSkipped++;
                continue;
            }

            if (existingReportOpt.isPresent() && hasPendingWorkflow) {
                warnings.add("Record " + recordsProcessed + ": Identifier " + identifier + " exists in Financial Report and has pending workflow approvals.");
                recordsSkipped++;
                continue;
            }

            try {
                ProcessingResult result = processReport(report, existingReportOpt, identifier, username, recordsProcessed);
                if (result.isSkipped()) {
                    warnings.add(result.getMessage());
                    recordsSkipped++;
                } else {
                    workflowIds.add(result.getWorkflowId());
                    recordsAccepted++;

                    if (recordsAccepted % BATCH_SIZE == 0) {
                        entityManager.flush();
                        entityManager.clear();
                        LOGGER.info("Processed batch of {} records, total accepted: {}", BATCH_SIZE, recordsAccepted);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process record {} for identifier: {}", recordsProcessed, identifier, e);
                throw new RuntimeException("Failed to process record for identifier: " + identifier);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("recordsProcessed", recordsProcessed);
        response.put("recordsAccepted", recordsAccepted);
        response.put("recordsSkipped", recordsSkipped);
        response.put("workflowIds", workflowIds);
        response.put("message", recordsAccepted > 0 ? "Successfully uploaded" : "No records processed");
        response.put("status", recordsAccepted > 0 ? "success" : "success_with_warnings");

        if (!warnings.isEmpty()) {
            response.put("warnings", warnings);
            response.put("status", "success_with_warnings");
        }

        return response;
    }

    private ProcessingResult processReport(FinancialReport report, Optional<FinancialReport> existingReportOpt, String identifier, String username, int recordNumber) {
        if (existingReportOpt.isPresent()) {
            FinancialReport existingReport = existingReportOpt.get();

            if (!validationService.hasChanges(existingReport, report)) {
                return ProcessingResult.skipped("Record " + recordNumber + ": Asset " + identifier + " exists in Financial Report with no changes detected.");
            }

            boolean isDeletion = report.getInitialCost() == null || report.getInitialCost().compareTo(BigDecimal.ZERO) == 0;

            if (isDeletion) {
                return handleDeletion(existingReport, username);
            } else {
                return handleModification(existingReport, report, username);
            }
        } else {
            return handleNewAsset(report, username);
        }
    }

    private ProcessingResult handleDeletion(FinancialReport existingReport, String username) {
        existingReport.setInitialCost(BigDecimal.ZERO);
        existingReport.setChangedBy(username);
        existingReport.setChangeDate(new Date());
        FinancialReport savedReport = financialReportService.save(existingReport);
        ApprovalWorkflowDTO workflow = approvalWorkflowService.createDeletionWorkflow(savedReport, savedReport.getNodeType(), "pending deletion");
        return ProcessingResult.success(workflow.getId());
    }

    private ProcessingResult handleModification(FinancialReport existingReport, FinancialReport report, String username) {
        try {
            stateService.saveOriginalState(existingReport, objectMapper);
            stateService.updateReportFields(existingReport, report);
            existingReport.setFinancialApprovalStatus("Pending");
            existingReport.setChangedBy(username);
            existingReport.setChangeDate(new Date());
            FinancialReport savedReport = financialReportRepository.save(existingReport);
            ApprovalWorkflowDTO workflow = approvalWorkflowService.createApprovalWorkflow(savedReport, savedReport.getNodeType(), "pending modification");
            return ProcessingResult.success(workflow.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to serialize original state", e);
            throw new RuntimeException("Failed to serialize original state: " + e.getMessage());
        }
    }

    private ProcessingResult handleNewAsset(FinancialReport report, String username) {
        report.setInsertedBy(username);
        report.setInsertDate(new Date());
        report.setStatusFlag("NEW");
        report.setFinancialApprovalStatus("Pending L1 Approval");
        FinancialReport savedReport = financialReportService.save(report);
        ApprovalWorkflowDTO workflow = approvalWorkflowService.createApprovalWorkflow(savedReport, savedReport.getNodeType(), "pending addition");
        return ProcessingResult.success(workflow.getId());
    }

    private String getIdentifier(FinancialReport report) {
        return report.getAssetSerialNumber() != null && !report.getAssetSerialNumber().isEmpty() ? report.getAssetSerialNumber() : report.getAssetName();
    }

    private boolean isInUnmappedInventory(String identifier) {
        return unmappedActiveInventoryRepository.findBySerialNumber(identifier).isPresent()
                || unmappedPassiveInventoryRepository.findBySerial(identifier).isPresent()
                || unmappedPassiveInventoryRepository.findByObjectId(identifier).isPresent()
                || unmappedPassiveInventoryRepository.findByElementType(identifier).isPresent()
                || unmappedITInventoryRepository.findByHostSerialNumber(identifier).isPresent()
                || unmappedITInventoryRepository.findByHardwareSerialNumber(identifier).isPresent()
                || unmappedITInventoryRepository.findByElementId(identifier).isPresent()
                || unmappedITInventoryRepository.findByHostName(identifier).isPresent();
    }

    private Optional<FinancialReport> findExistingReport(String identifier) {
        Optional<FinancialReport> reportOpt = financialReportService.findBySerialNumber(identifier);
        if (!reportOpt.isPresent()) {
            reportOpt = financialReportService.findByAssetName(identifier);
        }
        return reportOpt;
    }

    private static class ProcessingResult {
        private final boolean skipped;
        private final String message;
        private final Integer workflowId;

        private ProcessingResult(boolean skipped, String message, Integer workflowId) {
            this.skipped = skipped;
            this.message = message;
            this.workflowId = workflowId;
        }

        static ProcessingResult skipped(String message) {
            return new ProcessingResult(true, message, null);
        }

        static ProcessingResult success(Integer workflowId) {
            return new ProcessingResult(false, null, workflowId);
        }

        boolean isSkipped() {
            return skipped;
        }

        String getMessage() {
            return message;
        }

        Integer getWorkflowId() {
            return workflowId;
        }
    }
}
