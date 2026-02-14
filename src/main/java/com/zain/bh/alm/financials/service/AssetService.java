package com.zain.bh.alm.financials.service;

import java.util.List;

import com.zain.bh.alm.financials.entity.Asset;

public interface AssetService {

    List<Asset> findAll();

    Asset save(Asset asset);

    Asset findBySerialNumber(String serialNumber);

    Asset findBySupplierId(String supplierId);

    Asset findByPoId(String poId);

    List<Asset> findByStatus(String status);

    Asset findByAssetCode(String assetCode);
}
