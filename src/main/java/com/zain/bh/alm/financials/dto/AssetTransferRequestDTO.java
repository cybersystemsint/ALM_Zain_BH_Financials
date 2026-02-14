package com.zain.bh.alm.financials.dto;

public class AssetTransferRequestDTO {
	private String assetCode;
	private String inventoryId;
	private String serialNumber;
	private double purchasePrice;
	private String poId;
	private String createdBy;
	private String supplierId;
	private String purchaseDate;
	private String warrantyDetails;
	private String warrantExpiryDate;
	private String details;
	private String status;

	public String getAssetCode() {
		return assetCode;
	}

	public void setAssetCode(String assetCode) {
		this.assetCode = assetCode;
	}

	public String getInventoryId() {
		return inventoryId;
	}

	public void setInventoryId(String inventoryId) {
		this.inventoryId = inventoryId;
	}

	public String getSerialNumber() {
		return serialNumber;
	}

	public void setSerialNumber(String serialNumber) {
		this.serialNumber = serialNumber;
	}

	public double getPurchasePrice() {
		return purchasePrice;
	}

	public void setPurchasePrice(double purchasePrice) {
		this.purchasePrice = purchasePrice;
	}

	public String getPoId() {
		return poId;
	}

	public void setPoId(String poId) {
		this.poId = poId;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public String getSupplierId() {
		return supplierId;
	}

	public void setSupplierId(String supplierId) {
		this.supplierId = supplierId;
	}

	public String getPurchaseDate() {
		return purchaseDate;
	}

	public void setPurchaseDate(String purchaseDate) {
		this.purchaseDate = purchaseDate;
	}

	public String getWarrantyDetails() {
		return warrantyDetails;
	}

	public void setWarrantyDetails(String warrantyDetails) {
		this.warrantyDetails = warrantyDetails;
	}

	public String getWarrantExpiryDate() {
		return warrantExpiryDate;
	}

	public void setWarrantExpiryDate(String warrantExpiryDate) {
		this.warrantExpiryDate = warrantExpiryDate;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	@Override
	public String toString() {
		return "AssetTransferRequestDTO [assetCode=" + assetCode + ", inventoryId=" + inventoryId + ", serialNumber="
				+ serialNumber + ", purchasePrice=" + purchasePrice + ", poId=" + poId + ", createdBy=" + createdBy
				+ ", supplierId=" + supplierId + ", purchaseDate=" + purchaseDate + ", warrantyDetails="
				+ warrantyDetails + ", warrantExpiryDate=" + warrantExpiryDate + ", details=" + details + ", status="
				+ status + "]";
	}

}
