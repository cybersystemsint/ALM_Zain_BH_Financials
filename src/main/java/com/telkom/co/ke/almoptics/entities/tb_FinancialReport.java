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
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Temporal;

/**
 *
 * @author jgithu
 */
@Entity
@Table(name = "`tb_FinancialReport`", indexes = {
    @Index(name = "PRIMARY", columnList = "recordNo", unique = false)})
public class tb_FinancialReport implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recordNo")
    private int recordNo;

    @Column(name = "recordDatetime")
    // @Temporal(javax.persistence.TemporalType.DATE)
    private Date recordDatetime;

    @Column(name = "serialNumber")
    private String serialNumber;

    @Column(name = "rfid")
    private String rfid;

    @Column(name = "tag")
    private String tag;

    @Column(name = "assetId")
    private String assetId;

    @Column(name = "assetType")
    private String assetType;
    @Column(name = "nodeType")
    private String nodeType;

    @Column(name = "installationDate")
    // @Temporal(javax.persistence.TemporalType.DATE)
    private Date installationDate;

    @Column(name = "initialCost")
    private double initialCost;

    @Column(name = "salvageValue")
    private double salvageValue;

    @Column(name = "poNumber")
    private String poNumber;

    @Column(name = "poDate")
//    @Temporal(javax.persistence.TemporalType.DATE)
    private Date poDate;

    @Column(name = "newFACategory")
    private String newFACategory;

    @Column(name = "L1")
    private String L1;

    @Column(name = "L2")
    private String L2;
    @Column(name = "L3")
    private String L3;
    @Column(name = "L4")
    private String L4;

    @Column(name = "accDepreciationCode")
    private String accDepreciationCode;
    @Column(name = "depreciationCode")
    private String depreciationCode;

    @Column(name = "userfulLife")
    private Integer userfulLife;

    @Column(name = "vendorName")
    private String vendorName;

    @Column(name = "vendorNumber")
    private String vendorNumber;

    @Column(name = "projectNumber")
    private String projectNumber;

    @Column(name = "dateOfService")
    private Date dateOfService;
    @Column(name = "oldFACategory")
    private String oldFACategory;

    @Column(name = "costCenter")
    private String costCenter;

    @Column(name = "adjustment")
    private double adjustment;

    @Column(name = "invoiceNumber")
    private String invoiceNumber;
    @Column(name = "taskId")
    private String taskId;

    @Column(name = "poLineNumber")
    private String poLineNumber;

    @Column(name = "monthlyDepreciationAmt")
    private Double  monthlyDepreciationAmt;

    @Column(name = "accumulatedDepreciationAmt")
    private Double  accumulatedDepreciationAmt;

    @Column(name = "approvalStatus")
    private String approvalStatus;

    @Column(name = "depreciationDate")
    private Date depreciationDate;

    @Column(name = "netCost")
    private Double  netCost;

    public tb_FinancialReport() {
    }

//    public tb_FinancialReport(int recordNo, Date recordDatetime, String serialNumber, String rfid, String tag, String assetId, String assetType, String nodeType, Date installationDate, double initialCost, double salvageValue, String poNumber, Date poDate, String newFACategory, String L1, String L2, String L3, String L4, String accDepreciationCode, String depreciationCode, Integer userfulLife, String vendorName, String vendorNumber, String projectNumber, Date dateOfService, String oldFACategory, String costCenter, boolean adjustment, String invoiceNumber, String taskId, String poLineNumber) {
//        this.recordNo = recordNo;
//        this.recordDatetime = recordDatetime;
//        this.serialNumber = serialNumber;
//        this.rfid = rfid;
//        this.tag = tag;
//        this.assetId = assetId;
//        this.assetType = assetType;
//        this.nodeType = nodeType;
//        this.installationDate = installationDate;
//        this.initialCost = initialCost;
//        this.salvageValue = salvageValue;
//        this.poNumber = poNumber;
//        this.poDate = poDate;
//        this.newFACategory = newFACategory;
//        this.L1 = L1;
//        this.L2 = L2;
//        this.L3 = L3;
//        this.L4 = L4;
//        this.accDepreciationCode = accDepreciationCode;
//        this.depreciationCode = depreciationCode;
//        this.userfulLife = userfulLife;
//        this.vendorName = vendorName;
//        this.vendorNumber = vendorNumber;
//        this.projectNumber = projectNumber;
//        this.dateOfService = dateOfService;
//        this.oldFACategory = oldFACategory;
//        this.costCenter = costCenter;
//        this.adjustment = adjustment;
//        this.invoiceNumber = invoiceNumber;
//        this.taskId = taskId;
//        this.poLineNumber = poLineNumber;
//    }
    public int getRecordNo() {
        return recordNo;
    }

    @PrePersist
    protected void onCreate() {

        this.recordDatetime = new Date();

    }

    public Double  getMonthlyDepreciationAmt() {
        
          return monthlyDepreciationAmt != null ? monthlyDepreciationAmt : 0.0;
       // return monthlyDepreciationAmt;
    }

    public void setMonthlyDepreciationAmt(double monthlyDepreciationAmt) {
        this.monthlyDepreciationAmt = monthlyDepreciationAmt;
    }

    public Double  getAccumulatedDepreciationAmt() {
          return accumulatedDepreciationAmt != null ? accumulatedDepreciationAmt : 0.0;
      //  return accumulatedDepreciationAmt;
    }

    public void setAccumulatedDepreciationAmt(double accumulatedDepreciationAmt) {
        this.accumulatedDepreciationAmt = accumulatedDepreciationAmt;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Date getDepreciationDate() {
        return depreciationDate;
    }

    public void setDepreciationDate(Date depreciationDate) {
        this.depreciationDate = depreciationDate;
    }

    public Double getNetCost() {
         return netCost != null ? netCost : 0.0;
       // return netCost;
    }

    public void setNetCost(double netCost) {
        this.netCost = netCost;
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

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getRfid() {
        return rfid;
    }

    public void setRfid(String rfid) {
        this.rfid = rfid;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public Date getInstallationDate() {
        return installationDate;
    }

    public void setInstallationDate(Date installationDate) {
        this.installationDate = installationDate;
    }

    public double getInitialCost() {
        return initialCost;
    }

    public void setInitialCost(double initialCost) {
        this.initialCost = initialCost;
    }

    public double getSalvageValue() {
        return salvageValue;
    }

    public void setSalvageValue(double salvageValue) {
        this.salvageValue = salvageValue;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public Date getPoDate() {
        return poDate;
    }

    public void setPoDate(Date poDate) {
        this.poDate = poDate;
    }

    public String getNewFACategory() {
        return newFACategory;
    }

    public void setNewFACategory(String newFACategory) {
        this.newFACategory = newFACategory;
    }

    public String getL1() {
        return L1;
    }

    public void setL1(String L1) {
        this.L1 = L1;
    }

    public String getL2() {
        return L2;
    }

    public void setL2(String L2) {
        this.L2 = L2;
    }

    public String getL3() {
        return L3;
    }

    public void setL3(String L3) {
        this.L3 = L3;
    }

    public String getL4() {
        return L4;
    }

    public void setL4(String L4) {
        this.L4 = L4;
    }

    public String getAccDepreciationCode() {
        return accDepreciationCode;
    }

    public void setAccDepreciationCode(String accDepreciationCode) {
        this.accDepreciationCode = accDepreciationCode;
    }

    public String getDepreciationCode() {
        return depreciationCode;
    }

    public void setDepreciationCode(String depreciationCode) {
        this.depreciationCode = depreciationCode;
    }

    public Integer getUserfulLife() {
        return userfulLife;
    }

    public void setUserfulLife(Integer userfulLife) {
        this.userfulLife = userfulLife;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getVendorNumber() {
        return vendorNumber;
    }

    public void setVendorNumber(String vendorNumber) {
        this.vendorNumber = vendorNumber;
    }

    public String getProjectNumber() {
        return projectNumber;
    }

    public void setProjectNumber(String projectNumber) {
        this.projectNumber = projectNumber;
    }

    public Date getDateOfService() {
        return dateOfService;
    }

    public void setDateOfService(Date dateOfService) {
        this.dateOfService = dateOfService;
    }

    public String getOldFACategory() {
        return oldFACategory;
    }

    public void setOldFACategory(String oldFACategory) {
        this.oldFACategory = oldFACategory;
    }

    public String getCostCenter() {
        return costCenter;
    }

    public void setCostCenter(String costCenter) {
        this.costCenter = costCenter;
    }

    public double getAdjustment() {
        return adjustment;
    }

    public void setAdjustment(double adjustment) {
        this.adjustment = adjustment;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getPoLineNumber() {
        return poLineNumber;
    }

    public void setPoLineNumber(String poLineNumber) {
        this.poLineNumber = poLineNumber;
    }

}
