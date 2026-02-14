package com.zain.bh.alm.financials.service;

import java.util.List;

import com.zain.bh.alm.financials.entity.AssetAllocation;

public interface AssetAllocationService {

    List<AssetAllocation> findAll();

    AssetAllocation save(AssetAllocation assetAllocation);

    List<AssetAllocation> findByStatus(String status);

    AssetAllocation findByLocationId(String locationId);

    AssetAllocation findByPersonId(String personId);

    AssetAllocation findByAssetCode(String assetCode);
}
