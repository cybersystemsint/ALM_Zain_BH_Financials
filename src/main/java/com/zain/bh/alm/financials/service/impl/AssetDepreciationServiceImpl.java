package com.zain.bh.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.AssetDepreciation;
import com.zain.bh.alm.financials.repository.AssetDepreciationRepository;
import com.zain.bh.alm.financials.service.AssetDepreciationService;

@Service
@Transactional
public class AssetDepreciationServiceImpl implements AssetDepreciationService {

    private final AssetDepreciationRepository assetDepreciationRepository;

    public AssetDepreciationServiceImpl(AssetDepreciationRepository assetDepreciationRepository) {
        this.assetDepreciationRepository = assetDepreciationRepository;
    }

    public List<AssetDepreciation> findAll() {
        return assetDepreciationRepository.findAll();
    }

    public AssetDepreciation save(AssetDepreciation assetDepreciation) {
        return assetDepreciationRepository.save(assetDepreciation);
    }

    public AssetDepreciation findByAssetCode(String assetCode) {
        return assetDepreciationRepository.findByAssetCode(assetCode);
    }
}
