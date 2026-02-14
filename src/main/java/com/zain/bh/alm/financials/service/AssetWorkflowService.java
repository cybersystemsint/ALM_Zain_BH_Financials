package com.zain.bh.alm.financials.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.entity.WFFinancialApprovalRequest;
import com.zain.bh.alm.financials.repository.FinancialReportRepository;
import com.zain.bh.alm.financials.repository.WFFinancialApprovalRequestRepository;

@Service
public class AssetWorkflowService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AssetWorkflowService.class);

    private final WFFinancialApprovalRequestRepository approvalWorkflowRepository;
    private final FinancialReportRepository financialReportRepository;
    private final AuditLogService auditLogService;

    public AssetWorkflowService(WFFinancialApprovalRequestRepository approvalWorkflowRepository,
                                FinancialReportRepository financialReportRepository,
                                AuditLogService auditLogService) {
        this.approvalWorkflowRepository = approvalWorkflowRepository;
        this.financialReportRepository = financialReportRepository;
        this.auditLogService = auditLogService;
    }

    public void triggerApprovalWorkflow(FinancialReport asset, String originalStatus) {
        Optional<WFFinancialApprovalRequest> existingWorkflow = approvalWorkflowRepository
                .findByAssetIdAndUpdatedStatus(asset.getId().toString(), "Pending Addition");
        if (existingWorkflow.isPresent()) {
            LOGGER.info("Workflow already exists for asset {} with status Pending Addition",
                    asset.getAssetSerialNumber());
            return;
        }

        WFFinancialApprovalRequest workflow = new WFFinancialApprovalRequest();
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
        financialReportRepository.save(asset);

        LOGGER.info("Triggered approval workflow for asset {} with original status: {}", asset.getAssetSerialNumber(),
                originalStatus);
        auditLogService.logAudit(asset, asset.getStatusFlag(), originalStatus, "Workflow triggered: " + originalStatus);
    }

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
}
