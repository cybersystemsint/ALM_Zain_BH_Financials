package com.zain.bh.alm.financials.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.dto.ApprovalWorkflowDTO;
import com.zain.bh.alm.financials.entity.FinancialReport;

@Service
public class FinanceApprovalFilterService {

	private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private final FinancialReportService financialReportService;

	public FinanceApprovalFilterService(FinancialReportService financialReportService) {
		this.financialReportService = financialReportService;
	}

	public String getExpectedStatusForTeam(String team) {
		switch (team) {
		case "Financial L1":
			return "Pending L1 Approval";
		case "Financial L2":
			return "Pending L2 Approval";
		case "Financial L3":
			return "Pending L3 Approval";
		default:
			return null;
		}
	}

	public List<ApprovalWorkflowDTO> filterWorkflows(List<ApprovalWorkflowDTO> workflows, String expectedStatus,
			String processId, String originalStatus, String objectType, String assetId, String objectStatus, Date start,
			Date end) {
		return workflows.stream().filter(workflow -> expectedStatus.equals(workflow.getUpdatedStatus()))
				.filter(workflow -> processId == null || processId.equals(String.valueOf(workflow.getProcessId())))
				.filter(workflow -> originalStatus == null || originalStatus.equals(workflow.getOriginalStatus()))
				.filter(workflow -> matchesFilters(workflow, objectType, assetId, objectStatus, start, end))
				.collect(Collectors.toList());
	}

	private boolean matchesFilters(ApprovalWorkflowDTO workflow, String objectType, String assetId,
			String objectStatus, Date start, Date end) {
		FinancialReport report = getFinancialReport(workflow.getAssetId());

		boolean matchesObjectType = objectType == null || objectType.equals(workflow.getObjectType());
		boolean matchesAssetId = assetId == null || assetId.equals(workflow.getAssetId());
		boolean matchesObjectStatus = objectStatus == null
				|| (report != null && objectStatus.equals(report.getStatusFlag()));
		boolean matchesStartDate = start == null
				|| (workflow.getInsertDate() != null && !workflow.getInsertDate().before(start));
		boolean matchesEndDate = end == null
				|| (workflow.getChangeDate() != null && !workflow.getChangeDate().after(end));

		return matchesObjectType && matchesAssetId && matchesObjectStatus && matchesStartDate && matchesEndDate;
	}

	public List<Map<String, Object>> mapWorkflowsToItems(List<ApprovalWorkflowDTO> workflows) {
		return workflows.stream().map(this::mapWorkflowToItem).collect(Collectors.toList());
	}

	private Map<String, Object> mapWorkflowToItem(ApprovalWorkflowDTO workflow) {
		Map<String, Object> item = new HashMap<>();
		FinancialReport report = getFinancialReport(workflow.getAssetId());

		item.put("OBJECT_TYPE", workflow.getObjectType());
		item.put("ASSET_ID", workflow.getAssetId());
		item.put("ORIGINAL_STATUS", workflow.getOriginalStatus());
		item.put("UPDATED_STATUS", workflow.getUpdatedStatus());
		item.put("REQUESTER", workflow.getInsertedBy());
		item.put("UPDATER", workflow.getChangedBy());
		item.put("PROCESS_ID", workflow.getProcessId());
		item.put("COMMENTS", workflow.getComments());
		item.put("INSERTDATE", workflow.getInsertDate() != null ? SDF.format(workflow.getInsertDate()) : "");
		item.put("CHANGEDATE", workflow.getChangeDate() != null ? SDF.format(workflow.getChangeDate()) : "");
		item.put("WAREHOUSE_ID", report != null ? report.getSiteId() : null);
		item.put("WORKFLOW_ID", workflow.getId());
		item.put("ASSET_SERIAL_NUMBER", report != null ? report.getAssetSerialNumber() : null);
		return item;
	}

	public List<ApprovalWorkflowDTO> searchWorkflows(List<ApprovalWorkflowDTO> workflows, String query) {
		String queryLower = query.toLowerCase().trim();
		return workflows.stream().filter(workflow -> matchesSearchQuery(workflow, queryLower))
				.collect(Collectors.toList());
	}

	private boolean matchesSearchQuery(ApprovalWorkflowDTO workflow, String queryLower) {
		boolean matchesAssetId = workflow.getAssetId() != null
				&& workflow.getAssetId().toLowerCase().contains(queryLower);
		boolean matchesSerial = false;

		if (workflow.getAssetId() != null) {
			FinancialReport report = getFinancialReport(workflow.getAssetId());
			if (report != null) {
				matchesSerial = report.getAssetSerialNumber() != null
						&& report.getAssetSerialNumber().toLowerCase().contains(queryLower);
			}
		}
		return matchesAssetId || matchesSerial;
	}

	private FinancialReport getFinancialReport(String assetId) {
		if (assetId == null)
			return null;
		Optional<FinancialReport> reportOpt = financialReportService.findByAssetName(assetId);
		if (!reportOpt.isPresent()) {
			reportOpt = financialReportService.findBySerialNumber(assetId);
		}
		return reportOpt.orElse(null);
	}
}
