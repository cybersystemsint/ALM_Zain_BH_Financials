package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.PassiveInventory;

import java.util.List;
import java.util.Optional;

@Repository
public interface PassiveInventoryRepository extends JpaRepository<PassiveInventory, String> {
    Optional<PassiveInventory> findByObjectId(String objectId);
    Optional<PassiveInventory> findBySerial(String serial);
    List<PassiveInventory> findAllByOrderByLastModifiedDateDesc();

    @Query("SELECT p FROM PassiveInventory p WHERE p.objectId = :objectId OR p.serial = :serialNumber")
    List<PassiveInventory> findByObjectIdOrSerialNumber(
            @Param("objectId") String objectId,
            @Param("serialNumber") String serialNumber);

    @Override
    @Query("SELECT p FROM PassiveInventory p")
    List<PassiveInventory> findAll();

    @Query("SELECT p FROM PassiveInventory p WHERE p.serial IN :identifiers OR p.objectId IN :identifiers")
    List<PassiveInventory> findBySerialOrObjectIdIn(@Param("identifiers") List<String> identifiers);
}
