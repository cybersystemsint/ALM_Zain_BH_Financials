package com.zain.bh.alm.financials.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "`tb_Asset_Journal`")
public class AssetJournal implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "recordNo")
	private int recordNo;
	@Column(name = "recordDatetime")
	private Date recordDatetime;
	@Column(name = "trackingDate")
	private Date trackingDate;
	@Column(name = "assetCode")
	private String assetCode;
	@Column(name = "activity")
	private String activity;
	@Column(name = "details")
	private String details;
	@Column(name = "locationId")
	private String locationId;
	@Column(name = "activityBy")
	private String activityBy;

	public int getRecordNo() {
		return recordNo;
	}

	public void setRecordNo(int recordNo) {
		this.recordNo = recordNo;
	}

	public Date getRecordDatetime() {
		return recordDatetime;
	}

	public void setRecordDatetime(Date recordDatetime) {
		this.recordDatetime = recordDatetime;
	}

	public Date getTrackingDate() {
		return trackingDate;
	}

	public void setTrackingDate(Date trackingDate) {
		this.trackingDate = trackingDate;
	}

	public String getAssetCode() {
		return assetCode;
	}

	public void setAssetCode(String assetCode) {
		this.assetCode = assetCode;
	}

	public String getActivity() {
		return activity;
	}

	public void setActivity(String activity) {
		this.activity = activity;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public String getLocationId() {
		return locationId;
	}

	public void setLocationId(String locationId) {
		this.locationId = locationId;
	}

	public String getActivityBy() {
		return activityBy;
	}

	public void setActivityBy(String activityBy) {
		this.activityBy = activityBy;
	}
}
