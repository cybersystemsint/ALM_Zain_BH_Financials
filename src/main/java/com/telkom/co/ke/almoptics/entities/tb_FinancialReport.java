package com.telkom.co.ke.almoptics.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

@Entity
@Table(name = "`tb_FinancialReport`", indexes = {
        @Index(name = "PRIMARY", columnList = "Id", unique = true)})
public class tb_FinancialReport implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "SiteID")
    @JsonProperty("siteID")
    private String siteId;

    @Column(name = "Zone")
    @JsonProperty("zone")
    private String zone;

    @Column(name = "NodeType")
    @JsonProperty("nodeType")
    private String nodeType;

    @Column(name = "AssetName")
    @JsonProperty("assetName")
    private String assetName;

    @Column(name = "AssetType")
    @JsonProperty("assetType")
    private String assetType;

    @Column(name = "AssetCategory")
    @JsonProperty("assetCategory")
    private String assetCategory;

    @Column(name = "Model")
    @JsonProperty("model")
    private String model;

    @Column(name = "PartNumber")
    @JsonProperty("partNumber")
    private String partNumber;

    @Column(name = "AssetSerialNumber")
    @JsonProperty("assetSerialNumber")
    private String assetSerialNumber;

    @Column(name = "InstallationDate")
    @JsonProperty("installationDate")
    private String installationDate;

    @Column(name = "InitialCost", precision = 15, scale = 3)
    @JsonProperty("initialCost")
    private BigDecimal initialCost;

    @Column(name = "MonthlyDepreciationAmount", precision = 15, scale = 3)
    @JsonProperty("monthlyDepreciationAmount")
    private BigDecimal monthlyDepreciationAmount;

    @Column(name = "AccumulatedDepreciation", precision = 15, scale = 3)
    @JsonProperty("accumulatedDepreciation")
    private BigDecimal accumulatedDepreciation;

    @Column(name = "NetCost", precision = 15, scale = 3)
    @JsonProperty("netCost")
    private BigDecimal netCost;

    @Column(name = "SalvageValue", precision = 15, scale = 3)
    @JsonProperty("salvageValue")
    private BigDecimal salvageValue;

    @Column(name = "PONumber")
    @JsonProperty("poNumber")
    private String poNumber;

    @Column(name = "PODate")
    @JsonProperty("poDate")
    private String poDate;

    @Column(name = "`FA_CATEGORY(NEW)`")
    @JsonProperty("FA_CATEGORY")
    private String faCategory;

    @Column(name = "`L1(NEW)`")
    @JsonProperty("l1")
    private String l1;

    @Column(name = "`L2(NEW)`")
    @JsonProperty("l2")
    private String l2;

    @Column(name = "`L3(NEW)`")
    @JsonProperty("l3")
    private String l3;

    @Column(name = "`L4(NEW)`")
    @JsonProperty("l4")
    private String l4;

    @Column(name = "AccumulatedDepreciationCode")
    @JsonProperty("accumulatedDepreciationCode")
    private String accumulatedDepreciationCode;

    @Column(name = "DepreciationCode")
    @JsonProperty("depreciationCode")
    private String depreciationCode;

    @Column(name = "`UsefulLife(Months)`")
    @JsonProperty("usefulLifeMonths")
    private Integer usefulLifeMonths;

    @Column(name = "VENDOR_NAME")
    @JsonProperty("vendorName")
    private String vendorName;

    @Column(name = "VENDOR_NUMBER")
    @JsonProperty("vendorNumber")
    private String vendorNumber;

    @Column(name = "PROJECT_NUMBER")
    @JsonProperty("projectNumber")
    private String projectNumber;

    @Column(name = "Description", columnDefinition = "text")
    @JsonProperty("description")
    private String description;

    @Column(name = "OracleAssetID")
    @JsonProperty("oracleAssetID")
    private String oracleAssetId;

    @Column(name = "DateOfService")
    @JsonProperty("dateOfService")
    private String dateOfService;

    @Column(name = "InsertDate")
    @Temporal(TemporalType.TIMESTAMP)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("insertDate")
    private Date insertDate;

    @Column(name = "InsertedBy")
    @JsonProperty("insertedBy")
    private String insertedBy;

    @Column(name = "ChangeDate")
    @Temporal(TemporalType.TIMESTAMP)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("changeDate")
    private Date changeDate;

    @Column(name = "ChangedBy")
    @JsonProperty("changedBy")
    private String changedBy;

    @Column(name = "StatusFlag")
    @JsonProperty("statusFlag")
    private String statusFlag;

    @Column(name = "TechnologySupported")
    @JsonProperty("technologySupported")
    private String technologySupported;

    @Column(name = "RetirementDate")
    @Temporal(TemporalType.TIMESTAMP)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("retirementDate")
    private Date retirementDate;

    @Column(name = "OLDFARcategory")
    @JsonProperty("OLDFARcategory")
    private String oldFarCategory;

    @Column(name = "CostCenterData")
    @JsonProperty("costCenterData")
    private String costCenterData;

    @Column(name = "FinancialApprovalStatus")
    @JsonProperty("financialApprovalStatus")
    private String financialApprovalStatus;

    @Column(name = "NEPAssetID")
    @JsonProperty("nepAssetID")
    private String nepAssetId;

    @Column(name = "Deleted")
    @JsonProperty("deleted")
    private Boolean deleted;

    @Column(name = "Adjustment", precision = 15, scale = 3)
    @JsonProperty("adjustment")
    private BigDecimal adjustment;

    @Column(name = "WriteOffDate")
    @Temporal(TemporalType.TIMESTAMP)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("writeOffDate")
    private Date writeOffDate;

    @Column(name = "TAG")
    @JsonProperty("tag")
    private String tag;

    @Column(name = "HostSerialNumber")
    @JsonProperty("hostSerialNumber")
    private String hostSerialNumber;

    @Column(name = "TaskId")
    @JsonProperty("taskId")
    private String taskId;

    @Column(name = "PoLineNumber")
    @JsonProperty("poLineNumber")
    private String poLineNumber;

    @Column(name = "ReleaseNumber")
    @JsonProperty("releaseNumber")
    private String releaseNumber;

    @Column(name = "SpectrumLicenseDate")
    @Temporal(TemporalType.TIMESTAMP)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("spectrumLicenseDate")
    private Date spectrumLicenseDate;

    @Column(name = "ItemBarCode")
    @JsonProperty("itemBarCode")
    private String itemBarCode;

    @Column(name = "RFID", nullable = true)
    @JsonProperty("rfid")
    private String rfid;

    @Column(name = "InvoiceNumber", nullable = true)
    @JsonProperty("invoiceNumber")
    private String invoiceNumber;

    @Column(name = "OriginalState", columnDefinition = "TEXT")
    @JsonProperty("originalState")
    private String originalState;

    @Version
    @Column(name = "Version")
    private Long version;

    // Constructor
    public tb_FinancialReport() {
    }

    // PrePersist to automatically set insertDate
    @PrePersist
    protected void onCreate() {
        this.insertDate = new Date();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getAssetCategory() {
        return assetCategory;
    }

    public void setAssetCategory(String assetCategory) {
        this.assetCategory = assetCategory;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }

    public String getAssetSerialNumber() {
        return assetSerialNumber;
    }

    public void setAssetSerialNumber(String assetSerialNumber) {
        this.assetSerialNumber = assetSerialNumber;
    }

    public String getInstallationDate() {
        return installationDate;
    }

    public void setInstallationDate(String installationDate) {
        this.installationDate = installationDate;
    }

    public BigDecimal getInitialCost() {
        return initialCost != null ? initialCost.setScale(3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public void setInitialCost(BigDecimal initialCost) {
        this.initialCost = initialCost != null ? initialCost.setScale(3, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getMonthlyDepreciationAmount() {
        return monthlyDepreciationAmount != null ? monthlyDepreciationAmount.setScale(3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public void setMonthlyDepreciationAmount(BigDecimal monthlyDepreciationAmount) {
        this.monthlyDepreciationAmount = monthlyDepreciationAmount != null ?
                monthlyDepreciationAmount.setScale(3, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getAccumulatedDepreciation() {
        return accumulatedDepreciation != null ? accumulatedDepreciation.setScale(3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public void setAccumulatedDepreciation(BigDecimal accumulatedDepreciation) {
        this.accumulatedDepreciation = accumulatedDepreciation != null ?
                accumulatedDepreciation.setScale(3, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getNetCost() {
        return netCost != null ? netCost.setScale(3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public void setNetCost(BigDecimal netCost) {
        this.netCost = netCost != null ? netCost.setScale(3, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getSalvageValue() {
        return salvageValue != null ? salvageValue.setScale(3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public void setSalvageValue(BigDecimal salvageValue) {
        this.salvageValue = salvageValue != null ? salvageValue.setScale(3, RoundingMode.HALF_UP) : null;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public String getPoDate() {
        return poDate;
    }

    public void setPoDate(String poDate) {
        this.poDate = poDate;
    }

    public String getFaCategory() {
        return faCategory;
    }

    public void setFaCategory(String faCategory) {
        this.faCategory = faCategory;
    }

    public String getL1() {
        return l1;
    }

    public void setL1(String l1) {
        this.l1 = l1;
    }

    public String getL2() {
        return l2;
    }

    public void setL2(String l2) {
        this.l2 = l2;
    }

    public String getL3() {
        return l3;
    }

    public void setL3(String l3) {
        this.l3 = l3;
    }

    public String getL4() {
        return l4;
    }

    public void setL4(String l4) {
        this.l4 = l4;
    }

    public String getAccumulatedDepreciationCode() {
        return accumulatedDepreciationCode;
    }

    public void setAccumulatedDepreciationCode(String accumulatedDepreciationCode) {
        this.accumulatedDepreciationCode = accumulatedDepreciationCode;
    }

    public String getDepreciationCode() {
        return depreciationCode;
    }

    public void setDepreciationCode(String depreciationCode) {
        this.depreciationCode = depreciationCode;
    }

    public Integer getUsefulLifeMonths() {
        return usefulLifeMonths;
    }

    public void setUsefulLifeMonths(Integer usefulLifeMonths) {
        this.usefulLifeMonths = usefulLifeMonths;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOracleAssetId() {
        return oracleAssetId;
    }

    public void setOracleAssetId(String oracleAssetId) {
        this.oracleAssetId = oracleAssetId;
    }

    public String getDateOfService() {
        return dateOfService;
    }

    public void setDateOfService(String dateOfService) {
        this.dateOfService = dateOfService;
    }

    public Date getInsertDate() {
        return insertDate;
    }

    public void setInsertDate(Date insertDate) {
        this.insertDate = insertDate;
    }

    public String getInsertedBy() {
        return insertedBy;
    }

    public void setInsertedBy(String insertedBy) {
        this.insertedBy = insertedBy;
    }

    public Date getChangeDate() {
        return changeDate;
    }

    public void setChangeDate(Date changeDate) {
        this.changeDate = changeDate;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getStatusFlag() {
        return statusFlag;
    }

    public void setStatusFlag(String statusFlag) {
        this.statusFlag = statusFlag;
    }

    public String getTechnologySupported() {
        return technologySupported;
    }

    public void setTechnologySupported(String technologySupported) {
        this.technologySupported = technologySupported;
    }

    public Date getRetirementDate() {
        return retirementDate;
    }

    public void setRetirementDate(Date retirementDate) {
        this.retirementDate = retirementDate;
    }

    public String getOldFarCategory() {
        return oldFarCategory;
    }

    public void setOldFarCategory(String oldFarCategory) {
        this.oldFarCategory = oldFarCategory;
    }

    public String getCostCenterData() {
        return costCenterData;
    }

    public void setCostCenterData(String costCenterData) {
        this.costCenterData = costCenterData;
    }

    public String getFinancialApprovalStatus() {
        return financialApprovalStatus;
    }

    public void setFinancialApprovalStatus(String financialApprovalStatus) {
        this.financialApprovalStatus = financialApprovalStatus;
    }

    public String getNepAssetId() {
        return nepAssetId;
    }

    public void setNepAssetId(String nepAssetId) {
        this.nepAssetId = nepAssetId;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public BigDecimal getAdjustment() {
        return adjustment != null ? adjustment.setScale(3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public void setAdjustment(BigDecimal adjustment) {
        this.adjustment = adjustment != null ? adjustment.setScale(3, RoundingMode.HALF_UP) : null;
    }

    public Date getWriteOffDate() {
        return writeOffDate;
    }

    public void setWriteOffDate(Date writeOffDate) {
        this.writeOffDate = writeOffDate;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getHostSerialNumber() {
        return hostSerialNumber;
    }

    public void setHostSerialNumber(String hostSerialNumber) {
        this.hostSerialNumber = hostSerialNumber;
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

    public String getReleaseNumber() {
        return releaseNumber;
    }

    public void setReleaseNumber(String releaseNumber) {
        this.releaseNumber = releaseNumber;
    }

    public Date getSpectrumLicenseDate() {
        return spectrumLicenseDate;
    }

    public void setSpectrumLicenseDate(Date spectrumLicenseDate) {
        this.spectrumLicenseDate = spectrumLicenseDate;
    }

    public String getItemBarCode() {
        return itemBarCode;
    }

    public void setItemBarCode(String itemBarCode) {
        this.itemBarCode = itemBarCode;
    }

    public String getRfid() {
        return rfid;
    }

    public void setRfid(String rfid) {
        this.rfid = rfid;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getOriginalState() {
        return originalState;
    }

    public void setOriginalState(String originalState) {
        this.originalState = originalState;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        tb_FinancialReport that = (tb_FinancialReport) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "FinancialReport{" +
                "id=" + id +
                ", assetName='" + assetName + '\'' +
                ", assetSerialNumber='" + assetSerialNumber + '\'' +
                ", assetType='" + assetType + '\'' +
                ", oracleAssetId='" + oracleAssetId + '\'' +
                '}';
    }
}
