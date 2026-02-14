package com.zain.bh.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.AssetAllocation;
import com.zain.bh.alm.financials.repository.AssetAllocationRepository;
import com.zain.bh.alm.financials.service.AssetAllocationService;

@Service
@Transactional
public class AssetAllocationServiceImpl implements AssetAllocationService {

    private final AssetAllocationRepository assetAllocationRepository;

    public AssetAllocationServiceImpl(AssetAllocationRepository assetAllocationRepository) {
        this.assetAllocationRepository = assetAllocationRepository;
    }

    public List<AssetAllocation> findAll() {
        return assetAllocationRepository.findAll();
    }

    public AssetAllocation save(AssetAllocation assetAllocation) {
        return assetAllocationRepository.save(assetAllocation);
    }

    public List<AssetAllocation> findByStatus(String status) {
        return assetAllocationRepository.findBystatus(status);
    }

    public AssetAllocation findByLocationId(String locationId) {
        return assetAllocationRepository.findByLocationId(locationId);
    }

    public AssetAllocation findByPersonId(String personId) {
        return assetAllocationRepository.findByPersonId(personId);
    }

    public AssetAllocation findByAssetCode(String assetCode) {
        return assetAllocationRepository.findByAssetCode(assetCode);
    }
}
