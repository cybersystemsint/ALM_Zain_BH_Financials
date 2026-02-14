package com.zain.bh.alm.financials.service;

import java.util.List;

import com.zain.bh.alm.financials.entity.AssetDepreciation;

public interface AssetDepreciationService {

	List<AssetDepreciation> findAll();

	AssetDepreciation save(AssetDepreciation assetDepreciation);

	AssetDepreciation findByAssetCode(String assetCode);
}