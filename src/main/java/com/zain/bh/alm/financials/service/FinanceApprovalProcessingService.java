package com.zain.bh.alm.financials.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.dto.ApprovalWorkflowDTO;

@Service
public class FinanceApprovalProcessingService {

	private static final Logger LOGGER = LoggerFactory.getLogger(FinanceApprovalProcessingService.class);

	private final ApprovalWorkflowService approvalWorkflowService;

	public FinanceApprovalProcessingService(ApprovalWorkflowService approvalWorkflowService) {
		this.approvalWorkflowService = approvalWorkflowService;
	}

	public ProcessingResult processWorkflows(List<Integer> workflowIds, String comment, String username,
			WorkflowAction action) {
		List<Integer> processedWorkflows = new ArrayList<>();
		List<String> failedWorkflows = new ArrayList<>();

		for (Integer workflowId : workflowIds) {
			Optional<ApprovalWorkflowDTO> workflowOpt = approvalWorkflowService.findById(workflowId);
			if (!workflowOpt.isPresent()) {
				failedWorkflows.add("Workflow " + workflowId + " not found");
				LOGGER.warn("Workflow ID {} not found", workflowId);
				continue;
			}

			ApprovalWorkflowDTO workflow = workflowOpt.get();
			String currentStatus = workflow.getUpdatedStatus();

			if ("REJECTED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
				failedWorkflows.add("Workflow " + workflowId + " is already " + currentStatus);
				LOGGER.info("Workflow ID {} skipped: already {}", workflowId, currentStatus);
				continue;
			}

			if (workflow.getAssetId() == null || workflow.getAssetId().trim().isEmpty()) {
				failedWorkflows.add("Workflow " + workflowId + ": ASSET_ID is null or empty");
				LOGGER.warn("Workflow ID {} has null or empty ASSET_ID", workflowId);
				continue;
			}

			try {
				boolean success = executeAction(workflowId, comment, username, action);
				if (success) {
					processedWorkflows.add(workflowId);
					LOGGER.info("Workflow ID {} {} successfully", workflowId, action.getPastTense());
				} else {
					failedWorkflows.add("Workflow " + workflowId + ": Failed to " + action.getVerb()
							+ ", check ASSET_ID " + workflow.getAssetId());
					LOGGER.warn("Workflow ID {} failed to {} for ASSET_ID {}", workflowId, action.getVerb(),
							workflow.getAssetId());
				}
			} catch (Exception e) {
				failedWorkflows.add("Workflow " + workflowId + ": " + e.getMessage());
				LOGGER.error("Error {} workflow ID {}: {}", action.getVerb(), workflowId, e.getMessage(), e);
			}
		}

		return new ProcessingResult(processedWorkflows, failedWorkflows);
	}

	private boolean executeAction(Integer workflowId, String comment, String username, WorkflowAction action) {
		switch (action) {
		case APPROVE:
			return approvalWorkflowService.approveWorkflow(workflowId, comment, username);
		case REJECT:
			return approvalWorkflowService.rejectWorkflow(workflowId, comment, username);
		case CANCEL:
			return approvalWorkflowService.cancelWorkflow(workflowId, comment, username);
		default:
			throw new IllegalArgumentException("Unknown action: " + action);
		}
	}

	public enum WorkflowAction {
		APPROVE("approve", "approved"), REJECT("reject", "rejected"), CANCEL("cancel", "cancelled");

		private final String verb;
		private final String pastTense;

		WorkflowAction(String verb, String pastTense) {
			this.verb = verb;
			this.pastTense = pastTense;
		}

		public String getVerb() {
			return verb;
		}

		public String getPastTense() {
			return pastTense;
		}
	}

	public static class ProcessingResult {
		private final List<Integer> processedWorkflows;
		private final List<String> failedWorkflows;

		public ProcessingResult(List<Integer> processedWorkflows, List<String> failedWorkflows) {
			this.processedWorkflows = processedWorkflows;
			this.failedWorkflows = failedWorkflows;
		}

		public List<Integer> getProcessedWorkflows() {
			return processedWorkflows;
		}

		public List<String> getFailedWorkflows() {
			return failedWorkflows;
		}

		public String getMessage(String action) {
			String message = processedWorkflows.isEmpty() ? "No workflows " + action
					: "Successfully " + action + " " + processedWorkflows.size() + " item(s)";
			if (!failedWorkflows.isEmpty()) {
				message += ". Failed: " + String.join("; ", failedWorkflows);
			}
			return message;
		}
	}
}
