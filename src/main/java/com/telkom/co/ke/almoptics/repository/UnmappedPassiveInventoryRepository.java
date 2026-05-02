package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.UnmappedPassiveInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Unmapped Passive Inventory
 *
 * @author Gilian
 */
@Repository
public interface UnmappedPassiveInventoryRepository
        extends JpaRepository<UnmappedPassiveInventory, Long>, JpaSpecificationExecutor<UnmappedPassiveInventory> {

    /**
     * Finds an unmapped passive inventory by serial number.
     *
     * @param serial The serial number to search for.
     * @return Optional containing the unmapped passive inventory if found.
     */
    Optional<UnmappedPassiveInventory> findBySerial(String serial);

    /**
     * Finds an unmapped passive inventory by object ID.
     *
     * @param objectId The object ID to search for.
     * @return Optional containing the unmapped passive inventory if found.
     */
    Optional<UnmappedPassiveInventory> findByObjectId(String objectId);

    /**
     * Finds an unmapped passive inventory by serial number or object ID.
     *
     * @param serial   The serial number to search for.
     * @param objectId The object ID to search for.
     * @return Optional containing the unmapped passive inventory if found.
     */
    @Query("SELECT u FROM UnmappedPassiveInventory u WHERE u.serial = :serial OR u.objectId = :objectId")
    Optional<UnmappedPassiveInventory> findBySerialOrObjectId(@Param("serial") String serial, @Param("objectId") String objectId);

    /**
     * Finds an unmapped passive inventory by element type.
     *
     * @param elementType The element type to search for.
     * @return Optional containing the unmapped passive inventory if found.
     */
    Optional<UnmappedPassiveInventory> findByElementType(String elementType);

    @Modifying
    @Query("DELETE FROM UnmappedPassiveInventory u WHERE u.serial = :serial")
    void deleteBySerial(@Param("serial") String serial);

    @Modifying
    @Query("DELETE FROM UnmappedPassiveInventory u WHERE u.objectId = :objectId")
    void deleteByObjectId(@Param("objectId") String objectId);

    @Modifying
    @Query("DELETE FROM UnmappedPassiveInventory u WHERE u.elementType = :elementType")
    void deleteByElementType(@Param("elementType") String elementType);

    // ------------------------------------------------------------------
    // Bulk-delta queries used by SyncOrchestratorService.
    // ------------------------------------------------------------------

    /** Insert into unmapped every passive asset present in tb_Passive_Inventory
     *  but absent from both tb_FinancialReport and the unmapped table.
     *  COLLATE clauses normalise utf8mb4_0900_ai_ci (FR) vs utf8mb4_unicode_ci
     *  (passive tables) — see UnmappedActiveInventoryRepository for context. */
    @Modifying
    @Query(value = "INSERT INTO `tb_unmappedPassive_Inventory` " +
            "  (ElementID, Element_Type, Site_ID, Category, Item_BarCode, Serial, UOM, " +
            "   Entry_Date, Entry_User, Model, Item_Classification, Item_Classification_2, " +
            "   Notes, PR_PONo) " +
            "SELECT p.ObjectID, 'PASSIVE', p.SiteId, p.CategoryInNEP, p.ItemBarCode, p.Serial, p.UOM, " +
            "       NOW(), 'SYSTEM', p.Model, p.ItemClassification, p.ItemClassification2, " +
            "       p.Notes, p.`PR/PONo` " +
            "FROM tb_Passive_Inventory p " +
            "WHERE (p.Serial IS NOT NULL OR p.ObjectID IS NOT NULL) " +
            "  AND NOT EXISTS (SELECT 1 FROM tb_FinancialReport fr " +
            "                   WHERE fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                       = p.Serial COLLATE utf8mb4_unicode_ci " +
            "                      OR fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                       = p.ObjectID COLLATE utf8mb4_unicode_ci " +
            "                      OR fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "                       = p.Serial COLLATE utf8mb4_unicode_ci " +
            "                      OR fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "                       = p.ObjectID COLLATE utf8mb4_unicode_ci) " +
            "  AND NOT EXISTS (SELECT 1 FROM `tb_unmappedPassive_Inventory` u " +
            "                   WHERE u.Serial COLLATE utf8mb4_unicode_ci " +
            "                       = p.Serial COLLATE utf8mb4_unicode_ci " +
            "                      OR u.ElementID COLLATE utf8mb4_unicode_ci " +
            "                       = p.ObjectID COLLATE utf8mb4_unicode_ci)",
            nativeQuery = true)
    int bulkInsertMissingFromSource();

    /** Delete unmapped passive rows that now appear in tb_FinancialReport. */
    @Modifying
    @Query(value = "DELETE u FROM `tb_unmappedPassive_Inventory` u " +
            "INNER JOIN tb_FinancialReport fr " +
            "  ON fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "      = u.Serial COLLATE utf8mb4_unicode_ci " +
            "  OR fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "      = u.ElementID COLLATE utf8mb4_unicode_ci " +
            "  OR fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "      = u.Serial COLLATE utf8mb4_unicode_ci " +
            "  OR fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "      = u.ElementID COLLATE utf8mb4_unicode_ci",
            nativeQuery = true)
    int bulkDeleteMappedRows();
}