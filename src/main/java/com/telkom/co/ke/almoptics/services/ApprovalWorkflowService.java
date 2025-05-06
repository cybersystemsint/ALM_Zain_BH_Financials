package com.telkom.co.ke.almoptics.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telkom.co.ke.almoptics.entities.ApprovalStatus;
import com.telkom.co.ke.almoptics.entities.tb_ApprovalWorkflow;
import com.telkom.co.ke.almoptics.models.ApprovalWorkflow;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.models.AuditLog;
import com.telkom.co.ke.almoptics.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.security.SecureRandom;

@Service
public class ApprovalWorkflowService {
    private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowService.class);
    private static final int MAX_ATTEMPTS = 10;
    private static final int ID_RANGE_MIN = 1;
    private static final int ID_RANGE_MAX = 999999999;
    private static final SecureRandom random = new SecureRandom();

    @Autowired
    private ApprovalWorkflowRepository approvalWorkflowRepository;

    @Autowired
    private FinancialReportRepo financialReportRepo;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;

    @Autowired
    private UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;

    @Autowired
    private UnmappedITInventoryRepository unmappedITInventoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<tb_ApprovalWorkflow> getPendingApprovals() {
        return findByStatus("Pending L1 Approval");
    }

    @Transactional
    public tb_ApprovalWorkflow createApprovalWorkflow(tb_FinancialReport financialReport, String nodeType, String originalStatus) {
        logger.info("Creating approval workflow for financial report ID: {}", financialReport.getId());

        String assetId = financialReport.getAssetName() != null && !financialReport.getAssetName().trim().isEmpty() ?
                financialReport.getAssetName() : financialReport.getAssetSerialNumber();
        if (assetId == null || assetId.trim().isEmpty()) {
            logger.error("Neither assetName nor assetSerialNumber provided for financial report ID: {}", financialReport.getId());
            throw new IllegalArgumentException("assetName or assetSerialNumber must be provided");
        }

        // Check for existing pending workflows
        List<tb_ApprovalWorkflow> existingWorkflows = findByAssetId(assetId);
        if (existingWorkflows.stream().anyMatch(w -> w.getUPDATED_STATUS().startsWith("Pending"))) {
            logger.warn("Pending workflow already exists for asset ID: {}. Cannot create new workflow.", assetId);
            throw new IllegalStateException("Asset is in approval workflow pending approvals.");
        }

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setAssetId(assetId);
        workflow.setObjectType(nodeType != null ? nodeType : "default");

        // Use provided originalStatus if present; otherwise, determine it
        String workflowStatus = originalStatus != null ? originalStatus :
                determineOriginalStatus(financialReport, financialReport.getId() == null || !financialReportRepo.existsById(financialReport.getId()));
        workflow.setOriginalStatus(workflowStatus);
        workflow.setUpdatedStatus("Pending L1 Approval");
        workflow.setProcessId(generateUniqueProcessId());
        workflow.setComments("Financial report " + workflowStatus.toLowerCase() + " pending L1 approval");
        workflow.setInsertedBy(financialReport.getInsertedBy() != null ? financialReport.getInsertedBy() : "system");
        workflow.setInsertDate(LocalDateTime.now());

        ApprovalWorkflow savedWorkflow = approvalWorkflowRepository.save(workflow);
        tb_ApprovalWorkflow dto = convertToDto(savedWorkflow);

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
    public tb_ApprovalWorkflow createDeletionWorkflow(tb_FinancialReport financialReport, String nodeType, String originalStatus) {
        logger.info("Creating deletion workflow for financial report ID: {}", financialReport.getId());

        String assetId = financialReport.getAssetName() != null && !financialReport.getAssetName().trim().isEmpty() ?
                financialReport.getAssetName() : financialReport.getAssetSerialNumber();
        if (assetId == null || assetId.trim().isEmpty()) {
            logger.error("Neither assetName nor assetSerialNumber provided for financial report ID: {}", financialReport.getId());
            throw new IllegalArgumentException("assetName or assetSerialNumber must be provided");
        }

        // Check for existing pending workflows
        List<tb_ApprovalWorkflow> existingWorkflows = findByAssetId(assetId);
        Optional<tb_ApprovalWorkflow> existingPendingWorkflow = existingWorkflows.stream()
                .filter(w -> "Pending L1 Approval".equals(w.getUPDATED_STATUS()))
                .max(Comparator.comparing(w -> w.getINSERTDATE() != null ?
                        w.getINSERTDATE().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() :
                        LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)));
        if (existingPendingWorkflow.isPresent()) {
            logger.info("Found existing deletion workflow for asset ID: {}. Returning workflow ID: {}", assetId, existingPendingWorkflow.get().getID());
            return existingPendingWorkflow.get();
        }

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setAssetId(assetId);
        workflow.setObjectType(nodeType != null ? nodeType : "default");
        workflow.setOriginalStatus("pending deletion");
        workflow.setUpdatedStatus("Pending L1 Approval");
        workflow.setProcessId(generateUniqueProcessId());
        workflow.setComments("Financial report marked for deletion - pending L1 approval");
        workflow.setInsertedBy(financialReport.getChangedBy() != null ? financialReport.getChangedBy() : "system");
        workflow.setInsertDate(LocalDateTime.now());

        financialReport.setStatusFlag("DECOMMISSIONED");
        financialReport.setFinancialApprovalStatus("Pending");
        financialReportRepo.save(financialReport);

        ApprovalWorkflow savedWorkflow = approvalWorkflowRepository.save(workflow);
        tb_ApprovalWorkflow dto = convertToDto(savedWorkflow);

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
            logger.debug("Generated candidate PROCESS_ID: {}", processId);

            boolean exists = approvalWorkflowRepository.existsByProcessId(processId);
            if (!exists) {
                logger.debug("Unique PROCESS_ID confirmed: {}", processId);
                return processId;
            }

            logger.warn("PROCESS_ID {} already exists, retrying (attempt {}/{})", processId, attempts + 1, MAX_ATTEMPTS);
            attempts++;
        }

        logger.error("Failed to generate unique PROCESS_ID after {} attempts", MAX_ATTEMPTS);
        throw new RuntimeException("Unable to generate a unique PROCESS_ID after " + MAX_ATTEMPTS + " attempts");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void deleteFromUnmappedInventory(String identifier) {
        try {
            // Delete from active inventory
            if (unmappedActiveInventoryRepository.findBySerialNumber(identifier).isPresent()) {
                unmappedActiveInventoryRepository.deleteBySerialNumber(identifier);
                logger.info("Deleted unmapped active inventory for identifier: {}", identifier);
            }
            // Delete from passive inventory
            if (unmappedPassiveInventoryRepository.findBySerial(identifier).isPresent()) {
                unmappedPassiveInventoryRepository.deleteBySerial(identifier);
                logger.info("Deleted unmapped passive inventory by serial for identifier: {}", identifier);
            }
            if (unmappedPassiveInventoryRepository.findByObjectId(identifier).isPresent()) {
                unmappedPassiveInventoryRepository.deleteByObjectId(identifier);
                logger.info("Deleted unmapped passive inventory by objectId for identifier: {}", identifier);
            }
            if (unmappedPassiveInventoryRepository.findByElementType(identifier).isPresent()) {
                unmappedPassiveInventoryRepository.deleteByElementType(identifier);
                logger.info("Deleted unmapped passive inventory by elementType for identifier: {}", identifier);
            }
            // Delete from IT inventory
            if (unmappedITInventoryRepository.findByHostSerialNumber(identifier).isPresent()) {
                unmappedITInventoryRepository.deleteByHostSerialNumber(identifier);
                logger.info("Deleted unmapped IT inventory by hostSerialNumber for identifier: {}", identifier);
            }
            if (unmappedITInventoryRepository.findByHardwareSerialNumber(identifier).isPresent()) {
                unmappedITInventoryRepository.deleteByHardwareSerialNumber(identifier);
                logger.info("Deleted unmapped IT inventory by hardwareSerialNumber for identifier: {}", identifier);
            }
            if (unmappedITInventoryRepository.findByElementId(identifier).isPresent()) {
                unmappedITInventoryRepository.deleteByElementId(identifier);
                logger.info("Deleted unmapped IT inventory by elementId for identifier: {}", identifier);
            }
            if (unmappedITInventoryRepository.findByHostName(identifier).isPresent()) {
                unmappedITInventoryRepository.deleteByHostName(identifier);
                logger.info("Deleted unmapped IT inventory by hostName for identifier: {}", identifier);
            }
        } catch (Exception e) {
            logger.error("Failed to delete unmapped inventory for identifier: {}", identifier, e);
            throw new RuntimeException("Failed to delete unmapped inventory for identifier: " + identifier, e);
        }
    }

    @Transactional
    public boolean approveWorkflow(Integer workflowId, String approverComments, String approvedBy) {
        logger.info("Approving workflow: {}", workflowId);

        Optional<ApprovalWorkflow> workflowOpt = approvalWorkflowRepository.findById(workflowId);
        if (!workflowOpt.isPresent()) {
            logger.warn("Workflow not found: {}", workflowId);
            return false;
        }

        ApprovalWorkflow workflow = workflowOpt.get();
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
                logger.warn("Invalid status for approval: {}", currentStatus);
                return false;
        }

        Optional<tb_FinancialReport> financialReportOpt = findFinancialReportByAssetId(workflow.getAssetId());
        if (!financialReportOpt.isPresent()) {
            logger.error("Financial Report not found for ASSET_ID: {}", workflow.getAssetId());
            return false;
        }

        tb_FinancialReport financialReport = financialReportOpt.get();
        String previousStatus = financialReport.getFinancialApprovalStatus();
        String previousStatusFlag = financialReport.getStatusFlag();
        String serialNumber = financialReport.getAssetSerialNumber();
        String nodeType = financialReport.getNodeType();

        String newStatusFlag = determineStatusFlag(financialReport, previousStatusFlag, nextStatus, originalStatus);

        if ("APPROVED".equals(nextStatus)) {
            if ("pending deletion".equals(originalStatus)) {
                logger.info("Deleting financial report ID {} for {}", financialReport.getId(), originalStatus);
                financialReportRepo.delete(financialReport);
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
                logger.info("Moving financial report ID {} to write-off", financialReport.getId());
                financialReport.setFinancialApprovalStatus("Approved");
                financialReport.setStatusFlag(newStatusFlag);
                financialReport.setChangeDate(java.sql.Timestamp.valueOf(LocalDateTime.now()));
                financialReport.setChangedBy(approvedBy);
                financialReportRepo.save(financialReport);
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
                logger.info("Applying L3 approval for financial report ID {}. Current values: initialCost={}, assetSerialNumber={}",
                        financialReport.getId(), financialReport.getInitialCost(), financialReport.getAssetSerialNumber());

                financialReport.setStatusFlag(newStatusFlag);
                financialReport.setFinancialApprovalStatus("Approved");
                financialReport.setOriginalState(null);
                financialReport.setChangeDate(java.sql.Timestamp.valueOf(LocalDateTime.now()));
                financialReport.setChangedBy(approvedBy);

                financialReportRepo.save(financialReport);

                // Delete from unmapped inventory for additions
                if ("pending addition".equals(originalStatus)) {
                    String identifier = workflow.getAssetId();
                    if (serialNumber != null && !serialNumber.trim().isEmpty()) {
                        identifier = serialNumber;
                    }
                    try {
                        deleteFromUnmappedInventory(identifier);
                    } catch (Exception e) {
                        logger.error("Failed to delete unmapped inventory for identifier: {}", identifier, e);
                        // Continue to allow approval even if deletion fails, but log the error
                    }
                }

                logger.info("Saved financial report ID {}. New values: initialCost={}, assetSerialNumber={}",
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
                logger.warn("Invalid original status for approval: {}", originalStatus);
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

            financialReport.setFinancialApprovalStatus("Pending");
            financialReport.setStatusFlag(newStatusFlag);
            financialReport.setChangeDate(java.sql.Timestamp.valueOf(LocalDateTime.now()));
            financialReport.setChangedBy(approvedBy);
            financialReportRepo.save(financialReport);

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

        logger.info("Workflow {} approved to status: {}", workflowId, nextStatus);
        return true;
    }

    @Transactional
    public boolean rejectWorkflow(Integer workflowId, String rejectionComments, String rejectedBy) {
        logger.info("Rejecting workflow: {}", workflowId);

        Optional<ApprovalWorkflow> workflowOpt = approvalWorkflowRepository.findById(workflowId);
        if (!workflowOpt.isPresent()) {
            logger.warn("Workflow not found: {}", workflowId);
            return false;
        }

        ApprovalWorkflow workflow = workflowOpt.get();
        Optional<tb_FinancialReport> financialReportOpt = findFinancialReportByAssetId(workflow.getAssetId());
        if (!financialReportOpt.isPresent()) {
            logger.error("Financial Report not found for ASSET_ID: {}", workflow.getAssetId());
            return false;
        }

        tb_FinancialReport financialReport = financialReportOpt.get();
        String previousStatusFlag = financialReport.getStatusFlag();
        String serialNumber = financialReport.getAssetSerialNumber();
        String nodeType = financialReport.getNodeType();
        String originalStatus = workflow.getOriginalStatus();

        // For additions, delete the financial report
        if ("pending addition".equals(originalStatus)) {
            logger.info("Deleting financial report ID {} due to rejection of addition", financialReport.getId());
            financialReportRepo.delete(financialReport);
            workflow.setUpdatedStatus("REJECTED");
            workflow.setChangedBy(rejectedBy);
            workflow.setChangeDate(LocalDateTime.now());
            workflow.setComments((workflow.getComments() != null ? workflow.getComments() : "") +
                    "\nRejection Reason: " + rejectionComments);
            approvalWorkflowRepository.save(workflow);

            createAuditLog(
                    workflow.getAssetId(),
                    serialNumber,
                    nodeType,
                    originalStatus,
                    "REJECTED",
                    "Financial report deleted due to rejection of addition. Rejected by: " + rejectedBy +
                            ", Reason: " + rejectionComments
            );
            sendApprovalNotification(convertToDto(workflow), "REJECTED", serialNumber, nodeType);
            return true;
        }

        // For modifications, movements, or deletions, restore original state
        if (("pending modification".equals(originalStatus) || "pending movement".equals(originalStatus) || "pending deletion".equals(originalStatus))
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

                // For modifications, maintain Approved status
                if ("pending modification".equals(originalStatus)) {
                    financialReport.setFinancialApprovalStatus("Approved");
                } else {
                    financialReport.setFinancialApprovalStatus("Rejected");
                }
            } catch (Exception e) {
                logger.error("Failed to restore original state for report ID: {}", financialReport.getId(), e);
                return false;
            }
        } else {
            financialReport.setFinancialApprovalStatus("Rejected");
        }

        workflow.setUpdatedStatus("REJECTED");
        workflow.setChangedBy(rejectedBy);
        workflow.setChangeDate(LocalDateTime.now());
        workflow.setComments((workflow.getComments() != null ? workflow.getComments() : "") +
                "\nRejection Reason: " + rejectionComments);
        approvalWorkflowRepository.save(workflow);

        String newStatusFlag = determineStatusFlag(financialReport, previousStatusFlag, "REJECTED", originalStatus);
        financialReport.setStatusFlag(newStatusFlag);
        financialReport.setOriginalState(null);
        financialReport.setChangeDate(java.sql.Timestamp.valueOf(LocalDateTime.now()));
        financialReport.setChangedBy(rejectedBy);
        financialReportRepo.save(financialReport);

        createAuditLog(
                workflow.getAssetId(),
                serialNumber,
                nodeType,
                originalStatus,
                "REJECTED",
                "Approval workflow rejected. Rejected by: " + rejectedBy +
                        ", Reason: " + rejectionComments
        );

        sendApprovalNotification(convertToDto(workflow), "REJECTED", serialNumber, nodeType);
        return true;
    }

    @Transactional
    public boolean cancelWorkflow(Integer workflowId, String cancelComments, String cancelledBy) {
        logger.info("Cancelling workflow: {}", workflowId);

        Optional<ApprovalWorkflow> workflowOpt = approvalWorkflowRepository.findById(workflowId);
        if (!workflowOpt.isPresent()) {
            logger.warn("Workflow not found: {}", workflowId);
            return false;
        }

        ApprovalWorkflow workflow = workflowOpt.get();
        Optional<tb_FinancialReport> financialReportOpt = findFinancialReportByAssetId(workflow.getAssetId());
        if (!financialReportOpt.isPresent()) {
            logger.error("Financial Report not found for ASSET_ID: {}", workflow.getAssetId());
            return false;
        }

        tb_FinancialReport financialReport = financialReportOpt.get();
        String previousStatusFlag = financialReport.getStatusFlag();
        String serialNumber = financialReport.getAssetSerialNumber();
        String nodeType = financialReport.getNodeType();
        String originalStatus = workflow.getOriginalStatus();

        // For additions, delete the financial report
        if ("pending addition".equals(originalStatus)) {
            logger.info("Deleting financial report ID {} due to cancellation of addition", financialReport.getId());
            financialReportRepo.delete(financialReport);
            workflow.setUpdatedStatus("CANCELLED");
            workflow.setChangedBy(cancelledBy);
            workflow.setChangeDate(LocalDateTime.now());
            workflow.setComments((workflow.getComments() != null ? workflow.getComments() : "") +
                    "\nCancel Reason: " + cancelComments);
            approvalWorkflowRepository.save(workflow);

            createAuditLog(
                    workflow.getAssetId(),
                    serialNumber,
                    nodeType,
                    originalStatus,
                    "CANCELLED",
                    "Financial report deleted due to cancellation of addition. Cancelled by: " + cancelledBy +
                            ", Reason: " + cancelComments
            );
            sendApprovalNotification(convertToDto(workflow), "CANCELLED", serialNumber, nodeType);
            return true;
        }

        // For modifications, movements, or deletions, restore original state
        if (("pending modification".equals(originalStatus) || "pending movement".equals(originalStatus) || "pending deletion".equals(originalStatus))
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

                // For modifications, maintain Approved status
                if ("pending modification".equals(originalStatus)) {
                    financialReport.setFinancialApprovalStatus("Approved");
                } else {
                    financialReport.setFinancialApprovalStatus("Cancelled");
                }
            } catch (Exception e) {
                logger.error("Failed to restore original state for report ID: {}", financialReport.getId(), e);
                return false;
            }
        } else {
            financialReport.setFinancialApprovalStatus("Cancelled");
        }

        workflow.setUpdatedStatus("CANCELLED");
        workflow.setChangedBy(cancelledBy);
        workflow.setChangeDate(LocalDateTime.now());
        workflow.setComments((workflow.getComments() != null ? workflow.getComments() : "") +
                "\nCancel Reason: " + cancelComments);
        approvalWorkflowRepository.save(workflow);

        String newStatusFlag = determineStatusFlag(financialReport, previousStatusFlag, "CANCELLED", originalStatus);
        financialReport.setStatusFlag(newStatusFlag);
        financialReport.setOriginalState(null);
        financialReport.setChangeDate(java.sql.Timestamp.valueOf(LocalDateTime.now()));
        financialReport.setChangedBy(cancelledBy);
        financialReportRepo.save(financialReport);

        createAuditLog(
                workflow.getAssetId(),
                serialNumber,
                nodeType,
                originalStatus,
                "CANCELLED",
                "Approval workflow cancelled. Cancelled by: " + cancelledBy +
                        ", Reason: " + cancelComments
        );

        sendApprovalNotification(convertToDto(workflow), "CANCELLED", serialNumber, nodeType);
        return true;
    }

    @Transactional(readOnly = true)
    public Page<tb_ApprovalWorkflow> findAll(Pageable pageable) {
        Page<ApprovalWorkflow> entityPage = approvalWorkflowRepository.findAll(pageable);
        return entityPage.map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public Optional<tb_ApprovalWorkflow> findById(int workflowId) {
        return approvalWorkflowRepository.findById(workflowId).map(this::convertToDto);
    }

    @Transactional
    public tb_ApprovalWorkflow save(tb_ApprovalWorkflow workflow) {
        ApprovalWorkflow entity = convertToEntity(workflow);
        ApprovalWorkflow savedEntity = approvalWorkflowRepository.save(entity);
        return convertToDto(savedEntity);
    }

    @Transactional
    public void saveAll(List<tb_ApprovalWorkflow> workflows) {
        if (workflows == null || workflows.isEmpty()) {
            logger.warn("No workflows provided to saveAll");
            return;
        }

        try {
            List<ApprovalWorkflow> entities = workflows.stream()
                    .map(this::convertToEntity)
                    .collect(Collectors.toList());

            approvalWorkflowRepository.saveAll(entities);
            logger.info("Successfully saved {} approval workflows in batch", entities.size());
        } catch (Exception e) {
            logger.error("Error saving batch of approval workflows", e);
            throw new RuntimeException("Failed to save approval workflows: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void saveAllEntities(List<ApprovalWorkflow> workflows) {
        if (workflows == null || workflows.isEmpty()) {
            logger.warn("No workflows provided to saveAllEntities");
            return;
        }

        try {
            approvalWorkflowRepository.saveAll(workflows);
            logger.info("Successfully saved {} approval workflow entities in batch", workflows.size());
        } catch (Exception e) {
            logger.error("Error saving batch of approval workflow entities", e);
            throw new RuntimeException("Failed to save approval workflow entities: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<tb_ApprovalWorkflow> findByStatus(String status) {
        List<ApprovalWorkflow> entities = approvalWorkflowRepository.findByUpdatedStatus(status);
        return entities.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<tb_ApprovalWorkflow> findByAssetId(String assetId) {
        List<ApprovalWorkflow> entities = approvalWorkflowRepository.findByAssetIdOrderByInsertDateDesc(assetId);
        return entities.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<tb_ApprovalWorkflow> findByAssetIds(List<String> assetIds) {
        List<ApprovalWorkflow> entities = approvalWorkflowRepository.findByAssetIdInOrderByInsertDateDesc(assetIds);
        return entities.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<tb_ApprovalWorkflow> findByProcessId(Integer processId) {
        List<ApprovalWorkflow> entities = approvalWorkflowRepository.findByProcessIdOrderByInsertDateDesc(processId);
        return entities.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    private String determineOriginalStatus(tb_FinancialReport financialReport, boolean isNewAsset) {
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

    private String determineStatusFlag(tb_FinancialReport financialReport, String currentStatusFlag, String nextWorkflowStatus, String originalStatus) {
        if ("pending deletion".equals(originalStatus)) {
            return "DECOMMISSIONED";
        }

        LocalDateTime insertDate = financialReport.getInsertDate() != null
                ? financialReport.getInsertDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : LocalDateTime.now();
        long daysSinceInsert = Duration.between(insertDate, LocalDateTime.now()).toDays();

        if ("NEW".equals(currentStatusFlag) && daysSinceInsert < 30 && !"APPROVED".equals(nextWorkflowStatus)) {
            return "NEW";
        } else {
            return "EXISTING";
        }
    }

    private Optional<tb_FinancialReport> findFinancialReportByAssetId(String assetId) {
        List<tb_FinancialReport> byAssetName = financialReportRepo.findAllByAssetName(assetId);
        if (byAssetName.size() > 1) {
            logger.error("Multiple financial reports found for asset name: {}", assetId);
            throw new IllegalStateException("Multiple financial reports found for asset name: " + assetId);
        }
        if (!byAssetName.isEmpty()) {
            return Optional.of(byAssetName.get(0));
        }

        List<tb_FinancialReport> bySerialNumber = financialReportRepo.findAllByAssetSerialNumber(assetId);
        if (bySerialNumber.size() > 1) {
            logger.error("Multiple financial reports found for serial number: {}", assetId);
            throw new IllegalStateException("Multiple financial reports found for serial number: " + assetId);
        }
        return bySerialNumber.isEmpty() ? Optional.empty() : Optional.of(bySerialNumber.get(0));
    }

    private tb_ApprovalWorkflow convertToDto(ApprovalWorkflow entity) {
        tb_ApprovalWorkflow workflow = new tb_ApprovalWorkflow();
        workflow.setID(entity.getId());
        workflow.setASSET_ID(entity.getAssetId());
        workflow.setObjectType(entity.getObjectType());
        workflow.setORIGINAL_STATUS(entity.getOriginalStatus());
        workflow.setUPDATED_STATUS(entity.getUpdatedStatus());
        workflow.setPROCESS_ID(entity.getProcessId());
        workflow.setCOMMENTS(entity.getComments());
        workflow.setINSERTEDBY(entity.getInsertedBy());
        if (entity.getInsertDate() != null) {
            workflow.setINSERTDATE(java.util.Date.from(
                    entity.getInsertDate().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        }
        workflow.setCHANGEDBY(entity.getChangedBy());
        if (entity.getChangeDate() != null) {
            workflow.setCHANGEDATE(java.util.Date.from(
                    entity.getChangeDate().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        }
        return workflow;
    }

    private ApprovalWorkflow convertToEntity(tb_ApprovalWorkflow workflow) {
        ApprovalWorkflow entity = new ApprovalWorkflow();
        if (workflow.getID() != null) {
            entity.setId(workflow.getID());
        }
        entity.setAssetId(workflow.getASSET_ID());
        entity.setObjectType(workflow.getObjectType());
        entity.setOriginalStatus(workflow.getORIGINAL_STATUS());
        entity.setUpdatedStatus(workflow.getUPDATED_STATUS());
        entity.setProcessId(workflow.getPROCESS_ID());
        entity.setComments(workflow.getCOMMENTS());
        entity.setInsertedBy(workflow.getINSERTEDBY());
        if (workflow.getINSERTDATE() != null) {
            entity.setInsertDate(workflow.getINSERTDATE().toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime());
        }
        entity.setChangedBy(workflow.getCHANGEDBY());
        if (workflow.getCHANGEDATE() != null) {
            entity.setChangeDate(workflow.getCHANGEDATE().toInstant()
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

    private void sendApprovalNotification(tb_ApprovalWorkflow workflow, String notificationType,
                                          String serialNumber, String nodeType) {
        // Implementation unchanged
    }
}