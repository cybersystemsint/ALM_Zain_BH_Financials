package com.zain.bh.alm.financials.entity;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.*;

@Entity
@Table(name = "`tb_Asset`")
public class Asset implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recordNo")
    private int recordNo;
  
    @Column(name = "recordDatetime")
    private Date recordDatetime;
    @Column(name = "assetCode")
    private String assetCode;
    @Column(name = "inventoryId")
    private String inventoryId;
    @Column(name = "serialNumber")
    private String serialNumber;
    @Column(name = "purchasePrice")
    private float purchasePrice;
    @Column(name = "poId")
    private String poId;
    @Column(name = "createdBy")
    private String createdBy;
    @Column(name = "approved")
    private boolean approved;
    @Column(name = "approvedBy")
    private String approvedBy;
    @Column(name = "approvalDate")
    private Date approvalDate;
    @Column(name = "supplierId")
    private String supplierId;
    @Column(name = "purchaseDate")
    private Date purchaseDate;
    @Column(name = "status")
    private String status;
    @Column(name = "warrantyDetails")
    private String warrantyDetails;
    @Column(name = "warrantExpiryDate")
    private String warrantExpiryDate;
    @Column(name = "details")
    private String details;
    @Column(name = "depreciationModel")
    private String depreciationModel;
    @Column(name = "salvageValue")
    private String salvageValue;
    @Column(name = "usefulLife")
    private String usefulLife;


    public int getRecordNo() { return recordNo; }
    public void setRecordNo(int recordNo) { this.recordNo = recordNo; }

    public Date getRecordDatetime() { return recordDatetime; }
    public void setRecordDatetime(Date recordDatetime) { this.recordDatetime = recordDatetime; }

    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }

    public String getInventoryId() { return inventoryId; }
    public void setInventoryId(String inventoryId) { this.inventoryId = inventoryId; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public float getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(float purchasePrice) { this.purchasePrice = purchasePrice; }

    public String getPoId() { return poId; }
    public void setPoId(String poId) { this.poId = poId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public Date getApprovalDate() { return approvalDate; }
    public void setApprovalDate(Date approvalDate) { this.approvalDate = approvalDate; }

    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public Date getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(Date purchaseDate) { this.purchaseDate = purchaseDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getWarrantyDetails() { return warrantyDetails; }
    public void setWarrantyDetails(String warrantyDetails) { this.warrantyDetails = warrantyDetails; }

    public String getWarrantExpiryDate() { return warrantExpiryDate; }
    public void setWarrantExpiryDate(String warrantExpiryDate) { this.warrantExpiryDate = warrantExpiryDate; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getDepreciationModel() { return depreciationModel; }
    public void setDepreciationModel(String depreciationModel) { this.depreciationModel = depreciationModel; }

    public String getSalvageValue() { return salvageValue; }
    public void setSalvageValue(String salvageValue) { this.salvageValue = salvageValue; }

    public String getUsefulLife() { return usefulLife; }
    public void setUsefulLife(String usefulLife) { this.usefulLife = usefulLife; }
}
