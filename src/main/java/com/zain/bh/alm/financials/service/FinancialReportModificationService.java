package com.zain.bh.alm.financials.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zain.bh.alm.financials.dto.ApprovalWorkflowDTO;
import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.repository.FinancialReportRepository;

@Service
public class FinancialReportModificationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(FinancialReportModificationService.class);

	private final FinancialReportRepository financialReportRepository;
	private final ApprovalWorkflowService approvalWorkflowService;
	private final FinancialReportStateService stateService;
	private final ObjectMapper objectMapper;

	public FinancialReportModificationService(FinancialReportRepository financialReportRepository,
			ApprovalWorkflowService approvalWorkflowService, FinancialReportStateService stateService,
			ObjectMapper objectMapper) {
		this.financialReportRepository = financialReportRepository;
		this.approvalWorkflowService = approvalWorkflowService;
		this.stateService = stateService;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public Map<String, Object> modifyReport(String identifier, FinancialReport updatedReport) {
		Map<String, Object> response = new HashMap<>();

		Optional<FinancialReport> existingReportOpt = financialReportRepository.findByAssetSerialNumber(identifier);
		if (!existingReportOpt.isPresent()) {
			existingReportOpt = financialReportRepository.findByAssetName(identifier);
		}

		if (!existingReportOpt.isPresent()) {
			LOGGER.warn("Financial report not found for identifier: {}", identifier);
			response.put("message", "Financial report not found for identifier: " + identifier);
			response.put("status", "NOT_FOUND");
			return response;
		}

		FinancialReport existingReport = existingReportOpt.get();

		String assetId = existingReport.getAssetName() != null && !existingReport.getAssetName().trim().isEmpty()
				? existingReport.getAssetName()
				: existingReport.getAssetSerialNumber();
		List<ApprovalWorkflowDTO> existingWorkflows = approvalWorkflowService.findByAssetId(assetId);
		if (existingWorkflows.stream().anyMatch(w -> w.getUpdatedStatus().startsWith("Pending"))) {
			LOGGER.warn("Pending workflow exists for asset ID: {}", assetId);
			response.put("message",
					"Cannot modify: Asset with identifier " + identifier + " has pending workflow approvals");
			response.put("status", "CONFLICT");
			return response;
		}

		try {
			stateService.saveOriginalState(existingReport, objectMapper);
		} catch (Exception e) {
			LOGGER.error("Failed to serialize original state for identifier: {}", identifier, e);
			response.put("message", "Failed to serialize original state: " + e.getMessage());
			response.put("status", "ERROR");
			return response;
		}

		stateService.updateReportFields(existingReport, updatedReport);

		existingReport.setFinancialApprovalStatus("Pending L1 Approval");
		existingReport.setChangedBy(
				updatedReport.getChangedBy() != null ? updatedReport.getChangedBy() : existingReport.getChangedBy());
		existingReport.setChangeDate(new Date());

		FinancialReport savedReport = financialReportRepository.save(existingReport);

		ApprovalWorkflowDTO workflow;
		if (savedReport.getInitialCost() == null || savedReport.getInitialCost().compareTo(BigDecimal.ZERO) == 0) {
			workflow = approvalWorkflowService.createDeletionWorkflow(savedReport, savedReport.getNodeType(),
					"pending deletion");
		} else {
			workflow = approvalWorkflowService.createApprovalWorkflow(savedReport, savedReport.getNodeType(),
					"pending modification");
		}

		LOGGER.info("Financial report {} modified and submitted for approval with workflow ID: {}", savedReport.getId(),
				workflow.getId());
		response.put("message", "Financial report modification submitted for approval");
		response.put("report", savedReport);
		response.put("workflowId", workflow.getId());
		response.put("status", "SUCCESS");
		return response;
	}
}
