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
}