package com.zain.bh.alm.financials.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.AuditLog;
import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.entity.WFFinancialApprovalRequest;
import com.zain.bh.alm.financials.repository.AuditLogRepository;
import com.zain.bh.alm.financials.repository.FinancialReportRepository;
import com.zain.bh.alm.financials.repository.WFFinancialApprovalRequestRepository;

@Service
public class AssetStatusService {

    private final FinancialReportRepository financialReportRepository;
    private final WFFinancialApprovalRequestRepository approvalWorkflowRepository;
    private final AuditLogRepository auditLogRepository;
    private final AssetProcessingService assetProcessingService;

    public AssetStatusService(FinancialReportRepository financialReportRepository,
                              WFFinancialApprovalRequestRepository approvalWorkflowRepository,
                              AuditLogRepository auditLogRepository,
                              AssetProcessingService assetProcessingService) {
        this.financialReportRepository = financialReportRepository;
        this.approvalWorkflowRepository = approvalWorkflowRepository;
        this.auditLogRepository = auditLogRepository;
        this.assetProcessingService = assetProcessingService;
    }

    public Map<String, Object> syncSpecificAsset(String identifier) {
        Map<String, Object> response = new HashMap<>();
        Optional<FinancialReport> frAsset = financialReportRepository.findByAssetSerialNumber(identifier);

        if (frAsset.isPresent()) {
            FinancialReport asset = frAsset.get();
            assetProcessingService.processFRAsset(asset);
            response.put("status", "success");
            response.put("message", "Asset synchronization completed");
            response.put("assetDetails", mapAssetDetails(asset));
        } else {
            String assetType = assetProcessingService.determineAssetType(identifier);
            response.put("status", "warning");
            response.put("message", "Asset not found in financial report, mapped to unmapped inventory");
            response.put("serialNumber", identifier);
            response.put("assetType", assetType);
        }
        return response;
    }

    public Map<String, Object> checkAssetStatus(String identifier) {
        Map<String, Object> response = new HashMap<>();
        Optional<FinancialReport> frAsset = financialReportRepository.findByAssetSerialNumber(identifier);

        if (frAsset.isPresent()) {
            FinancialReport asset = frAsset.get();
            assetProcessingService.processFRAsset(asset);
            response.put("status", "success");
            response.put("foundIn", "FinancialReport");
            response.put("assetDetails", mapAssetDetails(asset));

            List<WFFinancialApprovalRequest> workflows = approvalWorkflowRepository
                    .findByAssetId(String.valueOf(asset.getId()));
            if (!workflows.isEmpty()) {
                response.put("workflows", workflows);
            }

            List<AuditLog> auditLogs = auditLogRepository.findByAssetIdOrSerialNumber(String.valueOf(asset.getId()),
                    asset.getAssetSerialNumber(), PageRequest.of(0, 10, Sort.by("changeDate").descending()));
            response.put("recentAuditLogs", auditLogs);
        } else {
            response.put("status", "error");
            response.put("message", "Asset not found in Financial Report, check unmapped inventory");
        }
        return response;
    }

    public Map<String, Object> mapAssetDetails(FinancialReport asset) {
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
