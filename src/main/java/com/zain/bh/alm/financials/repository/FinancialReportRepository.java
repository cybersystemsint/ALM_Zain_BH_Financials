package com.zain.bh.alm.financials.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.FinancialReport;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialReportRepository extends JpaRepository<FinancialReport, Long>, JpaSpecificationExecutor<FinancialReport> {
    @Override
    Page<FinancialReport> findAll(Pageable pageable);

    @Query("SELECT fr FROM FinancialReport fr WHERE " +
            "LOWER(fr.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(fr.siteId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(fr.tag) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(fr.assetType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(fr.nodeType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<FinancialReport> findBySearchTerm(@Param("search") String search, Pageable pageable);

    Optional<FinancialReport> findByAssetSerialNumber(String assetSerialNumber);
    Optional<FinancialReport> findByAssetName(String assetName);

    List<FinancialReport> findAllByAssetSerialNumber(String assetSerialNumber);
    List<FinancialReport> findAllByAssetName(String assetName);
    List<FinancialReport> findByStatusFlagNot(String statusFlag);
    List<FinancialReport> findByRetirementDateIsNotNull();

    @Query("SELECT fr FROM FinancialReport fr " +
            "WHERE LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(fr.assetSerialNumber) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<FinancialReport> findByAssetNameOrAssetSerialNumber(@Param("query") String query, Pageable pageable);

    @Query("SELECT fr FROM FinancialReport fr " +
            "WHERE (:assetName IS NULL OR fr.assetName = :assetName) " +
            "OR (:assetSerialNumber IS NULL OR fr.assetSerialNumber = :assetSerialNumber)")
    Optional<FinancialReport> findByAssetNameOrAssetSerialNumberExact(
            @Param("assetName") String assetName,
            @Param("assetSerialNumber") String assetSerialNumber);

    @Query("SELECT fr FROM FinancialReport fr WHERE fr.assetSerialNumber IN :serialNumbers")
    List<FinancialReport> findByAssetSerialNumberIn(@Param("serialNumbers") List<String> serialNumbers);

    @Query("SELECT SUM(fr.initialCost) FROM FinancialReport fr")
    Optional<BigDecimal> findTotalCost();

    @Query("SELECT SUM(fr.netCost) FROM FinancialReport fr")
    Optional<BigDecimal> findTotalNBV();

    @Query("SELECT SUM(fr.accumulatedDepreciation) FROM FinancialReport fr")
    Optional<BigDecimal> findTotalDepreciation();

    @Query("SELECT SUM(fr.initialCost) FROM FinancialReport fr " +
            "WHERE (:search IS NULL OR LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:date IS NULL OR fr.dateOfService = :date)")
    Optional<BigDecimal> findFilteredCost(@Param("search") String search, @Param("date") String lastMonthDate);

    @Query("SELECT SUM(fr.netCost) FROM FinancialReport fr " +
            "WHERE (:search IS NULL OR LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:date IS NULL OR fr.dateOfService = :date)")
    Optional<BigDecimal> findFilteredNBV(@Param("search") String search, @Param("date") String lastMonthDate);

    @Query("SELECT SUM(fr.accumulatedDepreciation) FROM FinancialReport fr " +
            "WHERE (:search IS NULL OR LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:date IS NULL OR fr.dateOfService = :date)")
    Optional<BigDecimal> findFilteredDepreciation(@Param("search") String search, @Param("date") String lastMonthDate);

    @Query("SELECT fr FROM FinancialReport fr WHERE fr.statusFlag != :statusFlag AND fr.netCost > :netCost")
    Page<FinancialReport> findByStatusFlagNotAndNetCostGreaterThan(
            @Param("statusFlag") String statusFlag,
            @Param("netCost") BigDecimal netCost,
            Pageable pageable);
}
