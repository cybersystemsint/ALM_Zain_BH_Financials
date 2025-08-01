package com.telkom.co.ke.almoptics.entities;

import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "tb_unmappedNELicense")
public class UnmappedNELicense {

    @Id
    @Column(name = "ElementID")
    private String elementID;

    @Column(name = "ElementType")
    private String elementType;

    @Column(name = "ParentNEType")
    private String parentNEType;

    @Column(name = "SiteId")
    private String siteId;

    @Column(name = "LicenseId")
    private String licenseId;

    @Column(name = "LicenseDetail")
    private String licenseDetail;

    @Column(name = "ExpiryDate")
    private String expiryDate;

    @Column(name = "Technology")
    private String technology;

    @Column(name = "NodeId")
    private String nodeId;

    @Column(name = "NodeName")
    private String nodeName;

    @Column(name = "Utilization")
    private BigDecimal utilization;

    @Column(name = "InstallationDate")
    @Temporal(TemporalType.DATE)
    private Date installationDate;

    @Column(name = "AssetUpdateDate")
    @Temporal(TemporalType.DATE)
    private Date assetUpdateDate;


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


    public String getElementID() {
        return elementID;
    }

    public void setElementID(String elementID) {
        this.elementID = elementID;
    }

    public String getElementType() {
        return elementType;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    public String getParentNEType() {
        return parentNEType;
    }

    public void setParentNEType(String parentNEType) {
        this.parentNEType = parentNEType;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
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

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getTechnology() {
        return technology;
    }

    public void setTechnology(String technology) {
        this.technology = technology;
    }

    public BigDecimal getUtilization() {
        return utilization;
    }

    public void setUtilization(BigDecimal utilization) {
        this.utilization = utilization;
    }

    public Date getInstallationDate() {
        return installationDate;
    }

    public void setInstallationDate(Date installationDate) {
        this.installationDate = installationDate;
    }

    public Date getAssetUpdateDate() {
        return assetUpdateDate;
    }

    public void setAssetUpdateDate(Date assetUpdateDate) {
        this.assetUpdateDate = assetUpdateDate;
    }

}