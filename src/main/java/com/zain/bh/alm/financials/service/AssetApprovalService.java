package com.zain.bh.alm.financials.service;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.Asset;
import com.zain.bh.alm.financials.entity.Item;

@Service
public class AssetApprovalService {

	private final AssetService assetService;
	private final ItemService itemService;
	private final DepreciationCalculationService depreciationCalculationService;

	public AssetApprovalService(AssetService assetService, ItemService itemService,
			DepreciationCalculationService depreciationCalculationService) {
		this.assetService = assetService;
		this.itemService = itemService;
		this.depreciationCalculationService = depreciationCalculationService;
	}

	public JSONArray getApprovedAssets(String status) {
		JSONArray response = new JSONArray();
		List<Asset> assets = assetService.findByStatus(status);

		for (Asset asset : assets) {
			JSONObject assetObj = buildAssetObject(asset, true);
			response.put(assetObj);
		}

		return response;
	}

	public JSONArray getAssetsForApproval(String status) {
		JSONArray response = new JSONArray();
		List<Asset> assets = assetService.findByStatus(status);

		for (Asset asset : assets) {
			JSONObject assetObj = buildAssetObject(asset, false);
			response.put(assetObj);
		}

		return response;
	}

	private JSONObject buildAssetObject(Asset asset, boolean includeBookValue) {
		JSONObject obj = new JSONObject();
		Item item = itemService.findByItemCode(asset.getAssetCode());

		obj.put("assetCode", asset.getAssetCode());
		obj.put("inventoryId", asset.getInventoryId());
		obj.put("serialNumber", asset.getSerialNumber());
		obj.put("purchasePrice", asset.getPurchasePrice());
		obj.put("poId", asset.getPoId());
		obj.put("createdBy", asset.getCreatedBy());
		obj.put("supplierId", asset.getSupplierId());
		obj.put("purchaseDate", asset.getPurchaseDate());
		obj.put("warrantyDetails", asset.getWarrantyDetails());
		obj.put("warrantExpiryDate", asset.getWarrantExpiryDate());
		obj.put("depreciationMethod", item != null ? item.getDepreciationMethod() : "Not Set");
		obj.put("details", asset.getDetails());

		if (includeBookValue && item != null) {
			double bookValue = calculateBookValue(asset, item);
			obj.put("bookValue", (int) Math.round(bookValue));
		}

		return obj;
	}

	private double calculateBookValue(Asset asset, Item item) {
		LocalDate purchaseDate = asset.getRecordDatetime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		int yearDiff = Period.between(purchaseDate, LocalDate.now()).getYears();
		double salvageValue = Double.parseDouble(asset.getSalvageValue());
		double purchasePrice = asset.getPurchasePrice();

		double bookValue;
		if (item.getDepreciationMethod().toLowerCase().startsWith("r")) {
			bookValue = depreciationCalculationService.calculateReducingBalanceDepreciation(purchasePrice, salvageValue, yearDiff);
		} else {
			int usefulLife = Integer.parseInt(asset.getUsefulLife());
			double totalDepreciation = depreciationCalculationService.calculateTotalDepreciation(
					purchasePrice, salvageValue, usefulLife, yearDiff);
			bookValue = purchasePrice - totalDepreciation;
		}

		return Math.max(bookValue, salvageValue);
	}
}
