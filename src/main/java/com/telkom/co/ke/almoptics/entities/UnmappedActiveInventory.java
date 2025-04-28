package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.*;

/**
 *
 * @author Gilian
 */
@Entity
@Table(name = "`tb_unmappednode`", indexes = {
        @Index(name = "PRIMARY", columnList = "id", unique = true)})
public class UnmappedActiveInventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "SiteId")
    private String siteId;

    @Column(name = "NodeName")
    private String nodeName;

    @Column(name = "AssetName")
    private String assetName;

    @Column(name = "AssetType")
    private String assetType;

    @Column(name = "NodeType")
    private String nodeType;

    @Column(name = "Manufacturer")
    private String manufacturer;

    @Column(name = "Model")
    private String model;

    @Column(name = "PartNumber")
    private String partNumber;

    @Column(name = "SerialNumber", unique = true, nullable = true)
    private String serialNumber;

    @Column(name = "Description")
    private String description;

    @Column(name = "ManufacturingDate", nullable = true)
    private Date manufacturingDate;

    @Column(name = "InstallationDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date installationDate;

    @Column(name = "AssetUpdateDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date assetUpdateDate;

    @Column(name = "Warranty")
    private String warranty;

    @Column(name = "InsertedBy", nullable = false)
    private String insertedBy;

    @Column(name = "InsertDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date insertDate;

    @PrePersist
    protected void onCreate() {
        this.insertDate = new Date();
    }

    public UnmappedActiveInventory() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
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

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
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

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getManufacturingDate() {
        return manufacturingDate;
    }

    public void setManufacturingDate(Date manufacturingDate) {
        this.manufacturingDate = manufacturingDate;
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

    public String getWarranty() {
        return warranty;
    }

    public void setWarranty(String warranty) {
        this.warranty = warranty;
    }

    public String getInsertedBy() {
        return insertedBy;
    }

    public void setInsertedBy(String insertedBy) {
        this.insertedBy = insertedBy;
    }

    public Date getInsertDate() {
        return insertDate;
    }

    public void setInsertDate(Date insertDate) {
        this.insertDate = insertDate;
    }
}