package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.UnmappedPassiveInventory;

import java.util.Optional;

@Repository
public interface UnmappedPassiveInventoryRepository
        extends JpaRepository<UnmappedPassiveInventory, Long>, JpaSpecificationExecutor<UnmappedPassiveInventory> {
    Optional<UnmappedPassiveInventory> findBySerial(String serial);
    Optional<UnmappedPassiveInventory> findByObjectId(String objectId);
    Optional<UnmappedPassiveInventory> findByElementType(String elementType);

    @Query("SELECT u FROM UnmappedPassiveInventory u WHERE u.serial = :serial OR u.objectId = :objectId")
    Optional<UnmappedPassiveInventory> findBySerialOrObjectId(@Param("serial") String serial, @Param("objectId") String objectId);

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
