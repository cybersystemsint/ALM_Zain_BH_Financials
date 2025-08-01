package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.FinancialReportProjection;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
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
//    @Query("SELECT fr FROM tb_FinancialReport fr " +
//            "WHERE (:assetName IS NULL OR fr.assetName = :assetName) " +
//            "OR (:assetSerialNumber IS NULL OR fr.assetSerialNumber = :assetSerialNumber)")
//    Optional<tb_FinancialReport> findByAssetNameOrAssetSerialNumberExact(
//            @Param("assetName") String assetName,
//            @Param("assetSerialNumber") String assetSerialNumber);
    @Query("SELECT fr FROM tb_FinancialReport fr " +
            "WHERE (:assetName IS NULL OR fr.assetName = :assetName) " +
            "OR (:assetSerialNumber IS NULL OR fr.assetSerialNumber = :assetSerialNumber)")
    Optional<tb_FinancialReport> findByAssetNameOrAssetSerialNumber(
            @Param("assetName") String assetName,
            @Param("assetSerialNumber") String assetSerialNumber);

    @Query("SELECT fr FROM tb_FinancialReport fr WHERE fr.assetName IN :identifiers OR fr.assetSerialNumber IN :identifiers")
    List<tb_FinancialReport> findByAssetNameOrAssetSerialNumberIn(@Param("identifiers") List<String> identifiers);

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

    @Query("SELECT f FROM tb_FinancialReport f WHERE LOWER(f.assetName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(f.assetSerialNumber) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<tb_FinancialReport> findAllByAssetNameContainingIgnoreCaseOrAssetSerialNumberContainingIgnoreCase(String query);

    @Query("SELECT fr FROM tb_FinancialReport fr " +
            "WHERE fr.netCost > :netCost " +
            "AND (:insertDate IS NULL OR fr.insertDate <= :insertDate) " +
            "AND (:search IS NULL OR LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(fr.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<tb_FinancialReport> findByNetCostGreaterThanAndInsertDateBefore(
            @Param("netCost") BigDecimal netCost,
            @Param("insertDate") Date insertDate,
            @Param("search") String search,
            Pageable pageable);
    @Query("SELECT r FROM tb_FinancialReport r WHERE (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%'))) AND r.insertDate <= :endOfMonth")
    Page<tb_FinancialReport> findByInsertDateBefore(Date endOfMonth, String search, Pageable pageable);

    @Query("SELECT r.initialCost AS initialCost, r.salvageValue AS salvageValue, r.dateOfService AS dateOfService, r.usefulLifeMonths AS usefulLifeMonths, r.monthlyDepreciationAmount AS monthlyDepreciationAmount FROM tb_FinancialReport r WHERE (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%'))) AND r.insertDate <= :endOfMonth")
    List<FinancialReportProjection> findProjectionByInsertDateBefore(Date endOfMonth, String search);

    @Query("SELECT COALESCE(SUM(r.initialCost), 0) FROM tb_FinancialReport r WHERE (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%'))) AND r.insertDate <= :endOfMonth")
    BigDecimal findTotalCostByInsertDateBefore(Date endOfMonth, String search);
    @Query("SELECT COALESCE(SUM(r.accumulatedDepreciation), 0) FROM tb_FinancialReport r WHERE (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%'))) AND r.insertDate <= :endOfMonth")
    BigDecimal findTotalDepreciationByInsertDateBefore(Date endOfMonth, String search);

    @Query("SELECT COALESCE(SUM(r.netCost), 0) FROM tb_FinancialReport r WHERE (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%'))) AND r.insertDate <= :endOfMonth")
    BigDecimal findTotalNBVByInsertDateBefore(Date endOfMonth, String search);

    @Query("SELECT r FROM tb_FinancialReport r WHERE r.assetName IN :ids OR r.assetSerialNumber IN :ids")
    List<tb_FinancialReport> findByAssetNameInOrAssetSerialNumberIn(@Param("ids") List<String> ids);
    // Modified/Added queries in FinancialReportRepo.java (add these to the interface)
    @Query("SELECT r FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonth AND (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<tb_FinancialReport> findByDateOfServiceBefore(Date endOfMonth, String search, Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.initialCost), 0) FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonth AND (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    BigDecimal findTotalCostByDateOfServiceBefore(Date endOfMonth, String search);

    @Query("SELECT COALESCE(SUM(r.accumulatedDepreciation), 0) FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonth AND (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    BigDecimal findTotalDepreciationByDateOfServiceBefore(Date endOfMonth, String search);

    @Query("SELECT COALESCE(SUM(r.netCost), 0) FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonth AND (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    BigDecimal findTotalNBVByDateOfServiceBefore(Date endOfMonth, String search);
}