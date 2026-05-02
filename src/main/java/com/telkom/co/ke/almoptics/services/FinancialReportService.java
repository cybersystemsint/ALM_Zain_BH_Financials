package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification; // Add this import

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing financial reports.
 */
public interface FinancialReportService {

    // Existing methods remain unchanged
    List<tb_FinancialReport> findAll();
    Page<tb_FinancialReport> findAll(Pageable pageable);
    Page<tb_FinancialReport> findBySearchTerm(String search, Pageable pageable);
    tb_FinancialReport save(tb_FinancialReport report);
    Optional<tb_FinancialReport> findBySerialNumber(String serialNumber);
    List<tb_FinancialReport> findAllBySerialNumber(String serialNumber);
    Optional<tb_FinancialReport> findByAssetName(String assetName);
    List<tb_FinancialReport> findAllByAssetName(String assetName);
    Optional<tb_FinancialReport> findById(Integer financialReportId);
    void delete(int recordNo);
    BigDecimal getTotalCost();
    BigDecimal getTotalNBV();
    BigDecimal getTotalDepreciation();
    BigDecimal getFilteredCost(String search, String lastMonthDate);
    BigDecimal getFilteredNBV(String search, String lastMonthDate);
    BigDecimal getFilteredDepreciation(String search, String lastMonthDate);

    // New methods
    Page<tb_FinancialReport> findByAssetNameOrSerialNumber(String query, Pageable pageable);
    Page<tb_FinancialReport> findAll(Specification<tb_FinancialReport> spec, Pageable pageable);
    // New methods
    tb_FinancialReport calculateDepreciation(String serialNumber, BigDecimal adjustment, String username);
    Page<tb_FinancialReport> findByStatusFlagNotAndNetCostGreaterThan(String statusFlag, BigDecimal netCost, Pageable pageable);
    Page<tb_FinancialReport> findByStatusFlagNot(String statusFlag, Pageable pageable);
    Map<String, Object> calculateDepreciationForMonth(String date, String search, Pageable pageable);
    Map<String, Object> calculateDepreciationForMonthV2(String date, String search, Pageable pageable);
    BigDecimal computeMonthlyDepreciation(tb_FinancialReport report);
    List<tb_FinancialReport> findAllByAssetNameInOrAssetSerialNumberIn(List<String> assetIds);
}