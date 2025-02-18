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
@Table(name = "`tb_FarReport`", indexes = {
    @Index(name = "PRIMARY", columnList = "recordNo", unique = false)})
public class tb_FarReport implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recordNo")
    private int recordNo;

    @Column(name = "recordDatetime")
    private Date recordDatetime;

    @Column(name = "book")
    private String book;

    @Column(name = "assetId")
    private String assetId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "description")
    private String description;

    @Column(name = "creationDate")
    private Date creationDate;

    @Column(name = "serialNumber")
    private String serialNumber;

    @Column(name = "tagNumber")
    private String tagNumber;
    @Column(name = "picStatus")
    private String picStatus;

    @Column(name = "picDate")
    private Date picDate;

    @Column(name = "cipDeliveryDate")
    private Date cipDeliveryDate;

    @Column(name = "linkId")
    private String linkId;

    @Column(name = "acceptanceNumber")
    private String acceptanceNumber;

    @Column(name = "depreciateFlag")
    private String depreciateFlag;

    @Column(name = "cipEu")
    private String cipEu;

    @Column(name = "invoiceNumber")
    private String invoiceNumber;

    @Column(name = "poNumber")
    private String poNumber;

    @Column(name = "poLineNumber")
    private String poLineNumber;

    @Column(name = "uplLine")
    private String uplLine;

    @Column(name = "transferToNewFar")
    private String transferToNewFar;

    @Column(name = "assetStatus")
    private String assetStatus;

    @Column(name = "value")
    private Double value;

    @Column(name = "partNumber")
    private String partNumber;

    @Column(name = "vendorName")
    private String vendorName;

    @Column(name = "vendorNumber")
    private String vendorNumber;

    @Column(name = "mergedCode")
    private String mergedCode;

    @Column(name = "costAccount")
    private String costAccount;

    @Column(name = "accumulatedDepreAccount")
    private String accumulatedDepreAccount;

    @Column(name = "cipCostAccount")
    private String cipCostAccount;

    @Column(name = "expenseCostCenter")
    private String expenseCostCenter;

    @Column(name = "expenseAccount")
    private String expenseAccount;

    @Column(name = "life")
    private Integer life;

    @Column(name = "datePlacedInService")
    private Date datePlacedInService;

    @Column(name = "cost")
    private Double cost;

    @Column(name = "nbv")
    private Double nbv;

    @Column(name = "depreciationAmount")
    private Double depreciationAmount;

    @Column(name = "ytdDepreciation")
    private Double ytdDepreciation;

    @Column(name = "depreciationReserve")
    private Double depreciationReserve;

    @Column(name = "salvageValue")
    private Double salvageValue;

    @Column(name = "category")
    private String category;
    @Column(name = "categoryDescription")
    private String categoryDescription;

    @Column(name = "locationSegment1")
    private String locationSegment1;

    @Column(name = "locationSegment2")
    private String locationSegment2;

    @Column(name = "locationSegment3")
    private String locationSegment3;

    @Column(name = "locationSegment4")
    private String locationSegment4;
    @Column(name = "locations")
    private String locations;

    @Column(name = "sequenceNumber")
    private Integer sequenceNumber;

    @Column(name = "monthlyDepreciationAmt")
    private Double monthlyDepreciationAmt;

    @Column(name = "accumulatedDepreciationAmt")
    private Double accumulatedDepreciationAmt;

    @Column(name = "depreciationDate")
    private Date depreciationDate;

    @Column(name = "netCost")
    private Double netCost;

    //new columns 
    @Column(name = "statusFlag")
    private String statusFlag;

    @Column(name = "changedBy")
    private String changedBy;

    @Column(name = "insertedBy")
    private String insertedBy;

    @Column(name = "financialApproval")
    private String financialApproval;

    @Column(name = "changedDate")
    private Date changedDate;

    public Double getMonthlyDepreciationAmt() {
        return monthlyDepreciationAmt;
    }

    public void setMonthlyDepreciationAmt(Double monthlyDepreciationAmt) {
        this.monthlyDepreciationAmt = monthlyDepreciationAmt;
    }

    public Double getAccumulatedDepreciationAmt() {
        return accumulatedDepreciationAmt;
    }

    public void setAccumulatedDepreciationAmt(Double accumulatedDepreciationAmt) {
        this.accumulatedDepreciationAmt = accumulatedDepreciationAmt;
    }

    public Date getDepreciationDate() {
        return depreciationDate;
    }

    public void setDepreciationDate(Date depreciationDate) {
        this.depreciationDate = depreciationDate;
    }

    public Double getNetCost() {
        return netCost;
    }

    public void setNetCost(Double netCost) {
        this.netCost = netCost;
    }

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

    public String getBook() {
        return book;
    }

    public void setBook(String book) {
        this.book = book;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getTagNumber() {
        return tagNumber;
    }

    public void setTagNumber(String tagNumber) {
        this.tagNumber = tagNumber;
    }

    public String getPicStatus() {
        return picStatus;
    }

    public void setPicStatus(String picStatus) {
        this.picStatus = picStatus;
    }

    public Date getPicDate() {
        return picDate;
    }

    public void setPicDate(Date picDate) {
        this.picDate = picDate;
    }

    public Date getCipDeliveryDate() {
        return cipDeliveryDate;
    }

    public void setCipDeliveryDate(Date cipDeliveryDate) {
        this.cipDeliveryDate = cipDeliveryDate;
    }

    public String getLinkId() {
        return linkId;
    }

    public void setLinkId(String linkId) {
        this.linkId = linkId;
    }

    public String getAcceptanceNumber() {
        return acceptanceNumber;
    }

    public void setAcceptanceNumber(String acceptanceNumber) {
        this.acceptanceNumber = acceptanceNumber;
    }

    public String getDepreciateFlag() {
        return depreciateFlag;
    }

    public void setDepreciateFlag(String depreciateFlag) {
        this.depreciateFlag = depreciateFlag;
    }

    public String getCipEu() {
        return cipEu;
    }

    public void setCipEu(String cipEu) {
        this.cipEu = cipEu;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public String getPoLineNumber() {
        return poLineNumber;
    }

    public void setPoLineNumber(String poLineNumber) {
        this.poLineNumber = poLineNumber;
    }

    public String getUplLine() {
        return uplLine;
    }

    public void setUplLine(String uplLine) {
        this.uplLine = uplLine;
    }

    public String getTransferToNewFar() {
        return transferToNewFar;
    }

    public void setTransferToNewFar(String transferToNewFar) {
        this.transferToNewFar = transferToNewFar;
    }

    public String getAssetStatus() {
        return assetStatus;
    }

    public void setAssetStatus(String assetStatus) {
        this.assetStatus = assetStatus;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
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

    public String getMergedCode() {
        return mergedCode;
    }

    public void setMergedCode(String mergedCode) {
        this.mergedCode = mergedCode;
    }

    public String getCostAccount() {
        return costAccount;
    }

    public void setCostAccount(String costAccount) {
        this.costAccount = costAccount;
    }

    public String getAccumulatedDepreAccount() {
        return accumulatedDepreAccount;
    }

    public void setAccumulatedDepreAccount(String accumulatedDepreAccount) {
        this.accumulatedDepreAccount = accumulatedDepreAccount;
    }

    public String getCipCostAccount() {
        return cipCostAccount;
    }

    public void setCipCostAccount(String cipCostAccount) {
        this.cipCostAccount = cipCostAccount;
    }

    public String getExpenseCostCenter() {
        return expenseCostCenter;
    }

    public void setExpenseCostCenter(String expenseCostCenter) {
        this.expenseCostCenter = expenseCostCenter;
    }

    public String getExpenseAccount() {
        return expenseAccount;
    }

    public void setExpenseAccount(String expenseAccount) {
        this.expenseAccount = expenseAccount;
    }

    public Integer getLife() {
        return life;
    }

    public void setLife(Integer life) {
        this.life = life;
    }

    public Date getDatePlacedInService() {
        return datePlacedInService;
    }

    public void setDatePlacedInService(Date datePlacedInService) {
        this.datePlacedInService = datePlacedInService;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public Double getNbv() {
        return nbv;
    }

    public void setNbv(Double nbv) {
        this.nbv = nbv;
    }

    public Double getDepreciationAmount() {
        return depreciationAmount;
    }

    public void setDepreciationAmount(Double depreciationAmount) {
        this.depreciationAmount = depreciationAmount;
    }

    public Double getYtdDepreciation() {
        return ytdDepreciation;
    }

    public void setYtdDepreciation(Double ytdDepreciation) {
        this.ytdDepreciation = ytdDepreciation;
    }

    public Double getDepreciationReserve() {
        return depreciationReserve;
    }

    public void setDepreciationReserve(Double depreciationReserve) {
        this.depreciationReserve = depreciationReserve;
    }

    public Double getSalvageValue() {
        return salvageValue;
    }

    public void setSalvageValue(Double salvageValue) {
        this.salvageValue = salvageValue;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCategoryDescription() {
        return categoryDescription;
    }

    public void setCategoryDescription(String categoryDescription) {
        this.categoryDescription = categoryDescription;
    }

    public String getLocationSegment1() {
        return locationSegment1;
    }

    public void setLocationSegment1(String locationSegment1) {
        this.locationSegment1 = locationSegment1;
    }

    public String getLocationSegment2() {
        return locationSegment2;
    }

    public void setLocationSegment2(String locationSegment2) {
        this.locationSegment2 = locationSegment2;
    }

    public String getLocationSegment3() {
        return locationSegment3;
    }

    public void setLocationSegment3(String locationSegment3) {
        this.locationSegment3 = locationSegment3;
    }

    public String getLocationSegment4() {
        return locationSegment4;
    }

    public void setLocationSegment4(String locationSegment4) {
        this.locationSegment4 = locationSegment4;
    }

    public String getLocations() {
        return locations;
    }

    public void setLocations(String locations) {
        this.locations = locations;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getStatusFlag() {
        return statusFlag;
    }

    public void setStatusFlag(String statusFlag) {
        this.statusFlag = statusFlag;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getInsertedBy() {
        return insertedBy;
    }

    public void setInsertedBy(String insertedBy) {
        this.insertedBy = insertedBy;
    }

    public String getFinancialApproval() {
        return financialApproval;
    }

    public void setFinancialApproval(String financialApproval) {
        this.financialApproval = financialApproval;
    }

    public Date getChangedDate() {
        return changedDate;
    }

    public void setChangedDate(Date changedDate) {
        this.changedDate = changedDate;
    }
    
    
    

}
