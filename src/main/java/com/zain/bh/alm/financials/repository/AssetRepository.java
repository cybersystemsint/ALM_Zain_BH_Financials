package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.Asset;

import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    Asset findBySerialNumber(String serialNumber);
    Asset findBySupplierId(String supplierId);
    Asset findByPoId(String poId);
    Asset findByAssetCode(String assetCode);
    List<Asset> findByStatus(String status);
}

