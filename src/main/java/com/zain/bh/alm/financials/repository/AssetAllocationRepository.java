package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.AssetAllocation;

import java.util.List;

@Repository
public interface AssetAllocationRepository extends JpaRepository<AssetAllocation, Long> {
    AssetAllocation findByLocationId(String locationId);
    AssetAllocation findByAssetCode(String assetCode);
    AssetAllocation findByPersonId(String personId);
    List<AssetAllocation> findBystatus(String status);
}
