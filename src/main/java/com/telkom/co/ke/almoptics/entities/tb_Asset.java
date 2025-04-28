/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.entities;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
/**
 *
 * @author jgithu
 */

@Entity
@Table(name = "`tb_Asset`", indexes = {@Index(name = "PRIMARY", columnList = "recordNo", unique = false)})
public class tb_Asset implements Serializable {
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
  
  public tb_Asset() {}
  
  public tb_Asset(int recordNo, Date recordDateTime, String assetCode, String inventoryId, String serialNumber, float purchasePrice, String poId, String createdBy, boolean approved, String approvedBy, Date approvalDate, String supplierId, Date purchaseDate, String status, String warrantyDetails, String warrantExpiryDate, String details, String depreciationModel, String salvageValue, String usefulLife) {
    this.recordNo = recordNo;
    this.recordDatetime = this.recordDatetime;
    this.assetCode = assetCode;
    this.inventoryId = inventoryId;
    this.serialNumber = serialNumber;
    this.purchasePrice = purchasePrice;
    this.poId = poId;
    this.createdBy = createdBy;
    this.approved = approved;
    this.approvedBy = approvedBy;
    this.approvalDate = approvalDate;
    this.supplierId = supplierId;
    this.purchaseDate = purchaseDate;
    this.status = status;
    this.warrantyDetails = warrantyDetails;
    this.warrantExpiryDate = warrantExpiryDate;
    this.details = details;
    this.depreciationModel = depreciationModel;
    this.salvageValue = salvageValue;
    this.usefulLife = usefulLife;
  }
  
  public int getRecordNo() {
    return this.recordNo;
  }
  
  public void setRecordNo(int recordNo) {
    this.recordNo = recordNo;
  }
  
  public Date getRecordDatetime() {
    return this.recordDatetime;
  }
  
  public void setRecordDatetime(Date recordDatetime) {
    this.recordDatetime = recordDatetime;
  }
  
  public String getAssetCode() {
    return this.assetCode;
  }
  
  public void setAssetCode(String assetCode) {
    this.assetCode = assetCode;
  }
  
  public String getInventoryId() {
    return this.inventoryId;
  }
  
  public void setInventoryId(String inventoryId) {
    this.inventoryId = inventoryId;
  }
  
  public String getSerialNumber() {
    return this.serialNumber;
  }
  
  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }
  
  public float getPurchasePrice() {
    return this.purchasePrice;
  }
  
  public void setPurchasePrice(float purchasePrice) {
    this.purchasePrice = purchasePrice;
  }
  
  public String getPoId() {
    return this.poId;
  }
  
  public void setPoId(String poId) {
    this.poId = poId;
  }
  
  public String getCreatedBy() {
    return this.createdBy;
  }
  
  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }
  
  public boolean isApproved() {
    return this.approved;
  }
  
  public void setApproved(boolean approved) {
    this.approved = approved;
  }
  
  public String getApprovedBy() {
    return this.approvedBy;
  }
  
  public void setApprovedBy(String approvedBy) {
    this.approvedBy = approvedBy;
  }
  
  public Date getApprovalDate() {
    return this.approvalDate;
  }
  
  public void setApprovalDate(Date approvalDate) {
    this.approvalDate = approvalDate;
  }
  
  public String getSupplierId() {
    return this.supplierId;
  }
  
  public void setSupplierId(String supplierId) {
    this.supplierId = supplierId;
  }
  
  public Date getPurchaseDate() {
    return this.purchaseDate;
  }
  
  public void setPurchaseDate(Date purchaseDate) {
    this.purchaseDate = purchaseDate;
  }
  
  public String getStatus() {
    return this.status;
  }
  
  public void setStatus(String status) {
    this.status = status;
  }
  
  public String getWarrantyDetails() {
    return this.warrantyDetails;
  }
  
  public void setWarrantyDetails(String warrantyDetails) {
    this.warrantyDetails = warrantyDetails;
  }
  
  public String getWarrantExpiryDate() {
    return this.warrantExpiryDate;
  }
  
  public void setWarrantExpiryDate(String warrantExpiryDate) {
    this.warrantExpiryDate = warrantExpiryDate;
  }
  
  public String getDetails() {
    return this.details;
  }
  
  public void setDetails(String details) {
    this.details = details;
  }
  
  public String getDepreciationModel() {
    return this.depreciationModel;
  }
  
  public void setDepreciationModel(String depreciationModel) {
    this.depreciationModel = depreciationModel;
  }
  
  public String getSalvageValue() {
    return this.salvageValue;
  }
  
  public void setSalvageValue(String salvageValue) {
    this.salvageValue = salvageValue;
  }
  
  public String getUsefulLife() {
    return this.usefulLife;
  }
  
  public void setUsefulLife(String usefulLife) {
    this.usefulLife = usefulLife;
  }
    
}
