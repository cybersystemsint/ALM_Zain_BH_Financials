package com.zain.bh.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.Asset;
import com.zain.bh.alm.financials.repository.AssetRepository;
import com.zain.bh.alm.financials.service.AssetService;

@Service
@Transactional
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;

    public AssetServiceImpl(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public List<Asset> findAll() {
        return assetRepository.findAll();
    }

    public Asset save(Asset asset) {
        return assetRepository.save(asset);
    }

    public Asset findBySerialNumber(String serialNumber) {
        return assetRepository.findBySerialNumber(serialNumber);
    }

    public Asset findBySupplierId(String supplierId) {
        return assetRepository.findBySupplierId(supplierId);
    }

    public Asset findByPoId(String poId) {
        return assetRepository.findByPoId(poId);
    }

    public List<Asset> findByStatus(String status) {
        return assetRepository.findByStatus(status);
    }

    public Asset findByAssetCode(String assetCode) {
        return assetRepository.findByAssetCode(assetCode);
    }
}
