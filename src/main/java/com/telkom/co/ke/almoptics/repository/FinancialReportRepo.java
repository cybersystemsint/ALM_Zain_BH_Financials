package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.FinancialReportProjection;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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
    // NOTE: Search predicates here MUST mirror the columns in findBySearchTerm so that
    // summary totals (Total Cost / Total NBV / Total Dep) match the displayed rows when
    // the user searches by Serial Number, Site, Tag, AssetType, NodeType, or AssetName.
    // Previously these only matched assetName, which caused totals to be 0 for any other
    // search field (regression reported by QA on the Searched/Filtered FAR summary tab).
    @Query("SELECT COALESCE(SUM(fr.initialCost), 0) FROM tb_FinancialReport fr " +
            "WHERE (:search IS NULL OR :search = '' OR " +
            "       LOWER(fr.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.siteId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.tag) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.assetType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.nodeType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:date IS NULL OR :date = '' OR fr.dateOfService = :date)")
    Optional<BigDecimal> findFilteredCost(@Param("search") String search, @Param("date") String lastMonthDate);

    @Query("SELECT COALESCE(SUM(fr.netCost), 0) FROM tb_FinancialReport fr " +
            "WHERE (:search IS NULL OR :search = '' OR " +
            "       LOWER(fr.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.siteId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.tag) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.assetType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.nodeType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:date IS NULL OR :date = '' OR fr.dateOfService = :date)")
    Optional<BigDecimal> findFilteredNBV(@Param("search") String search, @Param("date") String lastMonthDate);

    @Query("SELECT COALESCE(SUM(fr.accumulatedDepreciation), 0) FROM tb_FinancialReport fr " +
            "WHERE (:search IS NULL OR :search = '' OR " +
            "       LOWER(fr.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.siteId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.tag) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.assetType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.nodeType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "       LOWER(fr.assetName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:date IS NULL OR :date = '' OR fr.dateOfService = :date)")
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
    Page<tb_FinancialReport> findByDateOfServiceBefore(@Param("endOfMonth") String endOfMonth, @Param("search") String search, Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.initialCost), 0) FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonth AND (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    BigDecimal findTotalCostByDateOfServiceBefore(@Param("endOfMonth") String endOfMonth, @Param("search") String search);

    @Query("SELECT COALESCE(SUM(r.accumulatedDepreciation), 0) FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonth AND (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    BigDecimal findTotalDepreciationByDateOfServiceBefore(@Param("endOfMonth") String endOfMonth, @Param("search") String search);

    @Query("SELECT COALESCE(SUM(r.netCost), 0) FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonth AND (LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    BigDecimal findTotalNBVByDateOfServiceBefore(@Param("endOfMonth") String endOfMonth, @Param("search") String search);

    @Query("SELECT fr FROM tb_FinancialReport fr WHERE fr.statusFlag != :statusFlag")
    Page<tb_FinancialReport> findByStatusFlagNot(@Param("statusFlag") String statusFlag, Pageable pageable);

    @Query("SELECT r FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonth")
    Page<tb_FinancialReport> findByDateOfServiceBeforeNoSearch(@Param("endOfMonth") String endOfMonth, Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.initialCost), 0) FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonth")
    BigDecimal findTotalCostByDateOfServiceBeforeNoSearch(@Param("endOfMonth") String endOfMonth);

    @Query("SELECT COALESCE(SUM(r.accumulatedDepreciation), 0) FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonth")
    BigDecimal findTotalDepreciationByDateOfServiceBeforeNoSearch(@Param("endOfMonth") String endOfMonth);

    @Modifying
    @Query(value = "UPDATE tb_FinancialReport SET MonthlyDepreciationAmount = NULL WHERE InitialCost > 0 AND UsefulLife_Months > 0 AND DateOfService IS NOT NULL", nativeQuery = true)
    int resetMonthlyDepreciationAmount();

    @Modifying
    @Query(value = "UPDATE tb_FinancialReport SET AccumulatedDepreciation = NULL WHERE InitialCost > 0 AND UsefulLife_Months > 0 AND DateOfService IS NOT NULL", nativeQuery = true)
    int resetAccumulatedDepreciation();

    @Modifying
    @Query(value = "UPDATE tb_FinancialReport SET NetCost = NULL WHERE InitialCost > 0 AND UsefulLife_Months > 0 AND DateOfService IS NOT NULL", nativeQuery = true)
    int resetNetCost();

    @Modifying
    @Query(value = "UPDATE tb_FinancialReport SET MonthlyDepreciationAmount = (InitialCost - IFNULL(SalvageValue, 0)) / UsefulLife_Months WHERE InitialCost > 0 AND UsefulLife_Months > 0 AND DateOfService IS NOT NULL", nativeQuery = true)
    int updateMonthlyDepreciation();

    @Modifying
    @Query(value = "UPDATE tb_FinancialReport AS fr " +
            "JOIN ( " +
            "    SELECT " +
            "        Id, " +
            "        DATE_ADD(DATE_ADD(DateOfService, INTERVAL 1 - DAY(DateOfService) DAY), INTERVAL 1 MONTH) AS D, " +
            "        IF(WriteOffDate IS NOT NULL, LAST_DAY(WriteOffDate), CURDATE()) AS end_date " +
            "    FROM tb_FinancialReport " +
            ") AS dates ON fr.Id = dates.Id " +
            "SET " +
            "    fr.AccumulatedDepreciation = LEAST( " +
            "        GREATEST( " +
            "            (fr.MonthlyDepreciationAmount * LEAST( " +
            "                GREATEST(TIMESTAMPDIFF(MONTH, dates.D, dates.end_date), 0), " +
            "                fr.UsefulLife_Months " +
            "            ) + IFNULL(fr.Adjustment, 0)), " +
            "            0 " +
            "        ), " +
            "        (fr.InitialCost - IFNULL(fr.SalvageValue, 0)) " +
            "    ) " +
            "WHERE fr.MonthlyDepreciationAmount IS NOT NULL " +
            "AND fr.InitialCost > 0 " +
            "AND fr.UsefulLife_Months > 0 " +
            "AND fr.DateOfService IS NOT NULL", nativeQuery = true)
    int updateAccumulatedDepreciation();



    @Modifying
    @Query(value = "UPDATE tb_FinancialReport " +
            "SET " +
            "    NetCost = GREATEST(InitialCost - AccumulatedDepreciation, IFNULL(SalvageValue, 0)), " +
            "    AccumulatedDepreciation = InitialCost - NetCost " +
            "WHERE NetCost < IFNULL(SalvageValue, 0) " +
            "AND InitialCost > 0 " +
            "AND UsefulLife_Months > 0 " +
            "AND DateOfService IS NOT NULL", nativeQuery = true)
    int adjustNetCostAndAccumulatedDepreciation();

    @Modifying
    @Query(value = "UPDATE tb_FinancialReport AS fr " +
            "JOIN ( " +
            "    SELECT " +
            "        Id, " +
            "        DATE_ADD(DATE_ADD(DateOfService, INTERVAL 1 - DAY(DateOfService) DAY), INTERVAL 1 MONTH) AS D " +
            "    FROM tb_FinancialReport " +
            ") AS dates ON fr.Id = dates.Id " +
            "SET " +
            "    fr.RetirementDate = IF(fr.WriteOffDate IS NOT NULL, " +
            "        LAST_DAY(fr.WriteOffDate), " +
            "        DATE_SUB(DATE_ADD(dates.D, INTERVAL fr.UsefulLife_Months MONTH), INTERVAL 1 DAY) " +
            "    ) " +
            "WHERE fr.InitialCost > 0 AND fr.UsefulLife_Months > 0 AND fr.DateOfService IS NOT NULL", nativeQuery = true)
    int updateRetirementDate();

    @Modifying
    @Query(value = "UPDATE tb_FinancialReport SET NetCost = InitialCost - AccumulatedDepreciation WHERE InitialCost > 0 AND UsefulLife_Months > 0 AND DateOfService IS NOT NULL AND AccumulatedDepreciation IS NOT NULL", nativeQuery = true)
    int updateNetCost();

    // ------------------------------------------------------------------
    // Bulk set-based queries used by the new SyncOrchestratorService.
    // These replace the per-row save() calls in the legacy
    // processFinancialReportAsset path and are safe to run inside a
    // single transaction.
    // ------------------------------------------------------------------

    /** Bulk-promote NEW → EXISTING for assets older than 30 days. */
    @Modifying
    @Query(value = "UPDATE tb_FinancialReport " +
            "SET StatusFlag = 'EXISTING', ChangeDate = NOW() " +
            "WHERE StatusFlag = 'NEW' " +
            "  AND FinancialApprovalStatus IS NULL " +
            "  AND COALESCE(InsertDate, ChangeDate) < DATE_SUB(NOW(), INTERVAL 30 DAY)",
            nativeQuery = true)
    int bulkPromoteNewToExisting();

    /**
     * Bulk-mark FR rows as POTENTIALLY_MISSING (sets RetirementDate=NOW)
     * when the asset doesn't exist in any source inventory and hasn't
     * been touched in 14 days. Source tables: tb_Node (active),
     * tb_Passive_Inventory, vw_IT_Inventory.
     *
     * COLLATE clauses normalise utf8mb4_0900_ai_ci (FR) vs utf8mb4_unicode_ci
     * (source inventory tables). Without them MySQL throws error 1267
     * "Illegal mix of collations" on every join predicate.
     */
    @Modifying
    @Query(value = "UPDATE tb_FinancialReport fr " +
            "SET fr.RetirementDate = NOW(), fr.ChangeDate = NOW() " +
            "WHERE fr.RetirementDate IS NULL " +
            "  AND fr.StatusFlag <> 'DECOMMISSIONED' " +
            "  AND COALESCE(fr.ChangeDate, fr.InsertDate) < DATE_SUB(NOW(), INTERVAL 14 DAY) " +
            "  AND NOT EXISTS (SELECT 1 FROM tb_Node a " +
            "                  WHERE a.SerialNumber COLLATE utf8mb4_unicode_ci " +
            "                      = fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci) " +
            "  AND NOT EXISTS (SELECT 1 FROM tb_Passive_Inventory p " +
            "                  WHERE p.Serial COLLATE utf8mb4_unicode_ci " +
            "                          = fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                     OR p.ObjectID COLLATE utf8mb4_unicode_ci " +
            "                          = fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                     OR p.Serial COLLATE utf8mb4_unicode_ci " +
            "                          = fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "                     OR p.ObjectID COLLATE utf8mb4_unicode_ci " +
            "                          = fr.AssetName COLLATE utf8mb4_unicode_ci) " +
            "  AND NOT EXISTS (SELECT 1 FROM vw_IT_Inventory i " +
            "                  WHERE i.HostSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                          = fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                     OR i.ObjectID COLLATE utf8mb4_unicode_ci " +
            "                          = fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                     OR i.HostSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                          = fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "                     OR i.ObjectID COLLATE utf8mb4_unicode_ci " +
            "                          = fr.AssetName COLLATE utf8mb4_unicode_ci)",
            nativeQuery = true)
    int bulkMarkMissingAssets();

    /**
     * Bulk-clear RetirementDate for previously-missing assets that have
     * reappeared in source inventory. (Same COLLATE handling as
     * bulkMarkMissingAssets.)
     */
    @Modifying
    @Query(value = "UPDATE tb_FinancialReport fr " +
            "SET fr.RetirementDate = NULL, fr.ChangeDate = NOW() " +
            "WHERE fr.RetirementDate IS NOT NULL " +
            "  AND fr.StatusFlag <> 'DECOMMISSIONED' " +
            "  AND ( EXISTS (SELECT 1 FROM tb_Node a " +
            "               WHERE a.SerialNumber COLLATE utf8mb4_unicode_ci " +
            "                   = fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci) " +
            "     OR EXISTS (SELECT 1 FROM tb_Passive_Inventory p " +
            "               WHERE p.Serial COLLATE utf8mb4_unicode_ci " +
            "                       = fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                  OR p.ObjectID COLLATE utf8mb4_unicode_ci " +
            "                       = fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                  OR p.Serial COLLATE utf8mb4_unicode_ci " +
            "                       = fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "                  OR p.ObjectID COLLATE utf8mb4_unicode_ci " +
            "                       = fr.AssetName COLLATE utf8mb4_unicode_ci) " +
            "     OR EXISTS (SELECT 1 FROM vw_IT_Inventory i " +
            "               WHERE i.HostSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                       = fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                  OR i.ObjectID COLLATE utf8mb4_unicode_ci " +
            "                       = fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                  OR i.HostSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                       = fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "                  OR i.ObjectID COLLATE utf8mb4_unicode_ci " +
            "                       = fr.AssetName COLLATE utf8mb4_unicode_ci) )",
            nativeQuery = true)
    int bulkClearRecoveredAssets();

    /**
     * Bulk-decommission FR rows that have been missing past the
     * 14-day grace window.
     */
    @Modifying
    @Query(value = "UPDATE tb_FinancialReport " +
            "SET StatusFlag = 'DECOMMISSIONED', ChangeDate = NOW() " +
            "WHERE StatusFlag <> 'DECOMMISSIONED' " +
            "  AND RetirementDate IS NOT NULL " +
            "  AND RetirementDate < DATE_SUB(NOW(), INTERVAL 14 DAY)",
            nativeQuery = true)
    int bulkDecommissionAfterGracePeriod();

    // Lightweight projections used for decision-making without fetching
    // the full row.
    @Query("SELECT fr.assetSerialNumber FROM tb_FinancialReport fr WHERE fr.assetSerialNumber IS NOT NULL")
    List<String> findAllAssetSerialNumbers();

    @Query("SELECT fr.assetName FROM tb_FinancialReport fr WHERE fr.assetName IS NOT NULL")
    List<String> findAllAssetNames();

    // New methods
    // Updated methods to match entity field types: dateOfService (String), retirementDate (Date)
    @Query("SELECT r FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonthStr AND " +
            "(r.retirementDate IS NULL OR r.retirementDate >= :retirementDate) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<tb_FinancialReport> findByDateOfServiceBeforeAndRetirementDateIsNullOrAfter(
            @Param("endOfMonthStr") String endOfMonthStr,
            @Param("search") String search,
            @Param("retirementDate") Date retirementDate,
            Pageable pageable);

    @Query("SELECT r FROM tb_FinancialReport r WHERE r.dateOfService <= :endOfMonthStr AND " +
            "(r.retirementDate IS NULL OR r.retirementDate >= :retirementDate)")
    Page<tb_FinancialReport> findByDateOfServiceBeforeAndRetirementDateIsNullOrAfter(
            @Param("endOfMonthStr") String endOfMonthStr,
            @Param("retirementDate") Date retirementDate,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.initialCost), 0) FROM tb_FinancialReport r WHERE " +
            "r.dateOfService <= :endOfMonthStr AND " +
            "(r.retirementDate IS NULL OR r.retirementDate >= :retirementDate)")
    BigDecimal findTotalCostByDateOfServiceBeforeAndRetirementDateIsNullOrAfter(
            @Param("endOfMonthStr") String endOfMonthStr,
            @Param("retirementDate") Date retirementDate);

    @Query("SELECT COALESCE(SUM(r.initialCost), 0) FROM tb_FinancialReport r WHERE " +
            "r.dateOfService <= :endOfMonthStr AND " +
            "(r.retirementDate IS NULL OR r.retirementDate >= :retirementDate) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    BigDecimal findTotalCostByDateOfServiceBeforeAndRetirementDateIsNullOrAfter(
            @Param("endOfMonthStr") String endOfMonthStr,
            @Param("search") String search,
            @Param("retirementDate") Date retirementDate);

    @Query("SELECT COALESCE(SUM(r.accumulatedDepreciation), 0) FROM tb_FinancialReport r WHERE " +
            "r.dateOfService <= :endOfMonthStr AND " +
            "(r.retirementDate IS NULL OR r.retirementDate >= :retirementDate)")
    BigDecimal findTotalDepreciationByDateOfServiceBeforeAndRetirementDateIsNullOrAfter(
            @Param("endOfMonthStr") String endOfMonthStr,
            @Param("retirementDate") Date retirementDate);

    @Query("SELECT COALESCE(SUM(r.accumulatedDepreciation), 0) FROM tb_FinancialReport r WHERE " +
            "r.dateOfService <= :endOfMonthStr AND " +
            "(r.retirementDate IS NULL OR r.retirementDate >= :retirementDate) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    BigDecimal findTotalDepreciationByDateOfServiceBeforeAndRetirementDateIsNullOrAfter(
            @Param("endOfMonthStr") String endOfMonthStr,
            @Param("search") String search,
            @Param("retirementDate") Date retirementDate);

    @Query("SELECT COALESCE(SUM(r.monthlyDepreciationAmount), 0) FROM tb_FinancialReport r WHERE " +
            "r.dateOfService <= :endOfMonthStr AND " +
            "(r.retirementDate IS NULL OR r.retirementDate >= :retirementDate)")
    BigDecimal findTotalMonthlyDepreciationByDateOfServiceBeforeAndRetirementDateIsNullOrAfter(
            @Param("endOfMonthStr") String endOfMonthStr,
            @Param("retirementDate") Date retirementDate);

    @Query("SELECT COALESCE(SUM(r.monthlyDepreciationAmount), 0) FROM tb_FinancialReport r WHERE " +
            "r.dateOfService <= :endOfMonthStr AND " +
            "(r.retirementDate IS NULL OR r.retirementDate >= :retirementDate) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(r.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(r.assetSerialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    BigDecimal findTotalMonthlyDepreciationByDateOfServiceBeforeAndRetirementDateIsNullOrAfter(
            @Param("endOfMonthStr") String endOfMonthStr,
            @Param("search") String search,
            @Param("retirementDate") Date retirementDate);
}



