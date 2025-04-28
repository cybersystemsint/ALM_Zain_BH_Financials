package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialReportRepo extends JpaRepository<tb_FinancialReport, Long>, JpaSpecificationExecutor<tb_FinancialReport> {

    // Base pagination
    @Override
    Page<tb_FinancialReport> findAll(Pageable pageable);

    // Search with corrected field names
    @Query("SELECT fr FROM tb_FinancialReport fr WHERE " +
            "LOWER(fr.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(fr.siteId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(fr.tag) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(fr.assetType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(fr.nodeType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<tb_FinancialReport> findBySearchTerm(@Param("search") String search, Pageable pageable);

    // Key methods with corrected field names
    Optional<tb_FinancialReport> findByAssetSerialNumber(String assetSerialNumber);
    Optional<tb_FinancialReport> findByAssetName(String assetName);

    // Additional methods with corrections
    List<tb_FinancialReport> findAllByAssetSerialNumber(String assetSerialNumber);
    List<tb_FinancialReport> findAllByAssetName(String assetName);
    List<tb_FinancialReport> findByStatusFlagNot(String statusFlag);
    List<tb_FinancialReport> findByRetirementDateIsNotNull();

    // Existing method for search by AssetName or AssetSerialNumber with pagination
    @Query("SELECT fr FROM tb_FinancialReport fr " +
            "WHERE LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(fr.assetSerialNumber) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<tb_FinancialReport> findByAssetNameOrAssetSerialNumber(@Param("query") String query, Pageable pageable);

    // New method for exact match by assetName or assetSerialNumber
    @Query("SELECT fr FROM tb_FinancialReport fr " +
            "WHERE (:assetName IS NULL OR fr.assetName = :assetName) " +
            "OR (:assetSerialNumber IS NULL OR fr.assetSerialNumber = :assetSerialNumber)")
    Optional<tb_FinancialReport> findByAssetNameOrAssetSerialNumberExact(
            @Param("assetName") String assetName,
            @Param("assetSerialNumber") String assetSerialNumber);

    // New method for bulk serial number lookup
    @Query("SELECT fr FROM tb_FinancialReport fr WHERE fr.assetSerialNumber IN :serialNumbers")
    List<tb_FinancialReport> findByAssetSerialNumberIn(@Param("serialNumbers") List<String> serialNumbers);

    // Aggregate methods for totals
    @Query("SELECT SUM(fr.initialCost) FROM tb_FinancialReport fr")
    Optional<BigDecimal> findTotalCost();

    @Query("SELECT SUM(fr.netCost) FROM tb_FinancialReport fr")
    Optional<BigDecimal> findTotalNBV();

    @Query("SELECT SUM(fr.accumulatedDepreciation) FROM tb_FinancialReport fr")
    Optional<BigDecimal> findTotalDepreciation();

    // Aggregate methods for filtered values
    @Query("SELECT SUM(fr.initialCost) FROM tb_FinancialReport fr " +
            "WHERE (:search IS NULL OR LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:date IS NULL OR fr.dateOfService = :date)")
    Optional<BigDecimal> findFilteredCost(@Param("search") String search, @Param("date") String lastMonthDate);

    @Query("SELECT SUM(fr.netCost) FROM tb_FinancialReport fr " +
            "WHERE (:search IS NULL OR LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:date IS NULL OR fr.dateOfService = :date)")
    Optional<BigDecimal> findFilteredNBV(@Param("search") String search, @Param("date") String lastMonthDate);

    @Query("SELECT SUM(fr.accumulatedDepreciation) FROM tb_FinancialReport fr " +
            "WHERE (:search IS NULL OR LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:date IS NULL OR fr.dateOfService = :date)")
    Optional<BigDecimal> findFilteredDepreciation(@Param("search") String search, @Param("date") String lastMonthDate);
    // New method for active assets (not decommissioned and net cost > 0)
    @Query("SELECT fr FROM tb_FinancialReport fr WHERE fr.statusFlag != :statusFlag AND fr.netCost > :netCost")
    Page<tb_FinancialReport> findByStatusFlagNotAndNetCostGreaterThan(
            @Param("statusFlag") String statusFlag,
            @Param("netCost") BigDecimal netCost,
            Pageable pageable);


}