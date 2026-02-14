package com.zain.bh.alm.financials.service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.FarReport;

@Service
public class FarReportBusinessService {

	private final FarReportService farReportService;
	private final JdbcTemplate jdbcTemplate;

	public FarReportBusinessService(FarReportService farReportService, JdbcTemplate jdbcTemplate) {
		this.farReportService = farReportService;
		this.jdbcTemplate = jdbcTemplate;
	}

	public Map<String, Object> getFarReports(String assetId, int page, int size) {
		page = Math.max(page, 0);
		size = Math.max(size, 0);

		String countSql = "SELECT COUNT(*) FROM tb_FarReport " + (!assetId.isEmpty() ? "WHERE assetId = ?" : "");
		int totalRecords = !assetId.isEmpty() 
			? jdbcTemplate.queryForObject(countSql, Integer.class, assetId)
			: jdbcTemplate.queryForObject(countSql, Integer.class);

		BigDecimal totalcost = jdbcTemplate.queryForObject("SELECT SUM(cost) FROM tb_FarReport", BigDecimal.class);
		BigDecimal totalNBV = jdbcTemplate.queryForObject("SELECT SUM(netCost) FROM tb_FarReport", BigDecimal.class);
		BigDecimal totalDepreciation = jdbcTemplate.queryForObject("SELECT SUM(accumulatedDepreciationAmt) FROM tb_FarReport", BigDecimal.class);

		String paginationSql = "";
		if (page != 0 || size != 0) {
			if (page == 1 && size == 3000) {
				size = totalRecords;
			}
			page = Math.max(page, 1);
			size = Math.max(size, 1);
			int offset = (page - 1) * size;
			paginationSql = " LIMIT " + size + " OFFSET " + offset;
		}

		String sql = "SELECT * FROM tb_FarReport " + (!assetId.isEmpty() ? "WHERE assetId = ?" : "") + paginationSql;
		
		List<Map<String, Object>> result = new ArrayList<>();
		if (!assetId.isEmpty()) {
			jdbcTemplate.query(sql, rs -> {
				Map<String, Object> row = new LinkedHashMap<>();
				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
				}
				result.add(row);
			}, assetId);
		} else {
			jdbcTemplate.query(sql, rs -> {
				Map<String, Object> row = new LinkedHashMap<>();
				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
				}
				result.add(row);
			});
		}

		Map<String, Object> response = new HashMap<>();
		response.put("data", result);
		response.put("totalRecords", totalRecords);
		response.put("currentPage", page);
		response.put("pageSize", size);
		response.put("totalcost", totalcost);
		response.put("totalNBV", totalNBV);
		response.put("totalDepreciation", totalDepreciation);
		response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

		return response;
	}

	public void uploadFarReports(JSONArray jsonArray) throws ParseException {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		LocalDateTime now = LocalDateTime.now();
		java.util.Date parsedDate = format.parse(now.toString());
		java.sql.Date newDate = new java.sql.Date(parsedDate.getTime());

		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);
			long recordNo = Long.parseLong(jsonObject.getString("recordNo"));
			String assetID = jsonObject.getString("assetId");

			if (recordNo == 0) {
				createNewFarReport(jsonObject, format, newDate, assetID);
			} else {
				updateExistingFarReport(jsonObject, format, assetID);
			}
		}
	}

	private void createNewFarReport(JSONObject jsonObject, SimpleDateFormat format, java.sql.Date newDate, String assetID) throws ParseException {
		if (assetID.isEmpty()) {
			throw new IllegalArgumentException("Missing Asset Code detected");
		}

		List<FarReport> existing = farReportService.findByAssetId(assetID);
		if (!existing.isEmpty()) {
			throw new IllegalArgumentException("AssetCode already exists: " + assetID);
		}
		if (jsonObject.getString("datePlacedInService").isEmpty()) {
			throw new IllegalArgumentException("Date of service cannot be empty");
		}
		if (jsonObject.getDouble("cost") < 1) {
			throw new IllegalArgumentException("Invalid initial cost");
		}

		FarReport report = new FarReport();
		populateFarReport(report, jsonObject, format);
		report.setRecordDatetime(newDate);
		report.setStatusFlag("New");
		farReportService.save(report);
	}

	private void updateExistingFarReport(JSONObject jsonObject, SimpleDateFormat format, String assetID) throws ParseException {
		if (assetID.isEmpty()) {
			throw new IllegalArgumentException("Missing Asset Code detected");
		}

		List<FarReport> existing = farReportService.findByAssetId(assetID);
		if (existing.isEmpty()) {
			throw new IllegalArgumentException("Asset ID not found: " + assetID);
		}
		if (jsonObject.getString("datePlacedInService").isEmpty()) {
			throw new IllegalArgumentException("Date of service cannot be empty");
		}
		if (jsonObject.getDouble("cost") < 1) {
			throw new IllegalArgumentException("Invalid initial cost");
		}

		FarReport report = existing.get(0);
		populateFarReport(report, jsonObject, format);
		report.setStatusFlag("Existing");
		farReportService.save(report);
	}

	private void populateFarReport(FarReport report, JSONObject json, SimpleDateFormat format) throws ParseException {
		report.setBook(json.getString("book"));
		report.setAssetId(json.getString("assetId"));
		report.setQuantity(json.getInt("quantity"));
		report.setDescription(json.getString("description"));
		report.setCreationDate(format.parse(json.getString("creationDate")));
		report.setSerialNumber(json.getString("serialNumber"));
		report.setTagNumber(json.getString("tagNumber"));
		report.setPicStatus(json.getString("picStatus"));
		
		String picDate = json.getString("picDate");
		if (!picDate.isEmpty()) report.setPicDate(format.parse(picDate));
		
		String cipDeliveryDate = json.getString("cipDeliveryDate");
		if (!cipDeliveryDate.isEmpty()) report.setCipDeliveryDate(format.parse(cipDeliveryDate));
		
		report.setLinkId(json.getString("linkId"));
		report.setAcceptanceNumber(json.getString("acceptanceNumber"));
		report.setDepreciateFlag(json.getString("depreciateFlag"));
		report.setCipEu(json.getString("cipEu"));
		report.setInvoiceNumber(json.getString("invoiceNumber"));
		report.setPoNumber(json.getString("poNumber"));
		report.setPoLineNumber(json.getString("poLineNumber"));
		report.setUplLine(json.getString("uplLine"));
		report.setTransferToNewFar(json.getString("transferToNewFar"));
		report.setAssetStatus(json.getString("assetStatus"));
		
		String value = json.getString("value");
		if (!value.isEmpty()) report.setValue(Double.parseDouble(value));
		
		report.setPartNumber(json.getString("partNumber"));
		report.setVendorName(json.getString("vendorName"));
		report.setVendorNumber(json.getString("vendorNumber"));
		report.setMergedCode(json.getString("mergedCode"));
		report.setCostAccount(json.getString("costAccount"));
		report.setAccumulatedDepreAccount(json.getString("accumulatedDepreAccount"));
		report.setCipCostAccount(json.getString("cipCostAccount"));
		report.setExpenseCostCenter(json.getString("expenseCostCenter"));
		report.setExpenseAccount(json.getString("expenseAccount"));
		report.setLife(json.getInt("life"));
		
		String datePlacedInService = json.getString("datePlacedInService");
		if (!datePlacedInService.isEmpty()) report.setDatePlacedInService(format.parse(datePlacedInService));
		
		report.setCost(json.getDouble("cost"));
		report.setNbv(json.getDouble("nbv"));
		report.setDepreciationAmount(json.getDouble("depreciationAmount"));
		report.setYtdDepreciation(json.getDouble("ytdDepreciation"));
		report.setDepreciationReserve(json.getDouble("depreciationReserve"));
		report.setSalvageValue(json.getDouble("salvageValue"));
		report.setCategory(json.getString("category"));
		report.setCategoryDescription(json.getString("categoryDescription"));
		report.setLocationSegment1(json.getString("locationSegment1"));
		report.setLocationSegment2(json.getString("locationSegment2"));
		report.setLocationSegment3(json.getString("locationSegment3"));
		report.setLocationSegment4(json.getString("locationSegment4"));
		report.setLocations(json.getString("locations"));
		report.setSequenceNumber(json.getInt("sequenceNumber"));
	}
}
