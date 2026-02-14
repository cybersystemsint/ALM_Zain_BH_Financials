package com.zain.bh.alm.financials.controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zain.bh.alm.financials.dto.ApprovalWorkflowDTO;
import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.service.ApprovalWorkflowService;
import com.zain.bh.alm.financials.service.FinanceApprovalFilterService;
import com.zain.bh.alm.financials.service.FinanceApprovalProcessingService;
import com.zain.bh.alm.financials.service.FinancialReportService;
import com.zain.bh.alm.financials.service.FinanceApprovalProcessingService.ProcessingResult;
import com.zain.bh.alm.financials.service.FinanceApprovalProcessingService.WorkflowAction;

@RestController
@RequestMapping("/finance-approval")
@CrossOrigin(origins = "*")
public class FinanceApprovalController {

	private static final Logger LOGGER = LoggerFactory.getLogger(FinanceApprovalController.class);

	private final FinancialReportService financialReportService;
	private final ApprovalWorkflowService approvalWorkflowService;
	private final FinanceApprovalFilterService filterService;
	private final FinanceApprovalProcessingService processingService;

	public FinanceApprovalController(FinancialReportService financialReportService,
			ApprovalWorkflowService approvalWorkflowService, FinanceApprovalFilterService filterService,
			FinanceApprovalProcessingService processingService) {
		this.financialReportService = financialReportService;
		this.approvalWorkflowService = approvalWorkflowService;
		this.filterService = filterService;
		this.processingService = processingService;
	}

	@GetMapping("/approvals")
	public ResponseEntity<Map<String, Object>> getFinanceApprovals(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size, @RequestParam String team,
			@RequestParam(required = false) String objectType, @RequestParam(required = false) String assetId,
			@RequestParam(required = false) String originalStatus,
			@RequestParam(required = false) String objectStatus, @RequestParam(required = false) String processId,
			@RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate) {
		try {
			String expectedStatus = filterService.getExpectedStatusForTeam(team);
			if (expectedStatus == null) {
				return new ResponseEntity<>(Collections.singletonMap("message", "Invalid team"),
						HttpStatus.BAD_REQUEST);
			}

			SimpleDateFormat sdfFilter = new SimpleDateFormat("yyyy-MM-dd");
			Date start = startDate != null ? sdfFilter.parse(startDate) : null;
			Date end = endDate != null ? sdfFilter.parse(endDate) : null;

			Page<ApprovalWorkflowDTO> workflowPage = approvalWorkflowService
					.findAll(PageRequest.of(page, size, Sort.by("insertDate").descending()));
			List<ApprovalWorkflowDTO> filteredWorkflows = filterService.filterWorkflows(workflowPage.getContent(),
					expectedStatus, processId, originalStatus, objectType, assetId, objectStatus, start, end);

			List<Map<String, Object>> items = filterService.mapWorkflowsToItems(filteredWorkflows);

			Map<String, Object> response = new HashMap<>();
			response.put("items", items);
			response.put("totalCount", filteredWorkflows.size());
			response.put("currentPage", page);
			response.put("totalPages", (int) Math.ceil((double) filteredWorkflows.size() / size));

			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (Exception e) {
			LOGGER.error("Error fetching finance approvals: {}", e.getMessage());
			return new ResponseEntity<>(
					Collections.singletonMap("message", "Error fetching approvals: " + e.getMessage()),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/approve")
	public ResponseEntity<Map<String, Object>> approveItems(@RequestBody Map<String, Object> requestBody,
			Principal principal) {
		return processWorkflowAction(requestBody, principal, WorkflowAction.APPROVE);
	}

	@PostMapping("/reject")
	public ResponseEntity<Map<String, Object>> rejectItems(@RequestBody Map<String, Object> requestBody,
			Principal principal) {
		return processWorkflowAction(requestBody, principal, WorkflowAction.REJECT);
	}

	@PostMapping("/cancel")
	public ResponseEntity<Map<String, Object>> cancelItems(@RequestBody Map<String, Object> requestBody,
			Principal principal) {
		return processWorkflowAction(requestBody, principal, WorkflowAction.CANCEL);
	}

	private ResponseEntity<Map<String, Object>> processWorkflowAction(Map<String, Object> requestBody,
			Principal principal, WorkflowAction action) {
		try {
			List<Integer> workflowIds = ((List<?>) requestBody.get("items")).stream()
					.map(id -> Integer.parseInt(id.toString())).collect(Collectors.toList());
			String comment = (String) requestBody.get("comment");
			String username = principal != null ? principal.getName() : "system";

			if (workflowIds.isEmpty()) {
				return new ResponseEntity<>(Collections.singletonMap("message", "No items selected"),
						HttpStatus.BAD_REQUEST);
			}

			ProcessingResult result = processingService.processWorkflows(workflowIds, comment, username, action);

			Map<String, Object> response = new HashMap<>();
			response.put("message", result.getMessage(action.getPastTense()));
			response.put("processedWorkflows", result.getProcessedWorkflows());
			if (!result.getFailedWorkflows().isEmpty()) {
				response.put("failedWorkflows", result.getFailedWorkflows());
			}

			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (Exception e) {
			LOGGER.error("Error {} items: {}", action.getVerb(), e.getMessage());
			return new ResponseEntity<>(
					Collections.singletonMap("message", "Error " + action.getVerb() + " items: " + e.getMessage()),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/modify")
	public ResponseEntity<Map<String, Object>> modifyFinancialReport(@RequestBody FinancialReport updatedReport,
			Principal principal) {
		try {
			if (updatedReport.getId() == null) {
				return new ResponseEntity<>(Collections.singletonMap("message", "Financial report ID is required"),
						HttpStatus.BAD_REQUEST);
			}

			Optional<FinancialReport> existingReportOpt = financialReportService
					.findById(Math.toIntExact(updatedReport.getId()));
			if (!existingReportOpt.isPresent()) {
				return new ResponseEntity<>(Collections.singletonMap("message", "Financial report not found"),
						HttpStatus.NOT_FOUND);
			}

			FinancialReport existingReport = existingReportOpt.get();
			String username = principal != null ? principal.getName() : "system";

			if (updatedReport.getInitialCost() != null) {
				existingReport.setInitialCost(updatedReport.getInitialCost());
			}
			if (updatedReport.getAssetSerialNumber() != null) {
				existingReport.setAssetSerialNumber(updatedReport.getAssetSerialNumber());
			}
			if (updatedReport.getSiteId() != null) {
				existingReport.setSiteId(updatedReport.getSiteId());
			}
			if (updatedReport.getWriteOffDate() != null) {
				existingReport.setWriteOffDate(updatedReport.getWriteOffDate());
			}
			existingReport.setChangedBy(username);
			existingReport.setChangeDate(new java.sql.Timestamp(System.currentTimeMillis()));

			String nodeType = existingReport.getNodeType() != null ? existingReport.getNodeType() : "default";

			if (existingReport.getInitialCost() != null
					&& existingReport.getInitialCost().compareTo(BigDecimal.ZERO) == 0) {
				approvalWorkflowService.createDeletionWorkflow(existingReport, nodeType, "pending deletion");
			} else {
				approvalWorkflowService.createApprovalWorkflow(existingReport, nodeType, "pending modification");
			}
			financialReportService.save(existingReport);

			Map<String, Object> response = new HashMap<>();
			response.put("message", "Financial report updated and workflow created successfully");
			response.put("reportId", existingReport.getId());
			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (Exception e) {
			LOGGER.error("Error modifying financial report: {}", e.getMessage());
			return new ResponseEntity<>(
					Collections.singletonMap("message", "Error modifying financial report: " + e.getMessage()),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@GetMapping("/search")
	public ResponseEntity<Map<String, Object>> searchFinanceApprovals(@RequestParam String query,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
		try {
			Page<ApprovalWorkflowDTO> workflowPage = approvalWorkflowService
					.findAll(PageRequest.of(page, size, Sort.by("insertDate").descending()));
			List<ApprovalWorkflowDTO> filteredWorkflows = filterService.searchWorkflows(workflowPage.getContent(),
					query);

			List<Map<String, Object>> items = filterService.mapWorkflowsToItems(filteredWorkflows);

			Map<String, Object> response = new HashMap<>();
			response.put("items", items);
			response.put("totalCount", filteredWorkflows.size());
			response.put("currentPage", page);
			response.put("totalPages", (int) Math.ceil((double) filteredWorkflows.size() / size));

			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (Exception e) {
			LOGGER.error("Error searching finance approvals: {}", e.getMessage());
			return new ResponseEntity<>(
					Collections.singletonMap("message", "Error searching approvals: " + e.getMessage()),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
