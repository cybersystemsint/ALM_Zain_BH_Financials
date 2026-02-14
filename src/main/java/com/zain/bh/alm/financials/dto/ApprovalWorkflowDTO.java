package com.zain.bh.alm.financials.dto;

import java.util.Date;

public class ApprovalWorkflowDTO {

	private Integer id;
	private String assetId;
	private String originalStatus;
	private String updatedStatus;
	private Integer processId;
	private String comments;
	private String insertedBy;
	private Date insertDate;
	private String changedBy;
	private Date changeDate;
	private String objectType;

	public ApprovalWorkflowDTO() {
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getAssetId() {
		return assetId;
	}

	public void setAssetId(String assetId) {
		this.assetId = assetId;
	}

	public String getOriginalStatus() {
		return originalStatus;
	}

	public void setOriginalStatus(String originalStatus) {
		this.originalStatus = originalStatus;
	}

	public String getUpdatedStatus() {
		return updatedStatus;
	}

	public void setUpdatedStatus(String updatedStatus) {
		this.updatedStatus = updatedStatus;
	}

	public Integer getProcessId() {
		return processId;
	}

	public void setProcessId(Integer processId) {
		this.processId = processId;
	}

	public String getComments() {
		return comments;
	}

	public void setComments(String comments) {
		this.comments = comments;
	}

	public String getInsertedBy() {
		return insertedBy;
	}

	public void setInsertedBy(String insertedBy) {
		this.insertedBy = insertedBy;
	}

	public Date getInsertDate() {
		return insertDate;
	}

	public void setInsertDate(Date insertDate) {
		this.insertDate = insertDate;
	}

	public String getChangedBy() {
		return changedBy;
	}

	public void setChangedBy(String changedBy) {
		this.changedBy = changedBy;
	}

	public Date getChangeDate() {
		return changeDate;
	}

	public void setChangeDate(Date changeDate) {
		this.changeDate = changeDate;
	}

	public String getObjectType() {
		return objectType;
	}

	public void setObjectType(String objectType) {
		this.objectType = objectType;
	}

	@Override
	public String toString() {
		return "ApprovalWorkflowDTO [id=" + id + ", assetId=" + assetId + ", originalStatus=" + originalStatus
				+ ", updatedStatus=" + updatedStatus + ", processId=" + processId + ", comments=" + comments
				+ ", insertedBy=" + insertedBy + ", insertDate=" + insertDate + ", changedBy=" + changedBy
				+ ", changeDate=" + changeDate + ", objectType=" + objectType + "]";
	}

}
