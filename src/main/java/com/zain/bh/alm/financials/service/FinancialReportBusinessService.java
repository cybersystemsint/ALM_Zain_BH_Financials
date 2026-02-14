package com.zain.bh.alm.financials.service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.FinancialReport;

@Service
public class FinancialReportBusinessService {

	private final FinancialReportService financialReportService;

	public FinancialReportBusinessService(FinancialReportService financialReportService) {
		this.financialReportService = financialReportService;
	}

	public void uploadFinancialReports(JSONArray jsonArray) throws ParseException {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);
			long recordNo = Long.parseLong(jsonObject.getString("recordNo"));
			String siteId = jsonObject.getString("siteId");

			if (recordNo == 0) {
				createNewFinancialReport(jsonObject, format, siteId);
			} else {
				updateExistingFinancialReport(jsonObject, format, siteId);
			}
		}
	}

	public JSONArray getFinancialReports(String siteId) {
		List<FinancialReport> reports = siteId != null && !siteId.trim().isEmpty()
				? financialReportService.findByAssetName(siteId).map(Collections::singletonList)
						.orElse(Collections.emptyList())
				: financialReportService.findAll();

		JSONArray response = new JSONArray();
		for (FinancialReport report : reports) {
			response.put(buildFinancialReportJson(report));
		}
		return response;
	}

	private JSONObject buildFinancialReportJson(FinancialReport report) {
		JSONObject obj = new JSONObject();
		obj.put("id", report.getId());
		obj.put("serialNumber", report.getAssetSerialNumber());
		obj.put("tag", report.getTag());
		obj.put("siteId", report.getSiteId());
		obj.put("assetType", report.getAssetType());
		obj.put("nodeType", report.getNodeType());
		obj.put("installationDate", report.getInstallationDate());
		obj.put("initialCost", report.getInitialCost());
		obj.put("salvageValue", report.getSalvageValue());
		obj.put("poNumber", report.getPoNumber());
		obj.put("poDate", report.getPoDate());
		obj.put("faCategory", report.getFaCategory());
		obj.put("L1", report.getL1());
		obj.put("L2", report.getL2());
		obj.put("L3", report.getL3());
		obj.put("L4", report.getL4());
		obj.put("accDepreciationCode", report.getAccumulatedDepreciationCode());
		obj.put("depreciationCode", report.getDepreciationCode());
		obj.put("usefulLife", report.getUsefulLifeMonths());
		obj.put("vendorName", report.getVendorName());
		obj.put("vendorNumber", report.getVendorNumber());
		obj.put("projectNumber", report.getProjectNumber());
		obj.put("dateOfService", report.getDateOfService());
		obj.put("oldFaCategory", report.getOldFarCategory());
		obj.put("costCenterData", report.getCostCenterData());
		obj.put("adjustment", report.getAdjustment());
		obj.put("invoiceNumber", report.getInvoiceNumber());
		obj.put("taskId", report.getTaskId());
		obj.put("poLineNumber", report.getPoLineNumber());
		obj.put("monthlyDepreciationAmt",
				report.getMonthlyDepreciationAmount() != null ? report.getMonthlyDepreciationAmount() : BigDecimal.ZERO);
		obj.put("accumulatedDepreciationAmt",
				report.getAccumulatedDepreciation() != null ? report.getAccumulatedDepreciation() : BigDecimal.ZERO);
		obj.put("netCost", report.getNetCost() != null ? report.getNetCost() : BigDecimal.ZERO);
		obj.put("approvalStatus", report.getFinancialApprovalStatus());
		obj.put("retirementDate", report.getRetirementDate());
		return obj;
	}

	private void createNewFinancialReport(JSONObject json, SimpleDateFormat format, String siteId) throws ParseException {
		if (siteId.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing Asset Code detected");
		}

		Optional<FinancialReport> existing = financialReportService.findByAssetName(siteId);
		if (existing.isPresent()) {
			throw new IllegalArgumentException("Site ID already exists: " + siteId);
		}
		if (!json.has("dateOfService") || json.getString("dateOfService").trim().isEmpty()) {
			throw new IllegalArgumentException("Date of service cannot be empty");
		}
		if (!json.has("initialCost") || json.getDouble("initialCost") < 1) {
			throw new IllegalArgumentException("Invalid initial cost");
		}

		FinancialReport report = new FinancialReport();
		populateFinancialReport(report, json, format);
		report.setFinancialApprovalStatus("Created");
		financialReportService.save(report);
	}

	private void updateExistingFinancialReport(JSONObject json, SimpleDateFormat format, String siteId) throws ParseException {
		if (siteId.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing Asset Code detected");
		}

		Optional<FinancialReport> existing = financialReportService.findByAssetName(siteId);
		if (!existing.isPresent()) {
			throw new IllegalArgumentException("Site ID not found: " + siteId);
		}
		if (!json.has("dateOfService") || json.getString("dateOfService").trim().isEmpty()) {
			throw new IllegalArgumentException("Date of service cannot be empty");
		}
		if (!json.has("initialCost") || json.getDouble("initialCost") < 1) {
			throw new IllegalArgumentException("Invalid initial cost");
		}

		FinancialReport report = existing.get();
		populateFinancialReport(report, json, format);
		report.setFinancialApprovalStatus("Updated");
		financialReportService.save(report);
	}

	private void populateFinancialReport(FinancialReport report, JSONObject json, SimpleDateFormat format) throws ParseException {
		report.setAssetSerialNumber(json.getString("serialNumber"));
		report.setTag(json.getString("tag"));
		report.setSiteId(json.getString("siteId"));
		report.setAssetType(json.getString("assetType"));
		report.setNodeType(json.getString("nodeType"));
		report.setInstallationDate(String.valueOf(format.parse(json.getString("installationDate"))));
		report.setPoDate(String.valueOf(format.parse(json.getString("poDate"))));
		report.setDateOfService(String.valueOf(format.parse(json.getString("dateOfService"))));
		report.setInitialCost(BigDecimal.valueOf(json.getDouble("initialCost")));
		report.setSalvageValue(BigDecimal.valueOf(json.getDouble("salvageValue")));
		report.setAdjustment(BigDecimal.valueOf(json.getDouble("adjustment")));
		report.setFaCategory(json.getString("newFACategory"));
		report.setL1(json.getString("L1"));
		report.setL2(json.getString("L2"));
		report.setL3(json.getString("L3"));
		report.setL4(json.getString("L4"));
		report.setAccumulatedDepreciationCode(json.getString("accDepreciationCode"));
		report.setDepreciationCode(json.getString("depreciationCode"));
		report.setUsefulLifeMonths(json.getInt("userfulLife"));
		report.setVendorName(json.getString("vendorName"));
		report.setVendorNumber(json.getString("vendorNumber"));
		report.setProjectNumber(json.getString("projectNumber"));
		report.setOldFarCategory(json.getString("oldFACategory"));
		report.setCostCenterData(json.getString("costCenter"));
		report.setInvoiceNumber(json.getString("invoiceNumber"));
		report.setTaskId(json.getString("taskId"));
		report.setPoLineNumber(json.getString("poLineNumber"));
	}
}
