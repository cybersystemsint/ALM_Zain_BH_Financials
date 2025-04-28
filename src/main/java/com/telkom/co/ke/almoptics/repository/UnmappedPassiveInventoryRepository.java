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
}