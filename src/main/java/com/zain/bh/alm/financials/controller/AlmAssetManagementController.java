package com.zain.bh.alm.financials.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zain.bh.alm.financials.entity.Asset;
import com.zain.bh.alm.financials.entity.AssetAllocation;
import com.zain.bh.alm.financials.entity.AssetJournal;
import com.zain.bh.alm.financials.entity.Item;
import com.zain.bh.alm.financials.service.AssetAllocationBusinessService;
import com.zain.bh.alm.financials.service.AssetAllocationService;
import com.zain.bh.alm.financials.service.AssetApprovalService;
import com.zain.bh.alm.financials.service.AssetDisposalService;
import com.zain.bh.alm.financials.service.AssetJournalBusinessService;
import com.zain.bh.alm.financials.service.AssetJournalService;
import com.zain.bh.alm.financials.service.AssetService;
import com.zain.bh.alm.financials.service.AssetTransferService;
import com.zain.bh.alm.financials.service.AssetUpdateService;
import com.zain.bh.alm.financials.service.DepreciationCalculationService;
import com.zain.bh.alm.financials.service.FarReportBusinessService;
import com.zain.bh.alm.financials.service.FinancialReportBusinessService;
import com.zain.bh.alm.financials.service.ItemService;
import com.zain.bh.alm.financials.util.ResponseBuilder;

@CrossOrigin(origins = { "*" }, maxAge = 3600L)
@RestController
@RequestMapping("/")
public class AlmAssetManagementController {

	private static final Logger LOGGER = LoggerFactory.getLogger(AlmAssetManagementController.class);

	private final AssetAllocationService assetAllocationService;
	private final AssetJournalService assetJournalService;
	private final AssetService assetService;
	private final ItemService itemService;
	private final DepreciationCalculationService depreciationCalculationService;
	private final AssetAllocationBusinessService assetAllocationBusinessService;
	private final AssetDisposalService assetDisposalService;
	private final AssetTransferService assetTransferService;
	private final AssetUpdateService assetUpdateService;
	private final AssetJournalBusinessService assetJournalBusinessService;
	private final FarReportBusinessService farReportBusinessService;
	private final FinancialReportBusinessService financialReportBusinessService;
	private final AssetApprovalService assetApprovalService;

	public AlmAssetManagementController(ItemService itemService, AssetAllocationService assetAllocationService,
			AssetJournalService assetJournalService, AssetService assetService,
			DepreciationCalculationService depreciationCalculationService,
			AssetAllocationBusinessService assetAllocationBusinessService, AssetDisposalService assetDisposalService,
			AssetTransferService assetTransferService, AssetUpdateService assetUpdateService,
			AssetJournalBusinessService assetJournalBusinessService, FarReportBusinessService farReportBusinessService,
			FinancialReportBusinessService financialReportBusinessService, AssetApprovalService assetApprovalService) {
		this.itemService = itemService;
		this.assetAllocationService = assetAllocationService;
		this.assetJournalService = assetJournalService;
		this.assetService = assetService;
		this.depreciationCalculationService = depreciationCalculationService;
		this.assetAllocationBusinessService = assetAllocationBusinessService;
		this.assetDisposalService = assetDisposalService;
		this.assetTransferService = assetTransferService;
		this.assetUpdateService = assetUpdateService;
		this.assetJournalBusinessService = assetJournalBusinessService;
		this.farReportBusinessService = farReportBusinessService;
		this.financialReportBusinessService = financialReportBusinessService;
		this.assetApprovalService = assetApprovalService;
	}

	@PostMapping("getFAR")
	public Map<String, Object> getFARKSA(@RequestBody JSONObject assetRequest) {
		String assetId = assetRequest.getString("assetId");
		int page = assetRequest.has("page") ? assetRequest.getInt("page") : 1;
		int size = assetRequest.has("size") ? assetRequest.getInt("size") : 3000;
		return farReportBusinessService.getFarReports(assetId, page, size);
	}

	@RequestMapping("uploadFAR")
	public JSONObject uploadFAR(@RequestBody String req) {
		try {
			farReportBusinessService.uploadFarReports(new JSONArray(req));
			return ResponseBuilder.success("Financial Report successfully created/updated");
		} catch (IllegalArgumentException ex) {
			LOGGER.error("Validation error: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		} catch (Exception ex) {
			LOGGER.error("Error uploading FAR: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		}
	}

	@RequestMapping("uploadFR")
	public JSONObject uploadFR(@RequestBody String req) {
		try {
			financialReportBusinessService.uploadFinancialReports(new JSONArray(req));
			return ResponseBuilder.success("Financial Report successfully created/updated");
		} catch (IllegalArgumentException ex) {
			LOGGER.error("Validation error: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		} catch (Exception ex) {
			LOGGER.error("Error uploading FR: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		}
	}

	@RequestMapping("getFinancialReport")
	public JSONArray getFinancialReport(@RequestBody JSONObject assetRequest) {
		try {
			return financialReportBusinessService.getFinancialReports(assetRequest.getString("assetId"));
		} catch (Exception ex) {
			LOGGER.error("Error getting financial report: {}", ex.getMessage());
			return new JSONArray();
		}
	}

	@RequestMapping("assetTransfer")
	public JSONObject assetTransfer(@RequestBody JSONObject assetRequest) {
		try {
			if (assetRequest.isEmpty()) {
				return ResponseBuilder.customCode("01", "Request cannot be empty");
			}
			assetTransferService.transferAsset(assetRequest.getString("assetCode"),
					assetRequest.getString("inventoryId"), assetRequest.getString("serialNumber"),
					(float) assetRequest.getDouble("purchasePrice"), assetRequest.getString("poId"),
					assetRequest.getString("createdBy"), assetRequest.getString("supplierId"),
					assetRequest.getString("purchaseDate"), assetRequest.getString("warrantyDetails"),
					assetRequest.getString("warrantExpiryDate"), assetRequest.getString("details"),
					assetRequest.getString("status"));
			return ResponseBuilder.success("Asset transferred Successfully");
		} catch (Exception ex) {
			LOGGER.error("Error transferring asset: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		}
	}

	@RequestMapping("getApprovedAssets")
	public JSONArray getApprovedAssets(@RequestBody JSONObject assetRequest) {
		try {
			return assetApprovalService.getApprovedAssets(assetRequest.getString("status"));
		} catch (Exception ex) {
			LOGGER.error("Error getting approved assets: {}", ex.getMessage());
			return new JSONArray();
		}
	}

	@RequestMapping("getAssetsForApproval")
	public JSONArray getAssetsForApproval(@RequestBody JSONObject assetRequest) {
		try {
			return assetApprovalService.getAssetsForApproval(assetRequest.getString("status"));
		} catch (Exception ex) {
			LOGGER.error("Error getting assets for approval: {}", ex.getMessage());
			return new JSONArray();
		}
	}

	@RequestMapping("approveAssetTransfer")
	public JSONObject approveAssetTransfer(@RequestBody JSONObject assetRequest) {
		try {
			if (assetRequest.isEmpty()) {
				return ResponseBuilder.customCode("01", "Request cannot be empty");
			}
			assetTransferService.approveTransfer(assetRequest.getString("serialNumber"),
					assetRequest.getString("status"), assetRequest.getString("approvedBy"));
			return ResponseBuilder.success("Asset Transfer Approved Successfully");
		} catch (Exception ex) {
			LOGGER.error("Error approving asset transfer: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		}
	}

	@RequestMapping("allocateAsset")
	public JSONObject allocateAsset(@RequestBody JSONObject assetRequest) {
		try {
			assetAllocationBusinessService.allocateAsset(assetRequest.getString("assetCode"),
					assetRequest.getString("locationId"), assetRequest.getString("personId"),
					assetRequest.getString("status"), assetRequest.getString("details"));
			return ResponseBuilder.success("Asset Allocated Successfully");
		} catch (Exception ex) {
			LOGGER.error("Error allocating asset: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		}
	}

	@RequestMapping("getUnapprovedAllocatedAsset")
	public JSONArray getUnapprovedAllocatedAsset(@RequestBody JSONObject assetRequest) {
		JSONArray jsonObjectResponse = new JSONArray();
		try {
			String status = assetRequest.getString("status");
			List<AssetAllocation> allocations = assetAllocationService.findByStatus(status);
			for (AssetAllocation allocation : allocations) {
				JSONObject assetObj = new JSONObject();
				Item item = itemService.findByItemCode(allocation.getAssetCode());
				assetObj.put("assetCode", allocation.getAssetCode());
				assetObj.put("locationId", allocation.getLocationId());
				assetObj.put("personId", allocation.getPersonId());
				assetObj.put("details", allocation.getDetails());
				assetObj.put("depreciationMethod", item != null ? item.getDepreciationMethod() : "Not Set");
				jsonObjectResponse.put(assetObj);
			}
		} catch (Exception ex) {
			LOGGER.error("Error getting unapproved allocated assets: {}", ex.getMessage());
		}
		return jsonObjectResponse;
	}

	@RequestMapping("approveAssetAllocation")
	public JSONObject approveAssetAllocation(@RequestBody JSONObject assetRequest) {
		try {
			assetAllocationBusinessService.approveAllocation(assetRequest.getString("locationId"),
					assetRequest.getString("personId"), assetRequest.getString("status"));
			return ResponseBuilder.success("Asset Allocation Approved Successfully");
		} catch (Exception ex) {
			LOGGER.error("Error approving asset allocation: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		}
	}

	@RequestMapping("assetJournal")
	public JSONObject assetCaptureJournal(@RequestBody JSONObject assetRequest) {
		try {
			assetJournalBusinessService.createJournal(assetRequest.getString("assetCode"),
					assetRequest.getString("activity"), assetRequest.getString("details"),
					assetRequest.getString("activityBy"), assetRequest.getString("locationId"));
			return ResponseBuilder.success("Asset Journal Created Successfully");
		} catch (Exception ex) {
			LOGGER.error("Error creating asset journal: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		}
	}

	@RequestMapping("getAssetJournal")
	public JSONArray getAssetJournal() {
		JSONArray resArray = new JSONArray();
		try {
			List<AssetJournal> assetJournalList = assetJournalService.findAll();
			for (AssetJournal journal : assetJournalList) {
				JSONObject journalObj = new JSONObject();
				journalObj.put("assetCode", journal.getAssetCode());
				journalObj.put("activity", journal.getActivity());
				journalObj.put("details", journal.getDetails());
				journalObj.put("activityBy", journal.getActivityBy());
				journalObj.put("locationId", journal.getLocationId());
				journalObj.put("trackingDate", journal.getTrackingDate());
				resArray.put(journalObj);
			}
		} catch (Exception ex) {
			LOGGER.error("Error getting asset journal: {}", ex.getMessage());
		}
		return resArray;
	}

	@RequestMapping("updateWarrantDetails")
	public JSONObject updateWarrantDetails(@RequestBody JSONObject assetRequest) {
		try {
			assetUpdateService.updateWarranty(assetRequest.getString("assetCode"),
					assetRequest.getString("warrantExpiryDate"), assetRequest.getString("warrantyDetails"));
			return ResponseBuilder.success("Asset Warranty Updated Successfully");
		} catch (Exception ex) {
			LOGGER.error("Error updating warranty: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		}
	}

	@RequestMapping("updateDepreciationDetails")
	public JSONObject updateDepreciationDetails(@RequestBody JSONObject assetRequest) {
		try {
			assetUpdateService.updateDepreciation(assetRequest.getString("assetCode"),
					assetRequest.getString("depreciationModel"), assetRequest.getString("salvageValue"),
					assetRequest.getString("usefulLife"));
			return ResponseBuilder.success("Asset Depreciation Updated Successfully");
		} catch (Exception ex) {
			LOGGER.error("Error updating depreciation: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		}
	}

	@RequestMapping("getAssetDepreciation")
	public JSONObject getAssetDepreciation(@RequestBody JSONObject assetRequest) {
		try {
			String assetCode = assetRequest.getString("assetCode");
			Asset asset = assetService.findByAssetCode(assetCode);
			double initialCost = asset.getPurchasePrice();
			double salvageValue = Double.parseDouble(asset.getSalvageValue());
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			LocalDate purchaseDate = LocalDate.parse(asset.getPurchaseDate().toString().substring(0, 10), formatter);
			int yearsLived = LocalDate.now().getYear() - purchaseDate.getYear();
			double totalDepreciation = asset.getDepreciationModel().equalsIgnoreCase("straightline")
					? depreciationCalculationService.calculateTotalDepreciation(initialCost, salvageValue,
							Integer.parseInt(asset.getUsefulLife()), yearsLived)
					: depreciationCalculationService.calculateReducingBalanceDepreciation(initialCost, salvageValue,
							yearsLived);
			double bookValue = initialCost - totalDepreciation;
			JSONObject response = ResponseBuilder.success("Successful");
			response.put("bookValue", "KES:" + bookValue);
			response.put("totalDepreciation", "KES:" + totalDepreciation);
			response.put("assetCode", asset.getAssetCode());
			response.put("serialNumber", asset.getSerialNumber());
			response.put("salvageValue", asset.getSalvageValue());
			return response;
		} catch (Exception ex) {
			LOGGER.error("Error getting asset depreciation: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		}
	}

	@RequestMapping("assetDisposal")
	public JSONObject doAssetDisposal(@RequestBody JSONObject assetRequest) {
		try {
			assetDisposalService.decommissionAsset(assetRequest.getString("assetCode"),
					assetRequest.getString("dicommissionedBy"));
			return ResponseBuilder.success("Asset has been successfully decommissioned.");
		} catch (Exception ex) {
			LOGGER.error("Error disposing asset: {}", ex.getMessage());
			return ResponseBuilder.error(ex.getMessage());
		}
	}

}
