package com.zain.bh.alm.financials.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.dto.ApprovalWorkflowDTO;
import com.zain.bh.alm.financials.entity.FinancialReport;

@Service
public class FinancialReportDeletionService {

    private final FinancialReportService financialReportService;
    private final ApprovalWorkflowService approvalWorkflowService;

    public FinancialReportDeletionService(FinancialReportService financialReportService,
                                           ApprovalWorkflowService approvalWorkflowService) {
        this.financialReportService = financialReportService;
        this.approvalWorkflowService = approvalWorkflowService;
    }

    public Map<String, Object> deleteReport(String serialNumber, String username) {
        Map<String, Object> response = new HashMap<>();
        
        Optional<FinancialReport> existingReportOpt = financialReportService.findBySerialNumber(serialNumber);
        if (!existingReportOpt.isPresent()) {
            response.put("message", "Financial report with serial number " + serialNumber + " not found");
            response.put("status", "NOT_FOUND");
            return response;
        }

        FinancialReport existingReport = existingReportOpt.get();
        existingReport.setChangedBy(username);
        existingReport.setChangeDate(new Date());

        ApprovalWorkflowDTO workflow = approvalWorkflowService.createDeletionWorkflow(existingReport, existingReport.getNodeType(), "pending deletion");
        financialReportService.save(existingReport);

        response.put("message", "Financial report deletion submitted for approval");
        response.put("workflowId", workflow.getId());
        response.put("status", "SUCCESS");
        return response;
    }
}
