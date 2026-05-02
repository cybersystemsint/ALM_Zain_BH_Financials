package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.UnmappedActiveInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing UnmappedActiveInventory entities.
 * Provides methods to search by various attributes.
 *
 * @author Gilian
 */
@Repository
public interface UnmappedActiveInventoryRepository
        extends JpaRepository<UnmappedActiveInventory, Integer>, JpaSpecificationExecutor<UnmappedActiveInventory> {

    /**
     * Find unmapped active inventory by serial number.
     *
     * @param serialNumber The Serial Number of the asset.
     * @return Optional containing UnmappedActiveInventory if found.
     */
    Optional<UnmappedActiveInventory> findBySerialNumber(String serialNumber);

    /**
     * Find all unmapped active inventory ordered by insert date in descending order.
     *
     * @return List of UnmappedActiveInventory records.
     */
    List<UnmappedActiveInventory> findAllByOrderByInsertDateDesc();

    /**
     * Find unmapped active inventory by node name.
     *
     * @param nodeName The Node Name of the asset.
     * @return List of matching UnmappedActiveInventory records.
     */
    List<UnmappedActiveInventory> findByNodeName(String nodeName);

    /**
     * Find unmapped active inventory by site ID.
     *
     * @param siteId The Site ID of the asset.
     * @return List of matching UnmappedActiveInventory records.
     */
    List<UnmappedActiveInventory> findBySiteId(String siteId);

    /**
     * Find all unmapped active inventory records by serial number.
     *
     * @param serialNumber The Serial Number of the asset.
     * @return List of matching UnmappedActiveInventory records.
     */
    List<UnmappedActiveInventory> findAllBySerialNumber(String serialNumber);

    @Modifying
    @Query("DELETE FROM UnmappedActiveInventory u WHERE u.serialNumber = :serialNumber")
    void deleteBySerialNumber(@Param("serialNumber") String serialNumber);

    // ------------------------------------------------------------------
    // Bulk-delta queries used by SyncOrchestratorService. They keep the
    // unmapped table in lock-step with (tb_Node \ tb_FinancialReport)
    // without ever wiping the whole table — so the Unmapped report
    // always has data, no "blink" window.
    // ------------------------------------------------------------------

    /** Insert into unmapped every active asset that is in tb_Node but not yet
     *  in tb_FinancialReport and not yet in unmapped.
     *
     *  NOTE on COLLATE clauses: tb_FinancialReport (created on MySQL 8) uses
     *  utf8mb4_0900_ai_ci while tb_Node and tb_unmappednode (created on
     *  MySQL 5.7 originally) use utf8mb4_unicode_ci. MySQL refuses cross-
     *  collation string comparison ("Illegal mix of collations" — error
     *  1267). Forcing both sides to utf8mb4_unicode_ci normalises the
     *  comparison. The minor index-skip overhead is acceptable since these
     *  delta queries run only on demand. */
    @Modifying
    @Query(value = "INSERT INTO `tb_unmappednode` " +
            "  (SiteId, NodeName, AssetName, AssetType, NodeType, Manufacturer, Model, " +
            "   PartNumber, SerialNumber, Description, ManufacturingDate, InsertedBy, InsertDate) " +
            "SELECT n.siteId, n.NodeName, n.NodeName, n.NodeType, n.NodeType, n.Manufacturer, " +
            "       n.Model, n.PartNumber, n.SerialNumber, n.Description, n.ManufacturingDate, " +
            "       'SYSTEM', NOW() " +
            "FROM tb_Node n " +
            "WHERE n.SerialNumber IS NOT NULL " +
            "  AND NOT EXISTS (SELECT 1 FROM tb_FinancialReport fr " +
            "                  WHERE fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                      = n.SerialNumber COLLATE utf8mb4_unicode_ci) " +
            "  AND NOT EXISTS (SELECT 1 FROM `tb_unmappednode` u " +
            "                  WHERE u.SerialNumber COLLATE utf8mb4_unicode_ci " +
            "                      = n.SerialNumber COLLATE utf8mb4_unicode_ci)",
            nativeQuery = true)
    int bulkInsertMissingFromSource();

    /** Delete unmapped rows for assets that now exist in tb_FinancialReport. */
    @Modifying
    @Query(value = "DELETE u FROM `tb_unmappednode` u " +
            "INNER JOIN tb_FinancialReport fr " +
            "  ON fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "      = u.SerialNumber COLLATE utf8mb4_unicode_ci " +
            "  OR fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "      = u.SerialNumber COLLATE utf8mb4_unicode_ci",
            nativeQuery = true)
    int bulkDeleteMappedRows();
}