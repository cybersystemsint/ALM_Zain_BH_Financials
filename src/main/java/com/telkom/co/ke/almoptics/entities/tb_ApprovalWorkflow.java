package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.util.Date;

/**
 * Data Transfer Object for ApprovalWorkflow
 */
public class tb_ApprovalWorkflow implements Serializable {

    private Integer ID;
    private String ASSET_ID;
    private String ORIGINAL_STATUS;
    private String UPDATED_STATUS;
    private Integer PROCESS_ID;
    private String COMMENTS;
    private String INSERTEDBY;
    private Date INSERTDATE;
    private String CHANGEDBY;
    private Date CHANGEDATE;
    private String objectType; // Added field for Object_Type

    public tb_ApprovalWorkflow() {
    }

    // Getters and Setters
    public Integer getID() {
        return ID;
    }

    public void setID(Integer ID) {
        this.ID = ID;
    }

    public String getASSET_ID() {
        return ASSET_ID;
    }

    public void setASSET_ID(String ASSET_ID) {
        this.ASSET_ID = ASSET_ID;
    }

    public String getORIGINAL_STATUS() {
        return ORIGINAL_STATUS;
    }

    public void setORIGINAL_STATUS(String ORIGINAL_STATUS) {
        this.ORIGINAL_STATUS = ORIGINAL_STATUS;
    }

    public String getUPDATED_STATUS() {
        return UPDATED_STATUS;
    }

    public void setUPDATED_STATUS(String UPDATED_STATUS) {
        this.UPDATED_STATUS = UPDATED_STATUS;
    }

    public Integer getPROCESS_ID() {
        return PROCESS_ID;
    }

    public void setPROCESS_ID(Integer PROCESS_ID) {
        this.PROCESS_ID = (PROCESS_ID);
    }

    public String getCOMMENTS() {
        return COMMENTS;
    }

    public void setCOMMENTS(String COMMENTS) {
        this.COMMENTS = COMMENTS;
    }

    public String getINSERTEDBY() {
        return INSERTEDBY;
    }

    public void setINSERTEDBY(String INSERTEDBY) {
        this.INSERTEDBY = INSERTEDBY;
    }

    public Date getINSERTDATE() {
        return INSERTDATE;
    }

    public void setINSERTDATE(Date INSERTDATE) {
        this.INSERTDATE = INSERTDATE;
    }

    public String getCHANGEDBY() {
        return CHANGEDBY;
    }

    public void setCHANGEDBY(String CHANGEDBY) {
        this.CHANGEDBY = CHANGEDBY;
    }

    public Date getCHANGEDATE() {
        return CHANGEDATE;
    }

    public void setCHANGEDATE(Date CHANGEDATE) {
        this.CHANGEDATE = CHANGEDATE;
    }

    // Added getter and setter for objectType
    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    @Override
    public String toString() {
        return "tb_WF_Financial_Approval_Request{" +
                "ID=" + ID +
                ", ASSET_ID='" + ASSET_ID + '\'' +
                ", OBJECT_TYPE='" + objectType + '\'' + // Updated to include objectType
                ", ORIGINAL_STATUS='" + ORIGINAL_STATUS + '\'' +
                ", UPDATED_STATUS='" + UPDATED_STATUS + '\'' +
                ", PROCESS_ID='" + PROCESS_ID + '\'' +
                ", COMMENTS='" + COMMENTS + '\'' +
                ", INSERTEDBY='" + INSERTEDBY + '\'' +
                ", INSERTDATE=" + INSERTDATE +
                ", CHANGEDBY='" + CHANGEDBY + '\'' +
                ", CHANGEDATE=" + CHANGEDATE +
                '}';
    }
}