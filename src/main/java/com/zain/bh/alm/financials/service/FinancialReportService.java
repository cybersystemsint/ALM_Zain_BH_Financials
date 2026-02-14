package com.zain.bh.alm.financials.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.zain.bh.alm.financials.entity.FinancialReport;

public interface FinancialReportService {

    List<FinancialReport> findAll();
    Page<FinancialReport> findAll(Pageable pageable);
    Page<FinancialReport> findBySearchTerm(String search, Pageable pageable);
    FinancialReport save(FinancialReport report);
    Optional<FinancialReport> findBySerialNumber(String serialNumber);
    List<FinancialReport> findAllBySerialNumber(String serialNumber);
    Optional<FinancialReport> findByAssetName(String assetName);
    List<FinancialReport> findAllByAssetName(String assetName);
    Optional<FinancialReport> findById(Integer financialReportId);
    void delete(int recordNo);
    BigDecimal getTotalCost();
    BigDecimal getTotalNBV();
    BigDecimal getTotalDepreciation();
    BigDecimal getFilteredCost(String search, String lastMonthDate);
    BigDecimal getFilteredNBV(String search, String lastMonthDate);
    BigDecimal getFilteredDepreciation(String search, String lastMonthDate);
    Page<FinancialReport> findByAssetNameOrSerialNumber(String query, Pageable pageable);
    Page<FinancialReport> findAll(Specification<FinancialReport> spec, Pageable pageable);
    FinancialReport calculateDepreciation(String serialNumber, BigDecimal adjustment, String username);
    Page<FinancialReport> findByStatusFlagNotAndNetCostGreaterThan(String statusFlag, BigDecimal netCost, Pageable pageable);
}

