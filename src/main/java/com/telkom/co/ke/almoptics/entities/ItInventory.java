package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.*;

@Entity
@Table(name = "`vw_IT_Inventory`")
public class ItInventory implements Serializable {

    @Id
    @Column(name = "ObjectID")
    private String objectId;

    @Column(name = "ParentName")
    private String parentName;

    @Column(name = "SiteId")
    private String siteId;

    @Column(name = "FirstScan")
    private String firstScan;

    @Column(name = "Category")
    private String category;

    @Column(name = "IPAddress")
    private String ipAddress;

    @Column(name = "OS")
    private String os;

    @Column(name = "HardwareVendor")
    private String hardwareVendor;

    @Column(name = "Model")
    private String model;

    @Column(name = "Virtual")
    private String virtual;

    @Column(name = "HostType")
    private String hostType;

    @Column(name = "HostSerialNumber")
    private String hostSerialNumber;

    @Column(name = "DiskDriveType")
    private String diskDriveType;

    @Column(name = "DiskDriveModel")
    private String diskDriveModel;

    @Column(name = "DiskDriveSerialNumber")
    private String diskDriveSerialNumber;

    @Column(name = "DiskDriveSize")
    private String diskDriveSize;

    @Column(name = "LastUpdateSuccess")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUpdateSuccess;

    public ItInventory() {
    }

    // Getters and Setters

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getFirstScan() {
        return firstScan;
    }

    public void setFirstScan(String firstScan) {
        this.firstScan = firstScan;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getHardwareVendor() {
        return hardwareVendor;
    }

    public void setHardwareVendor(String hardwareVendor) {
        this.hardwareVendor = hardwareVendor;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getVirtual() {
        return virtual;
    }

    public void setVirtual(String virtual) {
        this.virtual = virtual;
    }

    public String getHostType() {
        return hostType;
    }

    public void setHostType(String hostType) {
        this.hostType = hostType;
    }

    public String getHostSerialNumber() {
        return hostSerialNumber;
    }

    public void setHostSerialNumber(String hostSerialNumber) {
        this.hostSerialNumber = hostSerialNumber;
    }

    public String getDiskDriveType() {
        return diskDriveType;
    }

    public void setDiskDriveType(String diskDriveType) {
        this.diskDriveType = diskDriveType;
    }

    public String getDiskDriveModel() {
        return diskDriveModel;
    }

    public void setDiskDriveModel(String diskDriveModel) {
        this.diskDriveModel = diskDriveModel;
    }

    public String getDiskDriveSerialNumber() {
        return diskDriveSerialNumber;
    }

    public void setDiskDriveSerialNumber(String diskDriveSerialNumber) {
        this.diskDriveSerialNumber = diskDriveSerialNumber;
    }

    public String getDiskDriveSize() {
        return diskDriveSize;
    }

    public void setDiskDriveSize(String diskDriveSize) {
        this.diskDriveSize = diskDriveSize;
    }

    public Date getLastUpdateSuccess() {
        return lastUpdateSuccess;
    }

    public void setLastUpdateSuccess(Date lastUpdateSuccess) {
        this.lastUpdateSuccess = lastUpdateSuccess;
    }
}