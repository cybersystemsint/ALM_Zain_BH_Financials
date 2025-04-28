package com.telkom.co.ke.almoptics.models;

import java.io.Serializable;
import java.time.LocalDateTime;
import javax.persistence.*;

/**
 *
 * @author Gilian
 */
@Entity
@Table(name = "`tb_WF_Financial_Approval_Request`", indexes = {
        @Index(name = "PRIMARY", columnList = "ID", unique = true)})
public class ApprovalWorkflow implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "ASSET_ID", length = 2000)
    private String assetId;

    @Column(name = "Object_Type", length = 2050 , nullable = true)
    private String objectType;

    @Column(name = "ORIGINAL_STATUS", length = 255)
    private String originalStatus;

    @Column(name = "UPDATED_STATUS", length = 255)
    private String updatedStatus;


    @Column(name = "PROCESS_ID", unique = true)
    private Integer processId;


    @Column(name = "COMMENTS", length = 600)
    private String comments;

    @Column(name = "INSERTEDBY", length = 255)
    private String insertedBy;

    @Column(name = "INSERTDATE")
    private LocalDateTime insertDate;

    @Column(name = "CHANGEDBY", length = 255)
    private String changedBy;

    @Column(name = "CHANGEDATE")
    private LocalDateTime changeDate;

    @PrePersist
    protected void onCreate() {
        this.insertDate = LocalDateTime.now();
    }

    public ApprovalWorkflow() {
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

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
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

    public LocalDateTime getInsertDate() {
        return insertDate;
    }

    public void setInsertDate(LocalDateTime insertDate) {
        this.insertDate = insertDate;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangeDate() {
        return changeDate;
    }

    public void setChangeDate(LocalDateTime changeDate) {
        this.changeDate = changeDate;
    }
}