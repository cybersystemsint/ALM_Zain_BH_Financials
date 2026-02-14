package com.zain.bh.alm.financials.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FarQueryService {

    private final JdbcTemplate jdbcTemplate;

    public FarQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getFarReports(String assetId, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(size, 1);

        // Count query with parameterized SQL
        String countSql = "SELECT COUNT(*) FROM tb_FarReport";
        Object[] countParams = new Object[0];
        
        if (assetId != null && !assetId.isEmpty()) {
            countSql += " WHERE assetId = ?";
            countParams = new Object[]{assetId};
        }
        
        int totalRecords = jdbcTemplate.queryForObject(countSql, countParams, Integer.class);

        // Aggregate queries
        BigDecimal totalCost = jdbcTemplate.queryForObject("SELECT SUM(cost) FROM tb_FarReport", BigDecimal.class);
        BigDecimal totalNBV = jdbcTemplate.queryForObject("SELECT SUM(netCost) FROM tb_FarReport", BigDecimal.class);
        BigDecimal totalDepreciation = jdbcTemplate.queryForObject("SELECT SUM(accumulatedDepreciationAmt) FROM tb_FarReport", BigDecimal.class);

        // Calculate pagination
        int offset = (page - 1) * size;
        
        // Data query with parameterized SQL
        String sql = "SELECT recordNo, recordDatetime, book, assetId, quantity, description, creationDate, " +
                    "serialNumber, tagNumber, picStatus, picDate, cipDeliveryDate, linkId, acceptanceNumber, " +
                    "depreciateFlag, cipEu, invoiceNumber, poNumber, poLineNumber, uplLine, transferToNewFar, " +
                    "assetStatus, value, partNumber, vendorName, vendorNumber, mergedCode, costAccount, " +
                    "accumulatedDepreAccount, cipCostAccount, expenseCostCenter, expenseAccount, Life, " +
                    "datePlacedInService, cost, nbv, depreciationAmount, ytdDepreciation, depreciationReserve, " +
                    "salvageValue, category, categoryDescription, locationSegment1, locationSegment2, " +
                    "locationSegment3, locationSegment4, locations, sequenceNumber, createdBy, createdDate, " +
                    "updatedBy, updatedDate, monthlyDepreciationAmt, accumulatedDepreciationAmt, depreciationDate, " +
                    "netCost FROM tb_FarReport";

        List<Object> params = new ArrayList<>();
        if (assetId != null && !assetId.isEmpty()) {
            sql += " WHERE assetId = ?";
            params.add(assetId);
        }
        
        sql += " LIMIT ? OFFSET ?";
        params.add(size);
        params.add(offset);

        List<Map<String, Object>> result = new ArrayList<>();
        jdbcTemplate.query(sql, params.toArray(), rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
            }
            result.add(row);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalcost", totalCost);
        response.put("totalNBV", totalNBV);
        response.put("totalDepreciation", totalDepreciation);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

        return response;
    }
}
