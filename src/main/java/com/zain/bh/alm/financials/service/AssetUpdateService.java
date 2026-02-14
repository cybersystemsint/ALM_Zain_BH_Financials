package com.zain.bh.alm.financials.service;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.Asset;

@Service
public class AssetUpdateService {

	private final AssetService assetService;

	public AssetUpdateService(AssetService assetService) {
		this.assetService = assetService;
	}

	public void updateWarranty(String assetCode, String warrantExpiryDate, String warrantyDetails) {
		Asset asset = assetService.findByAssetCode(assetCode);
		asset.setWarrantExpiryDate(warrantExpiryDate);
		asset.setWarrantyDetails(warrantyDetails);
		assetService.save(asset);
	}

	public void updateDepreciation(String assetCode, String depreciationModel, String salvageValue, String usefulLife) {
		Asset asset = assetService.findByAssetCode(assetCode);
		asset.setDepreciationModel(depreciationModel);
		asset.setSalvageValue(salvageValue);
		asset.setUsefulLife(usefulLife);
		assetService.save(asset);
	}
}
