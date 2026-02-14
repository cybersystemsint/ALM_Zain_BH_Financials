package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.UnmappedITInventory;

import java.util.Optional;

@Repository
public interface UnmappedITInventoryRepository
        extends JpaRepository<UnmappedITInventory, Long>, JpaSpecificationExecutor<UnmappedITInventory> {
    Optional<UnmappedITInventory> findByHardwareSerialNumber(String hardwareSerialNumber);
    Optional<UnmappedITInventory> findByHostSerialNumber(String hostSerialNumber);
    Optional<UnmappedITInventory> findByElementId(String elementId);
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
