package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.UnmappedActiveInventory;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnmappedActiveInventoryRepository
        extends JpaRepository<UnmappedActiveInventory, Integer>, JpaSpecificationExecutor<UnmappedActiveInventory> {
    Optional<UnmappedActiveInventory> findBySerialNumber(String serialNumber);
    List<UnmappedActiveInventory> findAllByOrderByInsertDateDesc();
    List<UnmappedActiveInventory> findByNodeName(String nodeName);
    List<UnmappedActiveInventory> findBySiteId(String siteId);
    List<UnmappedActiveInventory> findAllBySerialNumber(String serialNumber);

    @Modifying
    @Query("DELETE FROM UnmappedActiveInventory u WHERE u.serialNumber = :serialNumber")
    void deleteBySerialNumber(@Param("serialNumber") String serialNumber);
}
