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
@Table(name = "`tb_Asset_Depreciation`")
public class AssetDepreciation implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "recordId")
	private int recordId;
	@Column(name = "recordDatetime")
	private Date recordDatetime;
	@Column(name = "assetCode")
	private String assetCode;
	@Column(name = "depreciationDate")
	private String depreciationDate;
	@Column(name = "assetBookValue")
	private double assetBookValue;

	public int getRecordId() {
		return recordId;
	}

	public void setRecordId(int recordId) {
		this.recordId = recordId;
	}

	public Date getRecordDatetime() {
		return recordDatetime;
	}

	public void setRecordDatetime(Date recordDatetime) {
		this.recordDatetime = recordDatetime;
	}

	public String getAssetCode() {
		return assetCode;
	}

	public void setAssetCode(String assetCode) {
		this.assetCode = assetCode;
	}

	public String getDepreciationDate() {
		return depreciationDate;
	}

	public void setDepreciationDate(String depreciationDate) {
		this.depreciationDate = depreciationDate;
	}

	public double getAssetBookValue() {
		return assetBookValue;
	}

	public void setAssetBookValue(double assetBookValue) {
		this.assetBookValue = assetBookValue;
	}
}
