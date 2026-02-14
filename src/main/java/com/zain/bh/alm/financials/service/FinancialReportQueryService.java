package com.zain.bh.alm.financials.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.FinancialReport;

@Service
public class FinancialReportQueryService {

    private final FinancialReportService financialReportService;

    public FinancialReportQueryService(FinancialReportService financialReportService) {
        this.financialReportService = financialReportService;
    }

    public Map<String, Object> getAllReports(int page, int size, String search, String sortBy, String sortDir, String asAtDate) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<FinancialReport> reportPage = search != null && !search.trim().isEmpty() ?
                financialReportService.findBySearchTerm(search, pageable) :
                financialReportService.findAll(pageable);

        String lastMonthDate = getLastMonthDate(asAtDate);

        Map<String, Object> response = new HashMap<>();
        response.put("reports", reportPage.getContent());
        response.put("currentPage", reportPage.getNumber());
        response.put("totalItems", reportPage.getTotalElements());
        response.put("totalPages", reportPage.getTotalPages());
        response.put("first", reportPage.isFirst());
        response.put("last", reportPage.isLast());
        response.put("size", reportPage.getSize());
        response.put("sort", sortBy + "," + sortDir);

        BigDecimal totalCost = financialReportService.getTotalCost();
        BigDecimal totalNBV = financialReportService.getTotalNBV();
        BigDecimal totalDepreciation = financialReportService.getTotalDepreciation();
        BigDecimal filteredCost = financialReportService.getFilteredCost(search, lastMonthDate);
        BigDecimal filteredNBV = financialReportService.getFilteredNBV(search, lastMonthDate);
        BigDecimal filteredDepreciation = financialReportService.getFilteredDepreciation(search, lastMonthDate);

        response.put("totalCost", totalCost);
        response.put("totalNBV", totalNBV);
        response.put("totalDepreciation", totalDepreciation);
        response.put("filteredCost", filteredCost);
        response.put("filteredNBV", filteredNBV);
        response.put("filteredDepreciation", filteredDepreciation);

        return response;
    }

    public Map<String, Object> searchReports(String query, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<FinancialReport> reportPage = financialReportService.findByAssetNameOrSerialNumber(query, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("reports", reportPage.getContent());
        response.put("currentPage", reportPage.getNumber());
        response.put("totalItems", reportPage.getTotalElements());
        response.put("totalPages", reportPage.getTotalPages());
        response.put("first", reportPage.isFirst());
        response.put("last", reportPage.isLast());
        response.put("size", reportPage.getSize());
        response.put("sort", sortBy + "," + sortDir);
        return response;
    }

    public Map<String, Object> filterReports(Map<String, String> filters, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<FinancialReport> spec = (root, query, cb) -> {
            java.util.List<javax.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (filters.containsKey("l1") && !filters.get("l1").isEmpty()) {
                predicates.add(cb.equal(root.get("l1"), filters.get("l1")));
            }
            if (filters.containsKey("l2") && !filters.get("l2").isEmpty()) {
                predicates.add(cb.equal(root.get("l2"), filters.get("l2")));
            }
            if (filters.containsKey("l3") && !filters.get("l3").isEmpty()) {
                predicates.add(cb.equal(root.get("l3"), filters.get("l3")));
            }
            if (filters.containsKey("l4") && !filters.get("l4").isEmpty()) {
                predicates.add(cb.equal(root.get("l4"), filters.get("l4")));
            }
            if (filters.containsKey("itemBarCode") && !filters.get("itemBarCode").isEmpty()) {
                predicates.add(cb.equal(root.get("itemBarCode"), filters.get("itemBarCode")));
            }
            if (filters.containsKey("invoiceNumber") && !filters.get("invoiceNumber").isEmpty()) {
                predicates.add(cb.equal(root.get("invoiceNumber"), filters.get("invoiceNumber")));
            }
            if (filters.containsKey("assetSerialNumber") && !filters.get("assetSerialNumber").isEmpty()) {
                predicates.add(cb.equal(root.get("assetSerialNumber"), filters.get("assetSerialNumber")));
            }
            if (filters.containsKey("assetName") && !filters.get("assetName").isEmpty()) {
                predicates.add(cb.equal(root.get("assetName"), filters.get("assetName")));
            }
            if (filters.containsKey("poNumber") && !filters.get("poNumber").isEmpty()) {
                predicates.add(cb.equal(root.get("poNumber"), filters.get("poNumber")));
            }
            return cb.and(predicates.toArray(new javax.persistence.criteria.Predicate[0]));
        };

        Page<FinancialReport> reportPage = financialReportService.findAll(spec, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("reports", reportPage.getContent());
        response.put("currentPage", reportPage.getNumber());
        response.put("totalItems", reportPage.getTotalElements());
        response.put("totalPages", reportPage.getTotalPages());
        response.put("first", reportPage.isFirst());
        response.put("last", reportPage.isLast());
        response.put("size", reportPage.getSize());
        response.put("sort", sortBy + "," + sortDir);
        return response;
    }

    private String getLastMonthDate(String asAtDate) {
        if (asAtDate == null || asAtDate.isEmpty()) return null;
        LocalDate inputDate = LocalDate.parse(asAtDate);
        return YearMonth.from(inputDate).minusMonths(1).atEndOfMonth().toString();
    }
}
