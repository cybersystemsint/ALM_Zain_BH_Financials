package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.ITInventory;

import java.util.List;
import java.util.Optional;

@Repository
public interface ITInventoryRepository extends JpaRepository<ITInventory, Long> {
    Optional<ITInventory> findByObjectId(String objectId);
    Optional<ITInventory> findByHostSerialNumber(String hostSerialNumber);

    @Query("SELECT it FROM ITInventory it WHERE it.objectId = :objectId OR it.hostSerialNumber = :hostSerialNumber")
    List<ITInventory> findByObjectIdOrHostSerialNumber(@Param("objectId") String objectId, @Param("hostSerialNumber") String hostSerialNumber);
}
