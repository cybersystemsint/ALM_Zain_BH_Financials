package com.zain.bh.alm.financials.dto;

public class AssetAllocationRequestDTO {
	private String assetCode;
	private String locationId;
	private String personId;
	private String status;
	private String details;

	public String getAssetCode() {
		return assetCode;
	}

	public void setAssetCode(String assetCode) {
		this.assetCode = assetCode;
	}

	public String getLocationId() {
		return locationId;
	}

	public void setLocationId(String locationId) {
		this.locationId = locationId;
	}

	public String getPersonId() {
		return personId;
	}

	public void setPersonId(String personId) {
		this.personId = personId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	@Override
	public String toString() {
		return "AssetAllocationRequestDTO [assetCode=" + assetCode + ", locationId=" + locationId + ", personId="
				+ personId + ", status=" + status + ", details=" + details + "]";
	}

}
