package com.zain.bh.alm.financials.service;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.Asset;
import com.zain.bh.alm.financials.entity.AssetAllocation;

@Service
public class AssetDisposalService {

	private final AssetService assetService;
	private final AssetAllocationService assetAllocationService;
	private final MyAsyncService myAsyncService;

	public AssetDisposalService(AssetService assetService, AssetAllocationService assetAllocationService,
			MyAsyncService myAsyncService) {
		this.assetService = assetService;
		this.assetAllocationService = assetAllocationService;
		this.myAsyncService = myAsyncService;
	}

	public void decommissionAsset(String assetCode, String updatedBy) {
		Asset asset = assetService.findByAssetCode(assetCode);
		asset.setStatus("DECOMMISSIONED");
		assetService.save(asset);

		AssetAllocation allocation = assetAllocationService.findByAssetCode(assetCode);
		if (allocation != null) {
			allocation.setStatus("DECOMMISSIONED");
			assetAllocationService.save(allocation);
		}

		JSONObject warehouseRequest = new JSONObject();
		warehouseRequest.put("assetCode", assetCode);
		warehouseRequest.put("statusName", "Decommissioned");
		warehouseRequest.put("updatedBy", updatedBy);
		myAsyncService.httpPOST("http://10.22.25.92:8080/ALMWarehousing/updateInventoryStatus",
				warehouseRequest.toString());
	}
}
