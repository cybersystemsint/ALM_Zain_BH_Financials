package com.telkom.co.ke.almoptics.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telkom.co.ke.almoptics.dto.ApprovalRequest;
import com.telkom.co.ke.almoptics.dto.PageResult;
import com.telkom.co.ke.almoptics.entities.tb_ApprovalWorkflow;
import com.telkom.co.ke.almoptics.models.ApprovalWorkflow;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.models.AuditLog;
import com.telkom.co.ke.almoptics.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.security.SecureRandom;
import org.springframework.data.jpa.domain.Specification;
import org.apache.poi.xssf.streaming.SXSSFSheet;

import javax.persistence.Query;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;



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

    /** New file-based audit pipeline. Replaces direct auditLogRepository.save(...). */
    @Autowired(required = false)
    private AuditLogService auditLogService;

//    @Autowired
//    private NotificationService notificationService;

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

//    @Transactional(readOnly = true)
//    public List<tb_ApprovalWorkflow> getPendingApprovals() {
//        return findByStatus("Pending L1 Approval");
//    }

    @Transactional
    public tb_ApprovalWorkflow createApprovalWorkflow(tb_FinancialReport financialReport, String nodeType, String originalStatus, String username) {
        logger.info("Creating approval workflow for financial report ID: {} by user: {}", financialReport.getId(), username);

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
        workflow.setInsertedBy(username); // Use provided username
        workflow.setInsertDate(LocalDateTime.now());

        ApprovalWorkflow savedWorkflow = approvalWorkflowRepository.save(workflow);
        tb_ApprovalWorkflow dto = convertToDto(savedWorkflow);

        createAuditLog(
                assetId,
                financialReport.getAssetSerialNumber(),
                nodeType,
                workflowStatus,
                "Pending L1 Approval",
                "Approval workflow created for " + workflowStatus.toLowerCase() + " by " + username
        );

        sendApprovalNotification(dto, "REQUEST", financialReport.getAssetSerialNumber(), nodeType);
        return dto;
    }

    @Transactional
    public tb_ApprovalWorkflow createDeletionWorkflow(tb_FinancialReport financialReport, String nodeType, String originalStatus, String username) {
        logger.info("Creating deletion workflow for financial report ID: {} by user: {}", financialReport.getId(), username);

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
        workflow.setInsertedBy(username); // Use provided username
        workflow.setInsertDate(LocalDateTime.now());

        financialReport.setStatusFlag("DECOMMISSIONED");
        financialReport.setFinancialApprovalStatus("Pending");
        financialReport.setChangedBy(username); // Set ChangedBy to username
        financialReportRepo.save(financialReport);

        ApprovalWorkflow savedWorkflow = approvalWorkflowRepository.save(workflow);
        tb_ApprovalWorkflow dto = convertToDto(savedWorkflow);

        createAuditLog(
                assetId,
                financialReport.getAssetSerialNumber(),
                nodeType,
                "pending deletion",
                "Pending L1 Approval",
                "Deletion workflow created for financial report by " + username
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
                nextStatus = "Approved";
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

        if ("Approved".equals(nextStatus)) {
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

//                financialReport.setStatusFlag(newStatusFlag);
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
            workflow.setUpdatedStatus("Approved");
            workflow.setChangedBy(approvedBy);
            workflow.setChangeDate(LocalDateTime.now());
            approvalWorkflowRepository.save(workflow);
            logger.info("Workflow ID {} marked as APPROVED and retained in tb_WF_Financial_Approval_Request", workflowId);
//            approvalWorkflowRepository.delete(workflow);
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
                financialReport.setDeleted((String) originalState.get("deleted"));
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
                financialReport.setDeleted((String) originalState.get("deleted"));
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

//    @Transactional(readOnly = true)
//    public List<tb_ApprovalWorkflow> findByStatus(String status) {
//        List<ApprovalWorkflow> entities = approvalWorkflowRepository.findByUpdatedStatus(status);
//        return entities.stream().map(this::convertToDto).collect(Collectors.toList());
//    }
    @Transactional(readOnly = true)
    public Page<tb_ApprovalWorkflow> findByUpdatedStatusContaining(
            String updatedStatus, Pageable pageable) {
        Page<ApprovalWorkflow> entityPage = approvalWorkflowRepository
                .findByUpdatedStatusContaining(updatedStatus.toLowerCase(), pageable);
        return entityPage.map(this::convertToDto);
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

        if ("NEW".equals(currentStatusFlag) && daysSinceInsert < 30 && !"Approved".equals(nextWorkflowStatus)) {
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
        // Routed through AuditLogService → file-based 'audit' logger.
        // tb_AuditLog writes are off by default (audit.persist.db=false)
        // to keep the DB lean.
        if (auditLogService != null) {
            auditLogService.logStatusChange(objectId, serialNumber, previousStatus,
                    newStatus, nodeType, notes, "SYSTEM");
        }
    }

    // Modified ApprovalWorkflowService.findByFilters (added objectStatus param and subquery)
    @Transactional(readOnly = true)
    public Page<tb_ApprovalWorkflow> findByFilters(
            String team, String objectType, String assetId, String originalStatus,
            String updatedStatus, String processId, String startDate, String endDate,
            String objectStatus, Pageable pageable) {

        Specification<ApprovalWorkflow> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (team != null && !team.trim().isEmpty()) {
                String expectedStatus = switch (team) {
                    case "Financial L1" -> "Pending L1 Approval";
                    case "Financial L2" -> "Pending L2 Approval";
                    case "Financial L3" -> "Pending L3 Approval";
                    default -> null;
                };
                if (expectedStatus != null) {
                    predicates.add(cb.equal(cb.lower(root.get("updatedStatus")), expectedStatus.toLowerCase()));
                }
            }

            if (objectType != null && !objectType.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("objectType")), objectType.toLowerCase()));
            }
            if (assetId != null && !assetId.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("assetId")), assetId.toLowerCase()));
            }
            if (originalStatus != null && !originalStatus.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("originalStatus")), originalStatus.toLowerCase()));
            }
            if (updatedStatus != null && !updatedStatus.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("updatedStatus")), updatedStatus.toLowerCase()));
            }
            if (processId != null && !processId.trim().isEmpty()) {
                try {
                    int pid = Integer.parseInt(processId);
                    predicates.add(cb.equal(root.get("processId"), pid));
                } catch (NumberFormatException e) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                }
            }
            if (startDate != null && !startDate.trim().isEmpty()) {
                LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
                predicates.add(cb.greaterThanOrEqualTo(root.get("insertDate"), start));
            }
            if (endDate != null && !endDate.trim().isEmpty()) {
                LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
                predicates.add(cb.lessThanOrEqualTo(root.get("insertDate"), end));
            }
            if (objectStatus != null && !objectStatus.trim().isEmpty()) {
                Subquery<tb_FinancialReport> subquery = query.subquery(tb_FinancialReport.class);
                Root<tb_FinancialReport> reportRoot = subquery.from(tb_FinancialReport.class);
                subquery.select(reportRoot);
                Predicate assetMatch = cb.or(
                        cb.equal(reportRoot.get("assetName"), root.get("assetId")),
                        cb.equal(reportRoot.get("assetSerialNumber"), root.get("assetId"))
                );
                subquery.where(assetMatch, cb.equal(cb.lower(reportRoot.get("statusFlag")), objectStatus.toLowerCase()));
                predicates.add(cb.exists(subquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ApprovalWorkflow> entityPage = approvalWorkflowRepository.findAll(spec, pageable);
        return entityPage.map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public Page<tb_ApprovalWorkflow> searchByAssetIdOrSerialNumber(String query, Pageable pageable) {
        List<tb_FinancialReport> reports = financialReportRepo.findAllByAssetNameContainingIgnoreCaseOrAssetSerialNumberContainingIgnoreCase(query);
        List<tb_ApprovalWorkflow> workflows = new ArrayList<>();
        for (tb_FinancialReport report : reports) {
            List<tb_ApprovalWorkflow> byAssetId = findByAssetId(report.getAssetName() != null ? report.getAssetName() : report.getAssetSerialNumber());
            workflows.addAll(byAssetId);
        }

        // Sort and paginate
        workflows.sort(Comparator.comparing(tb_ApprovalWorkflow::getINSERTDATE, Comparator.nullsLast(Comparator.reverseOrder())));
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), workflows.size());
        List<tb_ApprovalWorkflow> pagedWorkflows = workflows.subList(start, end);

        return new PageImpl<>(pagedWorkflows, pageable, workflows.size());
    }

    private void sendApprovalNotification(tb_ApprovalWorkflow workflow, String notificationType,
                                          String serialNumber, String nodeType) {
        // Implementation unchanged
    }

    /**
     * Search for completed approvals (APPROVED or REJECTED status)
     * Used for historical tracking of all completed approval workflows
     * No team filtering - returns entire history
     */
    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> searchApprovalHistory(ApprovalRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 100;

        List<Map<String, Object>> data = fetchApprovalHistoryBatchForPage(request, page * size, size);
        long totalElements = countApprovalHistory(request);

        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setTotalElements(totalElements);
        result.setTotalPages((int) Math.ceil((double) totalElements / size));
        result.setPage(page);
        result.setSize(size);
        result.setdata(data);

        return result;
    }

    /**
     * Fetch approval history batch for page display
     */
    private List<Map<String, Object>> fetchApprovalHistoryBatchForPage(ApprovalRequest request, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
        SELECT w.ID as WORKFLOW_ID, w.Object_Type, w.ASSET_ID, w.ORIGINAL_STATUS, w.UPDATED_STATUS,
               w.PROCESS_ID, w.INSERTEDBY as REQUESTER, w.CHANGEDBY as UPDATER, w.COMMENTS,
               w.INSERTDATE, w.CHANGEDATE,
               fr.siteId as WAREHOUSE_ID, fr.assetSerialNumber as ASSET_SERIAL_NUMBER
        FROM tb_WF_Financial_Approval_Request w
        LEFT JOIN tb_FinancialReport fr
            ON (fr.assetName = w.ASSET_ID OR fr.assetSerialNumber = w.ASSET_ID)
        WHERE (LOWER(w.UPDATED_STATUS) = 'approved' OR LOWER(w.UPDATED_STATUS) = 'rejected')
        """);

        List<Object> params = new ArrayList<>();

        buildApprovalHistoryFilters(sql, params, request);

        sql.append(" ORDER BY w.CHANGEDATE DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        Query query = entityManager.createNativeQuery(sql.toString());

        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return convertToMapList(rows);
    }

    /**
     * Count approval history records matching filters
     */
    private long countApprovalHistory(ApprovalRequest request) {
        StringBuilder sql = new StringBuilder("""
        SELECT COUNT(*)
        FROM tb_WF_Financial_Approval_Request w
        LEFT JOIN tb_FinancialReport fr
            ON (fr.assetName = w.ASSET_ID OR fr.assetSerialNumber = w.ASSET_ID)
        WHERE (LOWER(w.UPDATED_STATUS) = 'approved' OR LOWER(w.UPDATED_STATUS) = 'rejected')
        """);

        List<Object> params = new ArrayList<>();

        buildApprovalHistoryFilters(sql, params, request);

        Query query = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Build filters for approval history (no team filter, history focused)
     * Supports both "filters" and "filterBy" parameter names for flexibility
     */
    private void buildApprovalHistoryFilters(StringBuilder sql, List<Object> params, ApprovalRequest request) {
        // 1. Multi-filters (new flexible way) - support both "filters" and "filterBy"
        List<ApprovalRequest.Filter> filterList = request.getFilters();
        if (filterList == null || filterList.isEmpty()) {
            filterList = request.getFilterBy();  // Try alternative name
        }

        if (filterList != null && !filterList.isEmpty()) {
            for (ApprovalRequest.Filter f : filterList) {
                if (f.getColumn() == null || f.getColumn().trim().isEmpty()) continue;

                String col = f.getColumn().trim();
                String value = f.getValue() != null ? f.getValue().trim() : "";
                if (value.isEmpty()) continue;

                String operator = f.getOperator() != null ? f.getOperator().name() : "CONTAINS";

                appendFilterCondition(sql, params, col, value, operator);
            }
        }

        // 2. Legacy / backward compatibility filters (NO TEAM FILTER FOR HISTORY)
        appendSimpleFilter(sql, params, "w.Object_Type", request.getObjectType());
        appendSimpleFilter(sql, params, "w.ASSET_ID", request.getAssetId());
        appendSimpleFilter(sql, params, "w.ORIGINAL_STATUS", request.getOriginalStatus());
        appendSimpleFilter(sql, params, "w.UPDATED_STATUS", request.getUpdatedStatus());

        if (request.getProcessId() != null && !request.getProcessId().isBlank()) {
            try {
                sql.append(" AND w.PROCESS_ID = ?");
                params.add(Integer.parseInt(request.getProcessId()));
            } catch (Exception ignored) {}
        }

        // Date filtering based on CHANGEDATE for history (when it was completed)
        if (request.getStartDate() != null && !request.getStartDate().isBlank()) {
            sql.append(" AND w.CHANGEDATE >= ?");
            params.add(request.getStartDate() + " 00:00:00");
        }
        if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
            sql.append(" AND w.CHANGEDATE <= ?");
            params.add(request.getEndDate() + " 23:59:59");
        }

        if (request.getObjectStatus() != null && !request.getObjectStatus().isBlank()) {
            sql.append("""
            AND EXISTS (
                SELECT 1 FROM tb_FinancialReport fr2
                WHERE (fr2.assetName = w.ASSET_ID OR fr2.assetSerialNumber = w.ASSET_ID)
                  AND LOWER(fr2.statusFlag) = LOWER(?)
            )
            """);
            params.add(request.getObjectStatus());
        }
    }

    /**
     * Stream approval history to CSV export
     */
    public void streamHistoryExportToCsv(ApprovalRequest request, PrintWriter writer) {
        logger.info("Approval History CSV export started. ExportAll: {}", request.isExportAll());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        writer.println(String.join(",", EXPORT_HEADERS));
        writer.flush();

        Integer lastId = null;
        int exported = 0;
        int limit = request.isExportAll() ? Integer.MAX_VALUE : EXPORT_LIMIT;
        boolean hasMore = true;
        long totalStart = System.currentTimeMillis();

        while (hasMore) {
            long batchStart = System.currentTimeMillis();
            int batchSize = Math.min(EXPORT_BATCH_SIZE, limit - exported);
            if (batchSize <= 0) break;

            List<Map<String, Object>> batch = fetchApprovalHistoryExportBatch(request, lastId, batchSize);

            if (batch.isEmpty()) {
                hasMore = false;
                break;
            }

            for (Map<String, Object> item : batch) {
                writer.println(toCsvRow(item));
                exported++;
            }
            writer.flush();

            logger.info("Approval History CSV batch exported: {} rows | lastId={} | took {} ms",
                    batch.size(), lastId, System.currentTimeMillis() - batchStart);

            if (batch.size() < batchSize || exported >= limit) {
                hasMore = false;
            } else {
                lastId = (Integer) batch.get(batch.size() - 1).get("WORKFLOW_ID");
            }
        }
        logger.info("Approval History CSV export completed. Total: {} rows | Duration: {} ms",
                exported, System.currentTimeMillis() - totalStart);
    }

    /**
     * Stream approval history to Excel export
     */
    public void streamHistoryExportToExcel(ApprovalRequest request, OutputStream out) throws Exception {
        logger.info("Approval History Excel export started. ExportAll: {}", request.isExportAll());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SXSSFWorkbook workbook = new SXSSFWorkbook(500);
        workbook.setCompressTempFiles(true);

        try {
            SXSSFSheet sheet = workbook.createSheet("ApprovalHistory");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(EXPORT_HEADERS[i]);
            }

            int rowIdx = 1;
            Integer lastId = null;
            int exported = 0;
            int limit = request.isExportAll() ? Integer.MAX_VALUE : EXPORT_LIMIT;
            boolean hasMore = true;
            long totalStart = System.currentTimeMillis();

            while (hasMore) {
                long batchStart = System.currentTimeMillis();
                int batchSize = Math.min(EXPORT_BATCH_SIZE, limit - exported);
                if (batchSize <= 0) break;

                List<Map<String, Object>> batch = fetchApprovalHistoryExportBatch(request, lastId, batchSize);

                if (batch.isEmpty()) {
                    hasMore = false;
                    break;
                }

                for (Map<String, Object> item : batch) {
                    Row row = sheet.createRow(rowIdx++);
                    fillExcelRow(row, item);
                    exported++;
                }

                logger.info("Approval History Excel batch exported: {} rows | lastId={} | took {} ms",
                        batch.size(), lastId, System.currentTimeMillis() - batchStart);

                if (batch.size() < batchSize || exported >= limit) {
                    hasMore = false;
                } else {
                    lastId = (Integer) batch.get(batch.size() - 1).get("WORKFLOW_ID");
                }
            }

            workbook.write(out);
            out.flush();
            logger.info("Approval History Excel export completed. Total: {} rows | Duration: {} ms",
                    exported, System.currentTimeMillis() - totalStart);

        } finally {
            workbook.dispose();
            try { workbook.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Fetch approval history export batch using cursor pagination
     */
    private List<Map<String, Object>> fetchApprovalHistoryExportBatch(ApprovalRequest request, Integer lastId, int limit) {
        StringBuilder sql = new StringBuilder("""
        SELECT w.ID as WORKFLOW_ID, w.Object_Type, w.ASSET_ID, w.ORIGINAL_STATUS, w.UPDATED_STATUS,
               w.PROCESS_ID, w.INSERTEDBY as REQUESTER, w.CHANGEDBY as UPDATER, w.COMMENTS,
               w.INSERTDATE, w.CHANGEDATE,
               fr.siteId as WAREHOUSE_ID, fr.assetSerialNumber as ASSET_SERIAL_NUMBER
        FROM tb_WF_Financial_Approval_Request w
        LEFT JOIN tb_FinancialReport fr
            ON (fr.assetName = w.ASSET_ID OR fr.assetSerialNumber = w.ASSET_ID)
        WHERE (LOWER(w.UPDATED_STATUS) = 'approved' OR LOWER(w.UPDATED_STATUS) = 'rejected')
        """);

        List<Object> params = new ArrayList<>();
        buildApprovalHistoryFilters(sql, params, request);

        // Cursor pagination - using ID ASC for stability
        if (lastId != null && lastId > 0) {
            sql.append(" AND w.ID > ?");
            params.add(lastId);
        }

        sql.append(" ORDER BY w.ID ASC LIMIT ?");
        params.add(limit);

        Query query = entityManager.createNativeQuery(sql.toString());

        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return convertToMapList(rows);
    }



    // ====================== CONSTANTS ======================
    private static final int EXPORT_BATCH_SIZE = 100_000;
    private static final int EXPORT_LIMIT = 1_000_000; // Adjust based on your needs

    private static final String[] EXPORT_HEADERS = {
            "WORKFLOW_ID", "OBJECT_TYPE", "ASSET_ID", "ORIGINAL_STATUS", "UPDATED_STATUS",
            "PROCESS_ID", "REQUESTER", "UPDATER", "COMMENTS", "INSERTDATE", "CHANGEDATE",
            "WAREHOUSE_ID", "ASSET_SERIAL_NUMBER"
    };

    public PageResult<Map<String, Object>> multiFilterSearch(ApprovalRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 100;

        List<Map<String, Object>> data = fetchApprovalBatchForPage(request, page * size, size);
        long totalElements = countApprovals(request);

        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setTotalElements(totalElements);
        result.setTotalPages((int) Math.ceil((double) totalElements / size));
        result.setPage(page);
        result.setSize(size);
        result.setdata(data);

        return result;
    }


    public void streamExportToCsv(ApprovalRequest request, PrintWriter writer) {
        logger.info("Approval CSV export started. ExportAll: {}", request.isExportAll());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        writer.println(String.join(",", EXPORT_HEADERS));
        writer.flush();

        Integer lastId = null;
        int exported = 0;
        int limit = request.isExportAll() ? Integer.MAX_VALUE : EXPORT_LIMIT;
        boolean hasMore = true;
        long totalStart = System.currentTimeMillis();

        while (hasMore) {
            long batchStart = System.currentTimeMillis();
            int batchSize = Math.min(EXPORT_BATCH_SIZE, limit - exported);
            if (batchSize <= 0) break;

            List<Map<String, Object>> batch = fetchApprovalExportBatch(request, lastId, batchSize);

            if (batch.isEmpty()) {
                hasMore = false;
                break;
            }

            for (Map<String, Object> item : batch) {
                writer.println(toCsvRow(item));
                exported++;
            }
            writer.flush();

            logger.info("Approval CSV batch exported: {} rows | lastId={} | took {} ms",
                    batch.size(), lastId, System.currentTimeMillis() - batchStart);

            if (batch.size() < batchSize || exported >= limit) {
                hasMore = false;
            } else {
                lastId = (Integer) batch.get(batch.size() - 1).get("WORKFLOW_ID");
            }
        }
        logger.info("Approval CSV export completed. Total: {} rows | Duration: {} ms",
                exported, System.currentTimeMillis() - totalStart);
    }

    public void streamExportToExcel(ApprovalRequest request, OutputStream out) throws Exception {
        logger.info("Approval Excel export started. ExportAll: {}", request.isExportAll());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SXSSFWorkbook workbook = new SXSSFWorkbook(500);
        workbook.setCompressTempFiles(true);

        try {
            SXSSFSheet sheet = workbook.createSheet("FinanceApprovals");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(EXPORT_HEADERS[i]);
            }

            int rowIdx = 1;
            Integer lastId = null;
            int exported = 0;
            int limit = request.isExportAll() ? Integer.MAX_VALUE : EXPORT_LIMIT;
            boolean hasMore = true;
            long totalStart = System.currentTimeMillis();

            while (hasMore) {
                long batchStart = System.currentTimeMillis();
                int batchSize = Math.min(EXPORT_BATCH_SIZE, limit - exported);
                if (batchSize <= 0) break;

                List<Map<String, Object>> batch = fetchApprovalExportBatch(request, lastId, batchSize);

                if (batch.isEmpty()) {
                    hasMore = false;
                    break;
                }

                for (Map<String, Object> item : batch) {
                    Row row = sheet.createRow(rowIdx++);
                    fillExcelRow(row, item);
                    exported++;
                }

                logger.info("Approval Excel batch exported: {} rows | lastId={} | took {} ms",
                        batch.size(), lastId, System.currentTimeMillis() - batchStart);

                if (batch.size() < batchSize || exported >= limit) {
                    hasMore = false;
                } else {
                    lastId = (Integer) batch.get(batch.size() - 1).get("WORKFLOW_ID");
                }
            }

            workbook.write(out);
            out.flush();
            logger.info("Approval Excel export completed. Total: {} rows | Duration: {} ms",
                    exported, System.currentTimeMillis() - totalStart);

        } finally {
            workbook.dispose();
            try { workbook.close(); } catch (Exception ignored) {}
        }
    }

    // ====================== ROW HELPERS ======================
    private String toCsvRow(Map<String, Object> item) {
        return String.join(",",
                csvSafe(item.get("WORKFLOW_ID")),
                csvSafe(item.get("OBJECT_TYPE")),
                csvSafe(item.get("ASSET_ID")),
                csvSafe(item.get("ORIGINAL_STATUS")),
                csvSafe(item.get("UPDATED_STATUS")),
                csvSafe(item.get("PROCESS_ID")),
                csvSafe(item.get("REQUESTER")),
                csvSafe(item.get("UPDATER")),
                csvSafe(item.get("COMMENTS")),
                csvSafe(item.get("INSERTDATE")),
                csvSafe(item.get("CHANGEDATE")),
                csvSafe(item.get("WAREHOUSE_ID")),
                csvSafe(item.get("ASSET_SERIAL_NUMBER"))
        );
    }

    private void fillExcelRow(Row row, Map<String, Object> item) {
        int col = 0;
        row.createCell(col++).setCellValue(safe(item.get("WORKFLOW_ID")));
        row.createCell(col++).setCellValue(safe(item.get("OBJECT_TYPE")));
        row.createCell(col++).setCellValue(safe(item.get("ASSET_ID")));
        row.createCell(col++).setCellValue(safe(item.get("ORIGINAL_STATUS")));
        row.createCell(col++).setCellValue(safe(item.get("UPDATED_STATUS")));
        row.createCell(col++).setCellValue(safe(item.get("PROCESS_ID")));
        row.createCell(col++).setCellValue(safe(item.get("REQUESTER")));
        row.createCell(col++).setCellValue(safe(item.get("UPDATER")));
        row.createCell(col++).setCellValue(safe(item.get("COMMENTS")));
        row.createCell(col++).setCellValue(safe(item.get("INSERTDATE")));
        row.createCell(col++).setCellValue(safe(item.get("CHANGEDATE")));
        row.createCell(col++).setCellValue(safe(item.get("WAREHOUSE_ID")));
        row.createCell(col++).setCellValue(safe(item.get("ASSET_SERIAL_NUMBER")));
    }

    private String csvSafe(Object val) {
        if (val == null) return "";
        String s = val.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String safe(Object val) {
        return val == null ? "" : val.toString();
    }

    // ====================== CORE BATCH FETCH FOR EXPORT (Cursor-based, ASC) ======================
    private List<Map<String, Object>> fetchApprovalExportBatch(ApprovalRequest request, Integer lastId, int limit) {
        StringBuilder sql = new StringBuilder("""
        SELECT w.ID as WORKFLOW_ID, w.Object_Type, w.ASSET_ID, w.ORIGINAL_STATUS, w.UPDATED_STATUS,
               w.PROCESS_ID, w.INSERTEDBY as REQUESTER, w.CHANGEDBY as UPDATER, w.COMMENTS,
               w.INSERTDATE, w.CHANGEDATE,
               fr.siteId as WAREHOUSE_ID, fr.assetSerialNumber as ASSET_SERIAL_NUMBER
        FROM tb_WF_Financial_Approval_Request w
        LEFT JOIN tb_FinancialReport fr 
            ON (fr.assetName = w.ASSET_ID OR fr.assetSerialNumber = w.ASSET_ID)
        WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();
        buildApprovalFilters(sql, params, request);

        // Cursor pagination - using ID ASC for stability
        if (lastId != null && lastId > 0) {
            sql.append(" AND w.ID > ?");
            params.add(lastId);
        }

        sql.append(" ORDER BY w.ID ASC LIMIT ?");
        params.add(limit);

        Query query = entityManager.createNativeQuery(sql.toString());

        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return convertToMapList(rows);
    }

    // ====================== COUNT FOR PAGINATION ======================
    private long countApprovals(ApprovalRequest request) {
        StringBuilder sql = new StringBuilder("""
        SELECT COUNT(*) 
        FROM tb_WF_Financial_Approval_Request w
        LEFT JOIN tb_FinancialReport fr 
            ON (fr.assetName = w.ASSET_ID OR fr.assetSerialNumber = w.ASSET_ID)
        WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        buildApprovalFilters(sql, params, request);

        Query query = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        return ((Number) query.getSingleResult()).longValue();
    }

    // ====================== PAGE FETCH (for UI pagination) ======================
    private List<Map<String, Object>> fetchApprovalBatchForPage(ApprovalRequest request, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
        SELECT w.ID as WORKFLOW_ID, w.Object_Type, w.ASSET_ID, w.ORIGINAL_STATUS, w.UPDATED_STATUS,
               w.PROCESS_ID, w.INSERTEDBY as REQUESTER, w.CHANGEDBY as UPDATER, w.COMMENTS,
               w.INSERTDATE, w.CHANGEDATE,
               fr.siteId as WAREHOUSE_ID, fr.assetSerialNumber as ASSET_SERIAL_NUMBER
        FROM tb_WF_Financial_Approval_Request w
        LEFT JOIN tb_FinancialReport fr 
            ON (fr.assetName = w.ASSET_ID OR fr.assetSerialNumber = w.ASSET_ID)
        WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        buildApprovalFilters(sql, params, request);

        sql.append(" ORDER BY w.ID DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        Query query = entityManager.createNativeQuery(sql.toString());

        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return convertToMapList(rows);
    }

    // ====================== BATCH FETCH FOR EXPORT (Cursor-based) ======================
    private List<Map<String, Object>> fetchApprovalBatch(ApprovalRequest request, Integer lastId, int limit) {
        StringBuilder sql = new StringBuilder("""
        SELECT w.ID as WORKFLOW_ID, w.Object_Type, w.ASSET_ID, w.ORIGINAL_STATUS, w.UPDATED_STATUS,
               w.PROCESS_ID, w.INSERTEDBY as REQUESTER, w.CHANGEDBY as UPDATER, w.COMMENTS,
               w.INSERTDATE, w.CHANGEDATE,
               fr.siteId as WAREHOUSE_ID, fr.assetSerialNumber as ASSET_SERIAL_NUMBER
        FROM tb_WF_Financial_Approval_Request w
        LEFT JOIN tb_FinancialReport fr 
            ON (fr.assetName = w.ASSET_ID OR fr.assetSerialNumber = w.ASSET_ID)
        WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        buildApprovalFilters(sql, params, request);

        if (lastId != null && lastId > 0) {
            sql.append(" AND w.ID < ?");   // DESC order for cursor
            params.add(lastId);
        }

        sql.append(" ORDER BY w.ID DESC LIMIT ?");
        params.add(limit);

        Query query = entityManager.createNativeQuery(sql.toString());

        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return convertToMapList(rows);
    }


    // ====================== COMMON FILTER BUILDER (FIXED) ======================
    private void buildApprovalFilters(StringBuilder sql, List<Object> params, ApprovalRequest request) {
        // 1. Multi-filters (new flexible way)
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            for (ApprovalRequest.Filter f : request.getFilters()) {
                if (f.getColumn() == null || f.getColumn().trim().isEmpty()) continue;

                String col = f.getColumn().trim();
                String value = f.getValue() != null ? f.getValue().trim() : "";
                if (value.isEmpty()) continue;

                String operator = f.getOperator() != null ? f.getOperator().name() : "CONTAINS";

                appendFilterCondition(sql, params, col, value, operator);
            }
        }

        // 2. Legacy / backward compatibility filters
        appendSimpleFilter(sql, params, "w.Object_Type", request.getObjectType());
        appendSimpleFilter(sql, params, "w.ASSET_ID", request.getAssetId());
        appendSimpleFilter(sql, params, "w.ORIGINAL_STATUS", request.getOriginalStatus());
        appendSimpleFilter(sql, params, "w.UPDATED_STATUS", request.getUpdatedStatus());

        if (request.getProcessId() != null && !request.getProcessId().isBlank()) {
            try {
                sql.append(" AND w.PROCESS_ID = ?");
                params.add(Integer.parseInt(request.getProcessId()));
            } catch (Exception ignored) {}
        }

        if (request.getTeam() != null && !request.getTeam().isBlank()) {
            String status = switch (request.getTeam().trim()) {
                case "Financial L1" -> "Pending L1 Approval";
                case "Financial L2" -> "Pending L2 Approval";
                case "Financial L3" -> "Pending L3 Approval";
                default -> null;
            };
            if (status != null) {
                sql.append(" AND LOWER(w.UPDATED_STATUS) = LOWER(?)");
                params.add(status);
            }
        }

        if (request.getStartDate() != null && !request.getStartDate().isBlank()) {
            sql.append(" AND w.INSERTDATE >= ?");
            params.add(request.getStartDate() + " 00:00:00");
        }
        if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
            sql.append(" AND w.INSERTDATE <= ?");
            params.add(request.getEndDate() + " 23:59:59");
        }

        if (request.getObjectStatus() != null && !request.getObjectStatus().isBlank()) {
            sql.append("""
            AND EXISTS (
                SELECT 1 FROM tb_FinancialReport fr2
                WHERE (fr2.assetName = w.ASSET_ID OR fr2.assetSerialNumber = w.ASSET_ID)
                  AND LOWER(fr2.statusFlag) = LOWER(?)
            )
            """);
            params.add(request.getObjectStatus());
        }
    }



    private void appendSimpleFilter(StringBuilder sql, List<Object> params, String column, String value) {
        if (value != null && !value.trim().isBlank()) {
            sql.append(" AND LOWER(").append(column).append(") = LOWER(?)");
            params.add(value.trim());
        }
    }

    private void appendFilterCondition(StringBuilder sql, List<Object> params,
                                               String column, String value, String operator) {

        String colExpression = getColumnExpression(column);  // handles joined columns

        if (colExpression == null) {
            logger.warn("Invalid filter column: {}", column);
            return;
        }

        switch (operator.toUpperCase()) {
            case "EQUALS":
                sql.append(" AND LOWER(").append(colExpression).append(") = LOWER(?)");
                params.add(value);
                break;

            case "CONTAINS":
                sql.append(" AND LOWER(").append(colExpression).append(") LIKE LOWER(?)");
                params.add("%" + value + "%");
                break;

            case "STARTS_WITH":
                sql.append(" AND LOWER(").append(colExpression).append(") LIKE LOWER(?)");
                params.add(value + "%");
                break;

            case "ENDS_WITH":
                sql.append(" AND LOWER(").append(colExpression).append(") LIKE LOWER(?)");
                params.add("%" + value);
                break;

            case "IS_EMPTY":
                sql.append(" AND (").append(colExpression).append(" IS NULL OR TRIM(")
                        .append(colExpression).append(") = '')");
                break;

            case "IS_NOT_EMPTY":
                sql.append(" AND ").append(colExpression).append(" IS NOT NULL AND TRIM(")
                        .append(colExpression).append(") != ''");
                break;

            default:
                // fallback to EQUALS
                sql.append(" AND LOWER(").append(colExpression).append(") = LOWER(?)");
                params.add(value);
        }
    }

    private boolean isValidApprovalColumn(String column) {
        Set<String> allowed = Set.of(
                "Object_Type", "ASSET_ID", "ORIGINAL_STATUS", "UPDATED_STATUS",
                "PROCESS_ID", "INSERTEDBY", "CHANGEDBY", "COMMENTS"
        );
        return allowed.contains(column);
    }

    private String getColumnExpression(String column) {
        return switch (column.trim().toUpperCase()) {
            // Main table columns
            case "WORKFLOW_ID", "ID" -> "w.ID";
            case "OBJECT_TYPE" -> "w.Object_Type";
            case "ASSET_ID" -> "w.ASSET_ID";
            case "ORIGINAL_STATUS" -> "w.ORIGINAL_STATUS";
            case "UPDATED_STATUS" -> "w.UPDATED_STATUS";
            case "PROCESS_ID" -> "w.PROCESS_ID";
            case "REQUESTER", "INSERTEDBY" -> "w.INSERTEDBY";
            case "UPDATER", "CHANGEDBY" -> "w.CHANGEDBY";
            case "COMMENTS" -> "w.COMMENTS";
            case "INSERTDATE" -> "w.INSERTDATE";
            case "CHANGEDATE" -> "w.CHANGEDATE";

            // Joined table columns
            case "WAREHOUSE_ID", "SITEID" -> "fr.siteId";
            case "ASSET_SERIAL_NUMBER" -> "fr.assetSerialNumber";

            default -> null; // invalid column
        };
    }
    // ====================== ROW CONVERTER ======================
    private List<Map<String, Object>> convertToMapList(List<Object[]> rows) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> result = new ArrayList<>();

        for (Object[] row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("WORKFLOW_ID", row[0]);
            item.put("OBJECT_TYPE", row[1]);
            item.put("ASSET_ID", row[2]);
            item.put("ORIGINAL_STATUS", row[3]);
            item.put("UPDATED_STATUS", row[4]);
            item.put("PROCESS_ID", row[5]);
            item.put("REQUESTER", row[6]);
            item.put("UPDATER", row[7]);
            item.put("COMMENTS", row[8]);
            item.put("INSERTDATE", row[9] != null ? sdf.format(row[9]) : "");
            item.put("CHANGEDATE", row[10] != null ? sdf.format(row[10]) : "");
            item.put("WAREHOUSE_ID", row[11]);
            item.put("ASSET_SERIAL_NUMBER", row[12]);
            result.add(item);
        }
        return result;
    }
}