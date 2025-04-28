package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.ItInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

/**
 *
 * @author Gilian
 */
@Repository
public interface ItInventoryRepository extends JpaRepository<ItInventory, Long> {

    Optional<ItInventory> findByObjectId(String objectId);

    // Corrected to match the entity field
    Optional<ItInventory> findByHostSerialNumber(String hostSerialNumber);

    /**
     * Check if an asset exists in IT Inventory using Object ID or Host Serial Number.
     *
     * @param objectId        The Object ID of the asset.
     * @param hostSerialNumber The Host Serial Number of the asset.
     * @return List of matching IT Inventory records.
     */
    @Query("SELECT it FROM ItInventory it WHERE it.objectId = :objectId OR it.hostSerialNumber = :hostSerialNumber")
    List<ItInventory> findByObjectIdOrHostSerialNumber(@Param("objectId") String objectId, @Param("hostSerialNumber") String hostSerialNumber);
}