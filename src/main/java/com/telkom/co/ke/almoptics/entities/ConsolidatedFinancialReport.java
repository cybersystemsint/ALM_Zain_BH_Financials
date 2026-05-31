package com.telkom.co.ke.almoptics.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "V_CONSOLIDATED_FINANCIAL_REPORT")
public class ConsolidatedFinancialReport {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "ORACLEASSETID")
    private String oracleAssetId;

    @Column(name = "INITIALCOST")
    private BigDecimal initialCost;

    @Column(name = "SALVAGEVALUE")
    private BigDecimal salvageValue;

    @Column(name = "ACCUMULATEDDEPRECIATION")
    private BigDecimal accumulatedDepreciation;

    @Column(name = "NETCOST")
    private BigDecimal netCost;

    @Column(name = "ADJUSTMENT")
    private BigDecimal adjustment;

    @Column(name = "PONUMBER")
    private String poNumber;

    @Column(name = "PODATE")
    private String poDate;

    @Column(name = "VENDORNAME")
    private String vendorName;

    @Column(name = "VENDORNUMBER")
    private String vendorNumber;

    @Column(name = "PROJECTNUMBER")
    private String projectNumber;

    @Column(name = "POLINENUMBER")
    private String poLineNumber;

    @Column(name = "RELEASENUMBER")
    private String releaseNumber;

    @Column(name = "DATEOFSERVICE")
    private String dateOfService;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOracleAssetId() { return oracleAssetId; }
    public void setOracleAssetId(String oracleAssetId) { this.oracleAssetId = oracleAssetId; }

    public BigDecimal getInitialCost() { return initialCost; }
    public void setInitialCost(BigDecimal initialCost) { this.initialCost = initialCost; }

    public BigDecimal getSalvageValue() { return salvageValue; }
    public void setSalvageValue(BigDecimal salvageValue) { this.salvageValue = salvageValue; }

    public BigDecimal getAccumulatedDepreciation() { return accumulatedDepreciation; }
    public void setAccumulatedDepreciation(BigDecimal accumulatedDepreciation) { this.accumulatedDepreciation = accumulatedDepreciation; }

    public BigDecimal getNetCost() { return netCost; }
    public void setNetCost(BigDecimal netCost) { this.netCost = netCost; }

    public BigDecimal getAdjustment() { return adjustment; }
    public void setAdjustment(BigDecimal adjustment) { this.adjustment = adjustment; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public String getPoDate() { return poDate; }
    public void setPoDate(String poDate) { this.poDate = poDate; }

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }

    public String getVendorNumber() { return vendorNumber; }
    public void setVendorNumber(String vendorNumber) { this.vendorNumber = vendorNumber; }

    public String getProjectNumber() { return projectNumber; }
    public void setProjectNumber(String projectNumber) { this.projectNumber = projectNumber; }

    public String getPoLineNumber() { return poLineNumber; }
    public void setPoLineNumber(String poLineNumber) { this.poLineNumber = poLineNumber; }

    public String getReleaseNumber() { return releaseNumber; }
    public void setReleaseNumber(String releaseNumber) { this.releaseNumber = releaseNumber; }

    public String getDateOfService() { return dateOfService; }
    public void setDateOfService(String dateOfService) { this.dateOfService = dateOfService; }
}
