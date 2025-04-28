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

}