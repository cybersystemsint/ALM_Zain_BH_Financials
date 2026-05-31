package com.telkom.co.ke.almoptics.dto;

public class ConsolidatedReport {

    private Long id;
    private String oracleAssetId;
    private String initialCost;
    private String salvageValue;
    private String accumulatedDepreciation;
    private String netCost;
    private String adjustment;
    private String poNumber;
    private String poDate;
    private String vendorName;
    private String vendorNumber;
    private String projectNumber;
    private String poLineNumber;
    private String releaseNumber;

    public ConsolidatedReport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOracleAssetId() { return oracleAssetId; }
    public void setOracleAssetId(String oracleAssetId) { this.oracleAssetId = oracleAssetId; }

    public String getInitialCost() { return initialCost; }
    public void setInitialCost(String initialCost) { this.initialCost = initialCost; }

    public String getSalvageValue() { return salvageValue; }
    public void setSalvageValue(String salvageValue) { this.salvageValue = salvageValue; }

    public String getAccumulatedDepreciation() { return accumulatedDepreciation; }
    public void setAccumulatedDepreciation(String accumulatedDepreciation) { this.accumulatedDepreciation = accumulatedDepreciation; }

    public String getNetCost() { return netCost; }
    public void setNetCost(String netCost) { this.netCost = netCost; }

    public String getAdjustment() { return adjustment; }
    public void setAdjustment(String adjustment) { this.adjustment = adjustment; }

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
}
