package com.zain.bh.alm.financials.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zain.bh.alm.financials.dto.ApprovalWorkflowDTO;
import com.zain.bh.alm.financials.entity.AuditLog;
import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.entity.WFFinancialApprovalRequest;
import com.zain.bh.alm.financials.repository.AuditLogRepository;
import com.zain.bh.alm.financials.repository.FinancialReportRepository;
import com.zain.bh.alm.financials.repository.UnmappedActiveInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedITInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedPassiveInventoryRepository;
import com.zain.bh.alm.financials.repository.WFFinancialApprovalRequestRepository;

@Service
public class ApprovalWorkflowService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalWorkflowService.class);
    private static final int MAX_ATTEMPTS = 10;
    private static final int ID_RANGE_MIN = 1;
    private static final int ID_RANGE_MAX = 999999999;
    private static final SecureRandom random = new SecureRandom();

    private final WFFinancialApprovalRequestRepository approvalWorkflowRepository;
    private final FinancialReportRepository financialReportRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;
    private final UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;
    private final UnmappedITInventoryRepository unmappedITInventoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ApprovalWorkflowService(WFFinancialApprovalRequestRepository approvalWorkflowRepository,
                                   FinancialReportRepository financialReportRepository,
                                   AuditLogRepository auditLogRepository,
                                   ObjectMapper objectMapper,
                                   UnmappedActiveInventoryRepository unmappedActiveInventoryRepository,
                                   UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository,
                                   UnmappedITInventoryRepository unmappedITInventoryRepository) {
        this.approvalWorkflowRepository = approvalWorkflowRepository;
        this.financialReportRepository = financialReportRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.unmappedActiveInventoryRepository = unmappedActiveInventoryRepository;
        this.unmappedPassiveInventoryRepository = unmappedPassiveInventoryRepository;
        this.unmappedITInventoryRepository = unmappedITInventoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ApprovalWorkflowDTO> getPendingApprovals() {
        return findByStatus("Pending L1 Approval");
    }

    @Transactional
    public ApprovalWorkflowDTO createApprovalWorkflow(FinancialReport financialReport, String nodeType, String originalStatus) {
        LOGGER.info("Creating approval workflow for financial report ID: {}", financialReport.getId());

        String assetId = financialReport.getAssetName() != null && !financialReport.getAssetName().trim().isEmpty() ?
                financialReport.getAssetName() : financialReport.getAssetSerialNumber();
        if (assetId == null || assetId.trim().isEmpty()) {
            LOGGER.error("Neither assetName nor assetSerialNumber provided for financial report ID: {}", financialReport.getId());
            throw new IllegalArgumentException("assetName or assetSerialNumber must be provided");
        }

        List<ApprovalWorkflowDTO> existingWorkflows = findByAssetId(assetId);
        if (existingWorkflows.stream().anyMatch(w -> w.getUpdatedStatus().startsWith("Pending"))) {
            LOGGER.warn("Pending workflow already exists for asset ID: {}", assetId);
            throw new IllegalStateException("Asset is in approval workflow pending approvals.");
        }

        WFFinancialApprovalRequest workflow = new WFFinancialApprovalRequest();
        workflow.setAssetId(assetId);
        workflow.setObjectType(nodeType != null ? nodeType : "default");

        String workflowStatus = originalStatus != null ? originalStatus :
                determineOriginalStatus(financialReport, financialReport.getId() == null || !financialReportRepository.existsById(financialReport.getId()));
        workflow.setOriginalStatus(workflowStatus);
        workflow.setUpdatedStatus("Pending L1 Approval");
        workflow.setProcessId(generateUniqueProcessId());
        workflow.setComments("Financial report " + workflowStatus.toLowerCase() + " pending L1 approval");
        workflow.setInsertedBy(financialReport.getInsertedBy() != null ? financialReport.getInsertedBy() : "system");
        workflow.setInsertDate(LocalDateTime.now());

        WFFinancialApprovalRequest savedWorkflow = approvalWorkflowRepository.save(workflow);
        ApprovalWorkflowDTO dto = convertToDto(savedWorkflow);

        createAuditLog(
                assetId,
                financialReport.getAssetSerialNumber(),
                nodeType,
                workflowStatus,
                "Pending L1 Approval",
                "Approval workflow created for " + workflowStatus.toLowerCase()
        );

        sendApprovalNotification(dto, "REQUEST", financialReport.getAssetSerialNumber(), nodeType);
        return dto;
    }

    @Transactional
    public ApprovalWorkflowDTO createDeletionWorkflow(FinancialReport financialReport, String nodeType, String originalStatus) {
        LOGGER.info("Creating deletion workflow for financial report ID: {}", financialReport.getId());

        String assetId = financialReport.getAssetName() != null && !financialReport.getAssetName().trim().isEmpty() ?
                financialReport.getAssetName() : financialReport.getAssetSerialNumber();
        if (assetId == null || assetId.trim().isEmpty()) {
            LOGGER.error("Neither assetName nor assetSerialNumber provided for financial report ID: {}", financialReport.getId());
            throw new IllegalArgumentException("assetName or assetSerialNumber must be provided");
        }

        List<ApprovalWorkflowDTO> existingWorkflows = findByAssetId(assetId);
        Optional<ApprovalWorkflowDTO> existingPendingWorkflow = existingWorkflows.stream()
                .filter(w -> "Pending L1 Approval".equals(w.getUpdatedStatus()))
                .max(Comparator.comparing(w -> w.getInsertDate() != null ?
                        w.getInsertDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() :
                        LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)));
        if (existingPendingWorkflow.isPresent()) {
            LOGGER.info("Found existing deletion workflow for asset ID: {}. Returning workflow ID: {}", assetId, existingPendingWorkflow.get().getId());
            return existingPendingWorkflow.get();
        }

        WFFinancialApprovalRequest workflow = new WFFinancialApprovalRequest();
        workflow.setAssetId(assetId);
        workflow.setObjectType(nodeType != null ? nodeType : "default");
        workflow.setOriginalStatus("pending deletion");
        workflow.setUpdatedStatus("Pending L1 Approval");
        workflow.setProcessId(generateUniqueProcessId());
        workflow.setComments("Financial report marked for deletion - pending L1 approval");
        workflow.setInsertedBy(financialReport.getChangedBy() != null ? financialReport.getChangedBy() : "system");
        workflow.setInsertDate(LocalDateTime.now());

        financialReport.setStatusFlag("DECOMMISSIONED");
        financialReport.setFinancialApprovalStatus("Pending L1 Approval");
        financialReportRepository.save(financialReport);

        WFFinancialApprovalRequest savedWorkflow = approvalWorkflowRepository.save(workflow);
        ApprovalWorkflowDTO dto = convertToDto(savedWorkflow);

        createAuditLog(
                assetId,
                financialReport.getAssetSerialNumber(),
                nodeType,
                "pending deletion",
                "Pending L1 Approval",
                "Deletion workflow created for financial report"
        );

        sendApprovalNotification(dto, "DELETE_REQUEST", financialReport.getAssetSerialNumber(), nodeType);
        return dto;
    }

    @Transactional
    public Integer generateUniqueProcessId() {
        int attempts = 0;
        while (attempts < MAX_ATTEMPTS) {
            int processId = random.nextInt(ID_RANGE_MAX - ID_RANGE_MIN + 1) + ID_RANGE_MIN;
            LOGGER.debug("Generated candidate PROCESS_ID: {}", processId);

            boolean exists = approvalWorkflowRepository.existsByProcessId(processId);
            if (!exists) {
                LOGGER.debug("Unique PROCESS_ID confirmed: {}", processId);
                return processId;
            }

            LOGGER.warn("PROCESS_ID {} already exists, retrying (attempt {}/{})", processId, attempts + 1, MAX_ATTEMPTS);
            attempts++;
        }

        LOGGER.error("Failed to generate unique PROCESS_ID after {} attempts", MAX_ATTEMPTS);
        throw new RuntimeException("Unable to generate a unique PROCESS_ID after " + MAX_ATTEMPTS + " attempts");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void deleteFromUnmappedInventory(String identifier) {
        try {
            // Delete from active inventory
            if (unmappedActiveInventoryRepository.findBySerialNumber(identifier).isPresent()) {
                unmappedActiveInventoryRepository.deleteBySerialNumber(identifier);
                LOGGER.info("Deleted unmapped active inventory for identifier: {}", identifier);
            }
            // Delete from passive inventory
            else if (unmappedPassiveInventoryRepository.findBySerial(identifier).isPresent()) {
                unmappedPassiveInventoryRepository.deleteBySerial(identifier);
                LOGGER.info("Deleted unmapped passive inventory by serial for identifier: {}", identifier);
            }
            else if (unmappedPassiveInventoryRepository.findByObjectId(identifier).isPresent()) {
                unmappedPassiveInventoryRepository.deleteByObjectId(identifier);
                LOGGER.info("Deleted unmapped passive inventory by objectId for identifier: {}", identifier);
            }
            else if (unmappedPassiveInventoryRepository.findByElementType(identifier).isPresent()) {
                unmappedPassiveInventoryRepository.deleteByElementType(identifier);
                LOGGER.info("Deleted unmapped passive inventory by elementType for identifier: {}", identifier);
            }
            // Delete from IT inventory
            else if (unmappedITInventoryRepository.findByHostSerialNumber(identifier).isPresent()) {
                unmappedITInventoryRepository.deleteByHostSerialNumber(identifier);
                LOGGER.info("Deleted unmapped IT inventory by hostSerialNumber for identifier: {}", identifier);
            }
            else if (unmappedITInventoryRepository.findByHardwareSerialNumber(identifier).isPresent()) {
                unmappedITInventoryRepository.deleteByHardwareSerialNumber(identifier);
                LOGGER.info("Deleted unmapped IT inventory by hardwareSerialNumber for identifier: {}", identifier);
            }
            else if (unmappedITInventoryRepository.findByElementId(identifier).isPresent()) {
                unmappedITInventoryRepository.deleteByElementId(identifier);
                LOGGER.info("Deleted unmapped IT inventory by elementId for identifier: {}", identifier);
            }
            else if (unmappedITInventoryRepository.findByHostName(identifier).isPresent()) {
                unmappedITInventoryRepository.deleteByHostName(identifier);
                LOGGER.info("Deleted unmapped IT inventory by hostName for identifier: {}", identifier);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to delete unmapped inventory for identifier: {}", identifier, e);
            throw new RuntimeException("Failed to delete unmapped inventory for identifier: " + identifier, e);
        }
    }


    @Transactional
    public boolean approveWorkflow(Integer workflowId, String approverComments, String approvedBy) {
        LOGGER.info("Approving workflow: {}", workflowId);

        Optional<WFFinancialApprovalRequest> workflowOpt = approvalWorkflowRepository.findById(workflowId);
        if (!workflowOpt.isPresent()) {
            LOGGER.warn("Workflow not found: {}", workflowId);
            return false;
        }

        WFFinancialApprovalRequest workflow = workflowOpt.get();
        String currentStatus = workflow.getUpdatedStatus();
        String originalStatus = workflow.getOriginalStatus();
        String nextStatus;

        switch (currentStatus) {
            case "Pending L1 Approval":
                nextStatus = "Pending L2 Approval";
                break;
            case "Pending L2 Approval":
                nextStatus = "Pending L3 Approval";
                break;
            case "Pending L3 Approval":
                nextStatus = "APPROVED";
                break;
            default:
                LOGGER.warn("Invalid status for approval: {}", currentStatus);
                return false;
        }

        Optional<FinancialReport> financialReportOpt = findFinancialReportByAssetId(workflow.getAssetId());
        if (!financialReportOpt.isPresent()) {
            LOGGER.error("Financial Report not found for ASSET_ID: {}", workflow.getAssetId());
            return false;
        }

        FinancialReport financialReport = financialReportOpt.get();
        String previousStatus = financialReport.getFinancialApprovalStatus();
        String previousStatusFlag = financialReport.getStatusFlag();
        String serialNumber = financialReport.getAssetSerialNumber();
        String nodeType = financialReport.getNodeType();

        String newStatusFlag = determineStatusFlag(financialReport, previousStatusFlag, nextStatus, originalStatus);

        if ("APPROVED".equals(nextStatus)) {
            if ("pending deletion".equals(originalStatus)) {
                LOGGER.info("Deleting financial report ID {} for {}", financialReport.getId(), originalStatus);
                financialReportRepository.delete(financialReport);
                createAuditLog(
                        workflow.getAssetId(),
                        serialNumber,
                        nodeType,
                        previousStatus,
                        "DELETED",
                        "Financial report deleted through approval workflow. Approver: " + approvedBy +
                                ", Comments: " + approverComments
                );
                sendApprovalNotification(convertToDto(workflow), "DELETED", serialNumber, nodeType);
            } else if ("pending movement".equals(originalStatus)) {
                LOGGER.info("Moving financial report ID {} to write-off", financialReport.getId());
                financialReport.setFinancialApprovalStatus("WriteOff");
                financialReport.setStatusFlag(newStatusFlag);
                financialReport.setChangeDate(java.sql.Timestamp.valueOf(LocalDateTime.now()));
                financialReport.setChangedBy(approvedBy);
                financialReportRepository.save(financialReport);
                createAuditLog(
                        workflow.getAssetId(),
                        serialNumber,
                        nodeType,
                        previousStatus,
                        "MOVED_TO_WRITEOFF",
                        "Financial report moved to write-off through approval workflow. Approver: " + approvedBy +
                                ", Comments: " + approverComments
                );
                sendApprovalNotification(convertToDto(workflow), "MOVED_TO_WRITEOFF", serialNumber, nodeType);
            } else if ("pending addition".equals(originalStatus) || "pending modification".equals(originalStatus)) {
                LOGGER.info("Applying L3 approval for financial report ID {}. Current values: initialCost={}, assetSerialNumber={}",
                        financialReport.getId(), financialReport.getInitialCost(), financialReport.getAssetSerialNumber());

                financialReport.setStatusFlag(newStatusFlag);
                financialReport.setFinancialApprovalStatus("Approved");
                financialReport.setOriginalState(null);
                financialReport.setChangeDate(java.sql.Timestamp.valueOf(LocalDateTime.now()));
                financialReport.setChangedBy(approvedBy);

                financialReportRepository.save(financialReport);

                String identifier = serialNumber != null && !serialNumber.trim().isEmpty() ? serialNumber : workflow.getAssetId();
                try {
                    deleteFromUnmappedInventory(identifier);
                } catch (Exception e) {
                    LOGGER.error("Failed to delete unmapped inventory for identifier: {}", identifier, e);
                }

                LOGGER.info("Saved financial report ID {}. New values: initialCost={}, assetSerialNumber={}",
                        financialReport.getId(), financialReport.getInitialCost(), financialReport.getAssetSerialNumber());

                createAuditLog(
                        workflow.getAssetId(),
                        serialNumber,
                        nodeType,
                        previousStatus,
                        "Approved",
                        "Financial report approved through workflow. Approver: " + approvedBy +
                                ", Comments: " + approverComments
                );
                sendApprovalNotification(convertToDto(workflow), "APPROVED", serialNumber, nodeType);
            } else {
                LOGGER.warn("Invalid original status for approval: {}", originalStatus);
                return false;
            }
            approvalWorkflowRepository.delete(workflow);
        } else {
            workflow.setUpdatedStatus(nextStatus);
            workflow.setChangedBy(approvedBy);
            workflow.setChangeDate(LocalDateTime.now());
            workflow.setComments((workflow.getComments() != null ? workflow.getComments() : "") +
                    "\nApprover Comments: " + approverComments);
            approvalWorkflowRepository.save(workflow);

            financialReport.setFinancialApprovalStatus(nextStatus);
            financialReport.setStatusFlag(newStatusFlag);
            financialReport.setChangeDate(java.sql.Timestamp.valueOf(LocalDateTime.now()));
            financialReport.setChangedBy(approvedBy);
            financialReportRepository.save(financialReport);

            createAuditLog(
                    workflow.getAssetId(),
                    serialNumber,
                    nodeType,
                    previousStatus,
                    nextStatus,
                    "Approval step completed: " + nextStatus + ". Approver: " + approvedBy +
                            ", Comments: " + approverComments
            );
            sendApprovalNotification(convertToDto(workflow), "APPROVAL_STEP", serialNumber, nodeType);
        }

        LOGGER.info("Workflow {} approved to status: {}", workflowId, nextStatus);
        return true;
    }



    @Transactional
    public boolean rejectWorkflow(Integer workflowId, String rejectionComments, String rejectedBy) {
        LOGGER.info("Rejecting workflow: {}", workflowId);

        Optional<WFFinancialApprovalRequest> workflowOpt = approvalWorkflowRepository.findById(workflowId);
        if (!workflowOpt.isPresent()) {
            LOGGER.warn("Workflow not found: {}", workflowId);
            return false;
        }

        WFFinancialApprovalRequest workflow = workflowOpt.get();
        Optional<FinancialReport> financialReportOpt = findFinancialReportByAssetId(workflow.getAssetId());
        if (!financialReportOpt.isPresent()) {
            LOGGER.error("Financial Report not found for ASSET_ID: {}", workflow.getAssetId());
            return false;
        }

        FinancialReport financialReport = financialReportOpt.get();
        String previousStatusFlag = financialReport.getStatusFlag();
        String serialNumber = financialReport.getAssetSerialNumber();
        String nodeType = financialReport.getNodeType();

        if (("pending modification".equals(workflow.getOriginalStatus()) || "pending movement".equals(workflow.getOriginalStatus()))
                && financialReport.getOriginalState() != null) {
            try {
                Map<String, Object> originalState = objectMapper.readValue(financialReport.getOriginalState(), Map.class);
                financialReport.setSiteId((String) originalState.get("siteId"));
                financialReport.setZone((String) originalState.get("zone"));
                financialReport.setNodeType((String) originalState.get("nodeType"));
                financialReport.setAssetName((String) originalState.get("assetName"));
                financialReport.setAssetType((String) originalState.get("assetType"));
                financialReport.setAssetCategory((String) originalState.get("assetCategory"));
                financialReport.setModel((String) originalState.get("model"));
                financialReport.setPartNumber((String) originalState.get("partNumber"));
                financialReport.setAssetSerialNumber((String) originalState.get("assetSerialNumber"));
                financialReport.setInstallationDate((String) originalState.get("installationDate"));
                financialReport.setInitialCost(originalState.get("initialCost") != null ? new BigDecimal(originalState.get("initialCost").toString()) : null);
                financialReport.setMonthlyDepreciationAmount(originalState.get("monthlyDepreciationAmount") != null ? new BigDecimal(originalState.get("monthlyDepreciationAmount").toString()) : null);
                financialReport.setAccumulatedDepreciation(originalState.get("accumulatedDepreciation") != null ? new BigDecimal(originalState.get("accumulatedDepreciation").toString()) : null);
                financialReport.setNetCost(originalState.get("netCost") != null ? new BigDecimal(originalState.get("netCost").toString()) : null);
                financialReport.setSalvageValue(originalState.get("salvageValue") != null ? new BigDecimal(originalState.get("salvageValue").toString()) : null);
                financialReport.setPoNumber((String) originalState.get("poNumber"));
                financialReport.setPoDate((String) originalState.get("poDate"));
                financialReport.setFaCategory((String) originalState.get("faCategory"));
                financialReport.setL1((String) originalState.get("l1"));
                financialReport.setL2((String) originalState.get("l2"));
                financialReport.setL3((String) originalState.get("l3"));
                financialReport.setL4((String) originalState.get("l4"));
                financialReport.setAccumulatedDepreciationCode((String) originalState.get("accumulatedDepreciationCode"));
                financialReport.setDepreciationCode((String) originalState.get("depreciationCode"));
                financialReport.setUsefulLifeMonths(originalState.get("usefulLifeMonths") != null ? ((Number) originalState.get("usefulLifeMonths")).intValue() : null);
                financialReport.setVendorName((String) originalState.get("vendorName"));
                financialReport.setVendorNumber((String) originalState.get("vendorNumber"));
                financialReport.setProjectNumber((String) originalState.get("projectNumber"));
                financialReport.setDescription((String) originalState.get("description"));
                financialReport.setOracleAssetId((String) originalState.get("oracleAssetId"));
                financialReport.setDateOfService((String) originalState.get("dateOfService"));
                financialReport.setTechnologySupported((String) originalState.get("technologySupported"));
                financialReport.setOldFarCategory((String) originalState.get("oldFarCategory"));
                financialReport.setCostCenterData((String) originalState.get("costCenterData"));
                financialReport.setNepAssetId((String) originalState.get("nepAssetId"));
                financialReport.setDeleted((Boolean) originalState.get("deleted"));
                financialReport.setAdjustment(originalState.get("adjustment") != null ? new BigDecimal(originalState.get("adjustment").toString()) : null);
                financialReport.setWriteOffDate(originalState.get("writeOffDate") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse((String) originalState.get("writeOffDate")) : null);
                financialReport.setTag((String) originalState.get("tag"));
                financialReport.setHostSerialNumber((String) originalState.get("hostSerialNumber"));
                financialReport.setTaskId((String) originalState.get("taskId"));
                financialReport.setPoLineNumber((String) originalState.get("poLineNumber"));
                financialReport.setReleaseNumber((String) originalState.get("releaseNumber"));
                financialReport.setSpectrumLicenseDate(originalState.get("spectrumLicenseDate") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse((String) originalState.get("spectrumLicenseDate")) : null);
                financialReport.setItemBarCode((String) originalState.get("itemBarCode"));
                financialReport.setRfid((String) originalState.get("rfid"));
                financialReport.setInvoiceNumber((String) originalState.get("invoiceNumber"));
            } catch (Exception e) {
                LOGGER.error("Failed to restore original state for report ID: {}", financialReport.getId(), e);
                return false;
            }
        }

        workflow.setUpdatedStatus("REJECTED");
        workflow.setChangedBy(rejectedBy);
        workflow.setChangeDate(LocalDateTime.now());
        workflow.setComments((workflow.getComments() != null ? workflow.getComments() : "") +
                "\nRejection Reason: " + rejectionComments);
        approvalWorkflowRepository.save(workflow);

        String newStatusFlag = determineStatusFlag(financialReport, previousStatusFlag, "REJECTED", workflow.getOriginalStatus());
        financialReport.setFinancialApprovalStatus("Rejected");
        financialReport.setStatusFlag(newStatusFlag);
        financialReport.setOriginalState(null);
        financialReport.setChangeDate(java.sql.Timestamp.valueOf(LocalDateTime.now()));
        financialReport.setChangedBy(rejectedBy);
        financialReportRepository.save(financialReport);

        createAuditLog(
                workflow.getAssetId(),
                serialNumber,
                nodeType,
                workflow.getOriginalStatus(),
                "Rejected",
                "Approval workflow rejected. Rejected by: " + rejectedBy +
                        ", Reason: " + rejectionComments
        );

        sendApprovalNotification(convertToDto(workflow), "REJECTED", serialNumber, nodeType);
        return true;
    }

    @Transactional
    public boolean cancelWorkflow(Integer workflowId, String cancelComments, String cancelledBy) {
        LOGGER.info("Cancelling workflow: {}", workflowId);

        Optional<WFFinancialApprovalRequest> workflowOpt = approvalWorkflowRepository.findById(workflowId);
        if (!workflowOpt.isPresent()) {
            LOGGER.warn("Workflow not found: {}", workflowId);
            return false;
        }

        WFFinancialApprovalRequest workflow = workflowOpt.get();
        Optional<FinancialReport> financialReportOpt = findFinancialReportByAssetId(workflow.getAssetId());
        if (!financialReportOpt.isPresent()) {
            LOGGER.error("Financial Report not found for ASSET_ID: {}", workflow.getAssetId());
            return false;
        }

        FinancialReport financialReport = financialReportOpt.get();
        String previousStatusFlag = financialReport.getStatusFlag();
        String serialNumber = financialReport.getAssetSerialNumber();
        String nodeType = financialReport.getNodeType();

        if (("pending modification".equals(workflow.getOriginalStatus()) || "pending movement".equals(workflow.getOriginalStatus()))
                && financialReport.getOriginalState() != null) {
            try {
                Map<String, Object> originalState = objectMapper.readValue(financialReport.getOriginalState(), Map.class);
                financialReport.setSiteId((String) originalState.get("siteId"));
                financialReport.setZone((String) originalState.get("zone"));
                financialReport.setNodeType((String) originalState.get("nodeType"));
                financialReport.setAssetName((String) originalState.get("assetName"));
                financialReport.setAssetType((String) originalState.get("assetType"));
                financialReport.setAssetCategory((String) originalState.get("assetCategory"));
                financialReport.setModel((String) originalState.get("model"));
                financialReport.setPartNumber((String) originalState.get("partNumber"));
                financialReport.setAssetSerialNumber((String) originalState.get("assetSerialNumber"));
                financialReport.setInstallationDate((String) originalState.get("installationDate"));
                financialReport.setInitialCost(originalState.get("initialCost") != null ? new BigDecimal(originalState.get("initialCost").toString()) : null);
                financialReport.setMonthlyDepreciationAmount(originalState.get("monthlyDepreciationAmount") != null ? new BigDecimal(originalState.get("monthlyDepreciationAmount").toString()) : null);
                financialReport.setAccumulatedDepreciation(originalState.get("accumulatedDepreciation") != null ? new BigDecimal(originalState.get("accumulatedDepreciation").toString()) : null);
                financialReport.setNetCost(originalState.get("netCost") != null ? new BigDecimal(originalState.get("netCost").toString()) : null);
                financialReport.setSalvageValue(originalState.get("salvageValue") != null ? new BigDecimal(originalState.get("salvageValue").toString()) : null);
                financialReport.setPoNumber((String) originalState.get("poNumber"));
                financialReport.setPoDate((String) originalState.get("poDate"));
                financialReport.setFaCategory((String) originalState.get("faCategory"));
                financialReport.setL1((String) originalState.get("l1"));
                financialReport.setL2((String) originalState.get("l2"));
                financialReport.setL3((String) originalState.get("l3"));
                financialReport.setL4((String) originalState.get("l4"));
                financialReport.setAccumulatedDepreciationCode((String) originalState.get("accumulatedDepreciationCode"));
                financialReport.setDepreciationCode((String) originalState.get("depreciationCode"));
                financialReport.setUsefulLifeMonths(originalState.get("usefulLifeMonths") != null ? ((Number) originalState.get("usefulLifeMonths")).intValue() : null);
                financialReport.setVendorName((String) originalState.get("vendorName"));
                financialReport.setVendorNumber((String) originalState.get("vendorNumber"));
                financialReport.setProjectNumber((String) originalState.get("projectNumber"));
                financialReport.setDescription((String) originalState.get("description"));
                financialReport.setOracleAssetId((String) originalState.get("oracleAssetId"));
                financialReport.setDateOfService((String) originalState.get("dateOfService"));
                financialReport.setTechnologySupported((String) originalState.get("technologySupported"));
                financialReport.setOldFarCategory((String) originalState.get("oldFarCategory"));
                financialReport.setCostCenterData((String) originalState.get("costCenterData"));
                financialReport.setNepAssetId((String) originalState.get("nepAssetId"));
                financialReport.setDeleted((Boolean) originalState.get("deleted"));
                financialReport.setAdjustment(originalState.get("adjustment") != null ? new BigDecimal(originalState.get("adjustment").toString()) : null);
                financialReport.setWriteOffDate(originalState.get("writeOffDate") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse((String) originalState.get("writeOffDate")) : null);
                financialReport.setTag((String) originalState.get("tag"));
                financialReport.setHostSerialNumber((String) originalState.get("hostSerialNumber"));
                financialReport.setTaskId((String) originalState.get("taskId"));
                financialReport.setPoLineNumber((String) originalState.get("poLineNumber"));
                financialReport.setReleaseNumber((String) originalState.get("releaseNumber"));
                financialReport.setSpectrumLicenseDate(originalState.get("spectrumLicenseDate") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse((String) originalState.get("spectrumLicenseDate")) : null);
                financialReport.setItemBarCode((String) originalState.get("itemBarCode"));
                financialReport.setRfid((String) originalState.get("rfid"));
                financialReport.setInvoiceNumber((String) originalState.get("invoiceNumber"));
            } catch (Exception e) {
                LOGGER.error("Failed to restore original state for report ID: {}", financialReport.getId(), e);
                return false;
            }
        }

        workflow.setUpdatedStatus("CANCELLED");
        workflow.setChangedBy(cancelledBy);
        workflow.setChangeDate(LocalDateTime.now());
        workflow.setComments((workflow.getComments() != null ? workflow.getComments() : "") +
                "\nCancel Reason: " + cancelComments);
        approvalWorkflowRepository.save(workflow);

        String newStatusFlag = determineStatusFlag(financialReport, previousStatusFlag, "CANCELLED", workflow.getOriginalStatus());
        financialReport.setFinancialApprovalStatus("Cancelled");
        financialReport.setStatusFlag(newStatusFlag);
        financialReport.setOriginalState(null);
        financialReport.setChangeDate(java.sql.Timestamp.valueOf(LocalDateTime.now()));
        financialReport.setChangedBy(cancelledBy);
        financialReportRepository.save(financialReport);

        createAuditLog(
                workflow.getAssetId(),
                serialNumber,
                nodeType,
                workflow.getOriginalStatus(),
                "Cancelled",
                "Approval workflow cancelled. Cancelled by: " + cancelledBy +
                        ", Reason: " + cancelComments
        );

        sendApprovalNotification(convertToDto(workflow), "CANCELLED", serialNumber, nodeType);
        return true;
    }

    @Transactional(readOnly = true)
    public Page<ApprovalWorkflowDTO> findAll(Pageable pageable) {
        Page<WFFinancialApprovalRequest> entityPage = approvalWorkflowRepository.findAll(pageable);
        return entityPage.map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public Optional<ApprovalWorkflowDTO> findById(int workflowId) {
        return approvalWorkflowRepository.findById(workflowId).map(this::convertToDto);
    }

    @Transactional
    public ApprovalWorkflowDTO save(ApprovalWorkflowDTO workflow) {
        WFFinancialApprovalRequest entity = convertToEntity(workflow);
        WFFinancialApprovalRequest savedEntity = approvalWorkflowRepository.save(entity);
        return convertToDto(savedEntity);
    }

    @Transactional
    public void saveAll(List<ApprovalWorkflowDTO> workflows) {
        if (workflows == null || workflows.isEmpty()) {
            LOGGER.warn("No workflows provided to saveAll");
            return;
        }

        try {
            List<WFFinancialApprovalRequest> entities = workflows.stream()
                    .map(this::convertToEntity)
                    .collect(Collectors.toList());

            approvalWorkflowRepository.saveAll(entities);
            LOGGER.info("Successfully saved {} approval workflows in batch", entities.size());
        } catch (Exception e) {
            LOGGER.error("Error saving batch of approval workflows", e);
            throw new RuntimeException("Failed to save approval workflows: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void saveAllEntities(List<WFFinancialApprovalRequest> workflows) {
        if (workflows == null || workflows.isEmpty()) {
            LOGGER.warn("No workflows provided to saveAllEntities");
            return;
        }

        try {
            approvalWorkflowRepository.saveAll(workflows);
            LOGGER.info("Successfully saved {} approval workflow entities in batch", workflows.size());
        } catch (Exception e) {
            LOGGER.error("Error saving batch of approval workflow entities", e);
            throw new RuntimeException("Failed to save approval workflow entities: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<ApprovalWorkflowDTO> findByStatus(String status) {
        List<WFFinancialApprovalRequest> entities = approvalWorkflowRepository.findByUpdatedStatus(status);
        return entities.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ApprovalWorkflowDTO> findByAssetId(String assetId) {
        List<WFFinancialApprovalRequest> entities = approvalWorkflowRepository.findByAssetIdOrderByInsertDateDesc(assetId);
        return entities.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ApprovalWorkflowDTO> findByAssetIds(List<String> assetIds) {
        List<WFFinancialApprovalRequest> entities = approvalWorkflowRepository.findByAssetIdInOrderByInsertDateDesc(assetIds);
        return entities.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ApprovalWorkflowDTO> findByProcessId(Integer processId) {
        List<WFFinancialApprovalRequest> entities = approvalWorkflowRepository.findByProcessIdOrderByInsertDateDesc(processId);
        return entities.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    private String determineOriginalStatus(FinancialReport financialReport, boolean isNewAsset) {
        if (financialReport.getInitialCost() == null || financialReport.getInitialCost().compareTo(BigDecimal.ZERO) == 0) {
            return "pending deletion";
        } else if (financialReport.getWriteOffDate() != null) {
            return "pending movement";
        } else if (isNewAsset) {
            return "pending addition";
        } else {
            return "pending modification";
        }
    }

    private String determineStatusFlag(FinancialReport financialReport, String currentStatusFlag, String nextWorkflowStatus, String originalStatus) {
        if ("pending deletion".equals(originalStatus)) {
            return "DECOMMISSIONED";
        }

        LocalDateTime insertDate = financialReport.getInsertDate() != null
                ? financialReport.getInsertDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : LocalDateTime.now();
        long daysSinceInsert = Duration.between(insertDate, LocalDateTime.now()).toDays();

        if (daysSinceInsert < 30 && !"APPROVED".equals(nextWorkflowStatus)) {
            return "NEW";
        } else {
            return "EXISTING";
        }
    }

    private Optional<FinancialReport> findFinancialReportByAssetId(String assetId) {
        List<FinancialReport> byAssetName = financialReportRepository.findAllByAssetName(assetId);
        if (byAssetName.size() > 1) {
            LOGGER.error("Multiple financial reports found for asset name: {}", assetId);
            throw new IllegalStateException("Multiple financial reports found for asset name: " + assetId);
        }
        if (!byAssetName.isEmpty()) {
            return Optional.of(byAssetName.get(0));
        }

        List<FinancialReport> bySerialNumber = financialReportRepository.findAllByAssetSerialNumber(assetId);
        if (bySerialNumber.size() > 1) {
            LOGGER.error("Multiple financial reports found for serial number: {}", assetId);
            throw new IllegalStateException("Multiple financial reports found for serial number: " + assetId);
        }
        return bySerialNumber.isEmpty() ? Optional.empty() : Optional.of(bySerialNumber.get(0));
    }

    private ApprovalWorkflowDTO convertToDto(WFFinancialApprovalRequest entity) {
        ApprovalWorkflowDTO workflow = new ApprovalWorkflowDTO();
        workflow.setId(entity.getId());
        workflow.setAssetId(entity.getAssetId());
        workflow.setObjectType(entity.getObjectType());
        workflow.setOriginalStatus(entity.getOriginalStatus());
        workflow.setUpdatedStatus(entity.getUpdatedStatus());
        workflow.setProcessId(entity.getProcessId());
        workflow.setComments(entity.getComments());
        workflow.setInsertedBy(entity.getInsertedBy());
        if (entity.getInsertDate() != null) {
            workflow.setInsertDate(java.util.Date.from(
                    entity.getInsertDate().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        }
        workflow.setChangedBy(entity.getChangedBy());
        if (entity.getChangeDate() != null) {
            workflow.setChangeDate(java.util.Date.from(
                    entity.getChangeDate().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        }
        return workflow;
    }

    private WFFinancialApprovalRequest convertToEntity(ApprovalWorkflowDTO workflow) {
        WFFinancialApprovalRequest entity = new WFFinancialApprovalRequest();
        if (workflow.getId() != null) {
            entity.setId(workflow.getId());
        }
        entity.setAssetId(workflow.getAssetId());
        entity.setObjectType(workflow.getObjectType());
        entity.setOriginalStatus(workflow.getOriginalStatus());
        entity.setUpdatedStatus(workflow.getUpdatedStatus());
        entity.setProcessId(workflow.getProcessId());
        entity.setComments(workflow.getComments());
        entity.setInsertedBy(workflow.getInsertedBy());
        if (workflow.getInsertDate() != null) {
            entity.setInsertDate(workflow.getInsertDate().toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime());
        }
        entity.setChangedBy(workflow.getChangedBy());
        if (workflow.getChangeDate() != null) {
            entity.setChangeDate(workflow.getChangeDate().toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime());
        }
        return entity;
    }

    private void createAuditLog(String objectId, String serialNumber, String nodeType,
                                String previousStatus, String newStatus, String notes) {
        AuditLog auditLog = new AuditLog();
        auditLog.setSerialNumber(serialNumber);
        auditLog.setPreviousStatus(previousStatus);
        auditLog.setNewStatus(newStatus);
        auditLog.setChangeDate(LocalDateTime.now());
        auditLog.setNodeType(nodeType);
        auditLog.setNotes(notes);
        auditLogRepository.save(auditLog);
    }

    private void sendApprovalNotification(ApprovalWorkflowDTO workflow, String notificationType, String serialNumber, String nodeType) {
    }
}
