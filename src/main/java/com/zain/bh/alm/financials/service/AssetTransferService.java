package com.zain.bh.alm.financials.service;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.Asset;

@Service
public class AssetTransferService {

	private final AssetService assetService;

	public AssetTransferService(AssetService assetService) {
		this.assetService = assetService;
	}

	public void transferAsset(String assetCode, String inventoryId, String serialNumber, float purchasePrice,
			String poId, String createdBy, String supplierId, String purchaseDate, String warrantyDetails,
			String warrantExpiryDate, String details, String status) throws Exception {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		Asset asset = new Asset();
		asset.setAssetCode(assetCode);
		asset.setCreatedBy(createdBy);
		asset.setInventoryId(inventoryId);
		asset.setPoId(poId);
		asset.setSalvageValue("0");
		asset.setPurchaseDate(format.parse(purchaseDate));
		asset.setPurchasePrice(purchasePrice);
		asset.setRecordDatetime(format.parse(purchaseDate));
		asset.setWarrantExpiryDate(warrantExpiryDate);
		asset.setSerialNumber(serialNumber);
		asset.setApproved(false);
		asset.setUsefulLife("0");
		asset.setStatus(status);
		asset.setSupplierId(supplierId);
		asset.setWarrantyDetails(warrantyDetails);
		assetService.save(asset);
	}

	public void approveTransfer(String serialNumber, String status, String approvedBy) {
		Asset asset = assetService.findBySerialNumber(serialNumber);
		asset.setApprovalDate(new Date());
		asset.setStatus(status);
		asset.setApprovedBy(approvedBy);
		asset.setApproved(true);
		assetService.save(asset);
	}
}
