package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.UnmappedITInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Unmapped IT Inventory
 *
 * @author Gilian
 */
@Repository
public interface UnmappedITInventoryRepository
        extends JpaRepository<UnmappedITInventory, Long>, JpaSpecificationExecutor<UnmappedITInventory> {

    /**
     * Finds an unmapped IT inventory by hardware serial number.
     *
     * @param hardwareSerialNumber The hardware serial number to search for.
     * @return Optional containing the unmapped IT inventory if found.
     */
    Optional<UnmappedITInventory> findByHardwareSerialNumber(String hardwareSerialNumber);

    /**
     * Finds all unmapped IT inventory records by hardware serial number.
     *
     * @param hardwareSerialNumber The hardware serial number to search for.
     * @return List of matching UnmappedITInventory records.
     */
    List<UnmappedITInventory> findAllByHardwareSerialNumber(String hardwareSerialNumber);

    /**
     * Finds an unmapped IT inventory by host serial number.
     *
     * @param hostSerialNumber The host serial number to search for.
     * @return Optional containing the unmapped IT inventory if found.
     */
    Optional<UnmappedITInventory> findByHostSerialNumber(String hostSerialNumber);

    /**
     * Finds an unmapped IT inventory by element ID.
     *
     * @param elementId The element ID to search for.
     * @return Optional containing the unmapped IT inventory if found.
     */
    Optional<UnmappedITInventory> findByElementId(String elementId);

    /**
     * Finds an unmapped IT inventory by host name.
     *
     * @param hostName The host name to search for.
     * @return Optional containing the unmapped IT inventory if found.
     */
    Optional<UnmappedITInventory> findByHostName(String hostName);

    @Modifying
    @Query("DELETE FROM UnmappedITInventory u WHERE u.hostSerialNumber = :hostSerialNumber")
    void deleteByHostSerialNumber(@Param("hostSerialNumber") String hostSerialNumber);

    @Modifying
    @Query("DELETE FROM UnmappedITInventory u WHERE u.hardwareSerialNumber = :hardwareSerialNumber")
    void deleteByHardwareSerialNumber(@Param("hardwareSerialNumber") String hardwareSerialNumber);

    @Modifying
    @Query("DELETE FROM UnmappedITInventory u WHERE u.elementId = :elementId")
    void deleteByElementId(@Param("elementId") String elementId);

    @Modifying
    @Query("DELETE FROM UnmappedITInventory u WHERE u.hostName = :hostName")
    void deleteByHostName(@Param("hostName") String hostName);

    @Query("SELECT u FROM UnmappedITInventory u WHERE u.hardwareSerialNumber = :hardwareSerialNumber OR u.elementId = :elementId")
    Optional<UnmappedITInventory> findByHardwareSerialNumberOrElementId(
            @Param("hardwareSerialNumber") String hardwareSerialNumber,
            @Param("elementId") String elementId);

    // ------------------------------------------------------------------
    // Bulk-delta queries used by SyncOrchestratorService.
    // ------------------------------------------------------------------

    /** Insert into unmapped every IT asset present in vw_IT_Inventory but absent
     *  from both tb_FinancialReport and the unmapped IT table.
     *  COLLATE clauses normalise utf8mb4_0900_ai_ci (FR) vs utf8mb4_unicode_ci
     *  (IT tables) — see UnmappedActiveInventoryRepository for context. */
    @Modifying
    @Query(value = "INSERT INTO `tb_unmappedIT_INVENTORY` " +
            "  (Element_ID, Element_Type, Site_ID, OS, Hardware_Vendor, Host_Type, " +
            "   Host_Serial_Number, Hardware_Serial_Number, Asset_Insert_Date, IP_Address, " +
            "   Model, Manufacturer, Last_Update_Success, Warranty) " +
            "SELECT i.ObjectID, 'IT', i.SiteId, i.OS, i.HardwareVendor, i.HostType, " +
            "       i.HostSerialNumber, i.HostSerialNumber, NOW(), i.IPAddress, " +
            "       i.Model, i.HardwareVendor, i.LastUpdateSuccess, 'Unknown' " +
            "FROM vw_IT_Inventory i " +
            "WHERE (i.HostSerialNumber IS NOT NULL OR i.ObjectID IS NOT NULL) " +
            "  AND NOT EXISTS (SELECT 1 FROM tb_FinancialReport fr " +
            "                   WHERE fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                       = i.HostSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                      OR fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                       = i.ObjectID COLLATE utf8mb4_unicode_ci " +
            "                      OR fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "                       = i.HostSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                      OR fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "                       = i.ObjectID COLLATE utf8mb4_unicode_ci) " +
            "  AND NOT EXISTS (SELECT 1 FROM `tb_unmappedIT_INVENTORY` u " +
            "                   WHERE u.Hardware_Serial_Number COLLATE utf8mb4_unicode_ci " +
            "                       = i.HostSerialNumber COLLATE utf8mb4_unicode_ci " +
            "                      OR u.Element_ID COLLATE utf8mb4_unicode_ci " +
            "                       = i.ObjectID COLLATE utf8mb4_unicode_ci)",
            nativeQuery = true)
    int bulkInsertMissingFromSource();

    /** Delete unmapped IT rows that now appear in tb_FinancialReport. */
    @Modifying
    @Query(value = "DELETE u FROM `tb_unmappedIT_INVENTORY` u " +
            "INNER JOIN tb_FinancialReport fr " +
            "  ON fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "      = u.Hardware_Serial_Number COLLATE utf8mb4_unicode_ci " +
            "  OR fr.AssetSerialNumber COLLATE utf8mb4_unicode_ci " +
            "      = u.Element_ID COLLATE utf8mb4_unicode_ci " +
            "  OR fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "      = u.Hardware_Serial_Number COLLATE utf8mb4_unicode_ci " +
            "  OR fr.AssetName COLLATE utf8mb4_unicode_ci " +
            "      = u.Element_ID COLLATE utf8mb4_unicode_ci",
            nativeQuery = true)
    int bulkDeleteMappedRows();
}