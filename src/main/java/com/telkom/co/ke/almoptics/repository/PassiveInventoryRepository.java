package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.PassiveInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PassiveInventory entities.
 * @author Gilian
 */
public interface PassiveInventoryRepository extends JpaRepository<PassiveInventory, String> {

    // Find by ObjectID (primary key, VARCHAR)
    Optional<PassiveInventory> findByObjectId(String objectId);

    // Find by Serial (unique constraint)
    Optional<PassiveInventory> findBySerial(String serial);

    // Find all ordered by LastModifiedDate descending
    List<PassiveInventory> findAllByOrderByLastModifiedDateDesc();

    /**
     * Check if an asset exists in Passive Inventory using Object ID or Serial Number.
     *
     * @param objectId     The Object ID of the asset (VARCHAR).
     * @param serialNumber The Serial Number of the asset (String).
     * @return List of matching Passive Inventory records.
     */
    @Query("SELECT p FROM PassiveInventory p WHERE p.objectId = :objectId OR p.serial = :serialNumber")
    List<PassiveInventory> findByObjectIdOrSerialNumber(
            @Param("objectId") String objectId,
            @Param("serialNumber") String serialNumber
    );

    /**
     * Retrieve all PassiveInventory entities.
     *
     * @return List of all PassiveInventory entities.
     */
    @Override
    @Query("SELECT p FROM PassiveInventory p")
    List<PassiveInventory> findAll();

    /**
     * Find Passive Inventory records by a list of Serial Numbers or Object IDs.
     *
     * @param identifiers List of Serial Numbers or Object IDs to search for
     * @return List of matching Passive Inventory records
     */
    @Query("SELECT p FROM PassiveInventory p WHERE p.serial IN :identifiers OR p.objectId IN :identifiers")
    List<PassiveInventory> findBySerialOrObjectIdIn(@Param("identifiers") List<String> identifiers);
}


//package com.telkom.co.ke.almoptics.repository;
//
//import com.telkom.co.ke.almoptics.entities.PassiveInventory;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//
//import java.util.List;
//import java.util.Optional;
//
///**
// * Repository for PassiveInventory entities.
// * @author Gilian
// */
//public interface PassiveInventoryRepository extends JpaRepository<PassiveInventory, String> {
//
//    // Find by ObjectID (primary key, VARCHAR)
//    Optional<PassiveInventory> findByObjectId(String objectId);
//
//    // Find by Serial (unique constraint)
//    Optional<PassiveInventory> findBySerial(String serial);
//
//    // Find all ordered by LastModifiedDate descending
//    List<PassiveInventory> findAllByOrderByLastModifiedDateDesc();
//
//    /**
//     * Check if an asset exists in Passive Inventory using Object ID or Serial Number.
//     *
//     * @param objectId     The Object ID of the asset (VARCHAR).
//     * @param serialNumber The Serial Number of the asset (String).
//     * @return List of matching Passive Inventory records.
//     */
//    @Query("SELECT p FROM PassiveInventory p WHERE p.objectId = :objectId OR p.serial = :serialNumber")
//    List<PassiveInventory> findByObjectIdOrSerialNumber(
//            @Param("objectId") String objectId,
//            @Param("serialNumber") String serialNumber
//    );
//
//    /**
//     * Retrieve all PassiveInventory entities.
//     *
//     * @return List of all PassiveInventory entities.
//     */
//    @Override
//    @Query("SELECT p FROM PassiveInventory p")
//    List<PassiveInventory> findAll();
//}