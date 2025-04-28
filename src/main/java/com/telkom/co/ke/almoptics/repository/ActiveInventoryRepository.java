package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.ActiveInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Repository for Active Inventory
 *
 * @author Gilian
 */
@Repository
public interface ActiveInventoryRepository extends JpaRepository<ActiveInventory, Integer> {

    /**
     * Finds assets in Active Inventory using Serial Number.
     *
     * @param serialNumber The Serial Number of the asset
     * @return List of matching Active Inventory records
     */
    List<ActiveInventory> findBySerialNumber(String serialNumber);

    /**
     * JPQL Query to find records by Serial Number
     */
    @Query("SELECT a FROM ActiveInventory a WHERE a.serialNumber = :serialNumber")
    List<ActiveInventory> findBySerialNumberJPQL(@Param("serialNumber") String serialNumber);
}