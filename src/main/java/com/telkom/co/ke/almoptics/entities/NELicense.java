package com.telkom.co.ke.almoptics.entities;
import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PostPersist;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;




@Entity
@Table(name = "tb_NELicense")
public class NELicense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;


    @Column(name = "LicenseId")
    private String licenseId;


    @Column(name = "LicenseDetail")
    private String licenseDetail;


    @Column(name = "NodeId")
    private String nodeId;

    @Column(name = "NodeName")
    private String nodeName;

    @Column(name = "NodeType")
    private String nodeType;

    @Column(name = "NESiteName")
    private String neSiteName;

    @Column(name = "SiteID")
    private String siteId;

    @Column(name = "Zone")
    private String zone;

    @Column(name = "Allocated")
    private Integer allocated;

    @Column(name = "Usage%")
    private BigDecimal usagePercent;

    @Column(name = "Usage")
    private Integer usage;

    @Column(name = "Config")
    private Integer config;

    @Column(name = "Unit")
    private String unit;

    @Column(name = "ExpiryDate")
    private String expiryDate;


    @Column(name = "InsertDate")
    @Temporal(TemporalType.DATE)
    private Date insertDate;

    @Column(name = "LastChangeDate")
    @Temporal(TemporalType.DATE)
    private Date lastChangeDate;

    @Column(name = "Technology")
    private String technology;

    @Column(name = "LicenseDetailValue")
    private String licenseDetailValue;

    @Column(name = "Manufacturer")
    private String manufacturer;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }




    public String getLicenseId() {
        return licenseId;
    }

    public void setLicenseId(String licenseId) {
        this.licenseId = licenseId;
    }

    public String getLicenseDetail() {
        return licenseDetail;
    }

    public void setLicenseDetail(String licenseDetail) {
        this.licenseDetail = licenseDetail;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }


    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }



    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getNeSiteName() {
        return neSiteName;
    }

    public void setNeSiteName(String neSiteName) {
        this.neSiteName = neSiteName;
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


    public Integer getAllocated() {
        return allocated;
    }

    public void setAllocated(Integer allocated) {
        this.allocated = allocated;
    }

    public Integer getUsage() {
        return usage;
    }

    public void setUsage(Integer usage) {
        this.usage = usage;
    }

    public BigDecimal getUsagePercent() {
        return usagePercent;
    }

    public void setUsagePercent(BigDecimal usagePercent) {
        this.usagePercent = usagePercent;
    }

    public Integer getConfig() {
        return config;
    }

    public void setConfig(Integer config) {
        this.config = config;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    @PrePersist
    public void prePersist() {
        // Set the insertDate to the current date when the entity is being persisted
        this.insertDate = new Date();
    }
    public Date getInsertDate() {
        return insertDate;
    }

    public void setInsertDate(Date insertDate) {
        this.insertDate = insertDate;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastChangeDate = new Date();
    }


    public Date getLastChangeDate() {
        return lastChangeDate;
    }

    public void setLastChangeDate(Date lastChangeDate) {
        this.lastChangeDate = lastChangeDate;
    }

    public String getTechnology() {
        return technology;
    }

    public void setTechnology(String technology) {
        this.technology = technology;
    }

    public String getLicenseDetailValue() {
        return licenseDetailValue;
    }

    public void setLicenseDetailValue(String licenseDetailValue) {
        this.licenseDetailValue = licenseDetailValue;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

}