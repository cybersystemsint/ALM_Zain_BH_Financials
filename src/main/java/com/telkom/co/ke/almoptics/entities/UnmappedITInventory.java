package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.*;

/**
 *
 * @author Gilian
 */
@Entity
@Table(name = "`tb_unmappedIT_INVENTORY`", indexes = {
        @Index(name = "PRIMARY", columnList = "ID", unique = true)})
public class UnmappedITInventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "Element_ID")
    private String elementId;

    @Column(name = "Element_Type")
    private String elementType;

    @Column(name = "Parent_NE_Type")
    private String parentNEType;

    @Column(name = "Site_ID")
    private String siteId;

    @Column(name = "Floor")
    private String floor;

    @Column(name = "OS")
    private String os;

    @Column(name = "Hardware_Vendor")
    private String harwareVendor;

    @Column(name = "Host_Type")
    private String hostType;

    @Column(name = "Host_Serial_Number", unique = true)
    private String hostSerialNumber;

    @Column(name = "Disk_Drive_Serial_Number")
    private String diskDriveSerialNumber;

    @Column(name = "Hardware_Serial_Number")
    private String hardwareSerialNumber;

    @Column(name = "Memory_Part_Number")
    private String memoryPartNumber;

    @Column(name = "Domain")
    private String domain;

    @Column(name = "Warranty")
    private String warranty;

    @Column(name = "SKU_Number")
    private String skuNumber;

    @Column(name = "Asset_Insert_Date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date assetInsertDate;

    @Column(name = "IP_Address")
    private String ipAddress;

    @Column(name = "Model")
    private String model;

    @Column(name = "Manufacturer")
    private String manufacturer;

    @Column(name = "Last_Update_Success")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUpdateSuccess;

    @Column(name = "Host_Name")
    private String hostName;

    @PrePersist
    protected void onCreate() {
        this.assetInsertDate = new Date();
    }

    public UnmappedITInventory() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
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

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getHarwareVendor() {
        return harwareVendor;
    }

    public void setHarwareVendor(String harwareVendor) {
        this.harwareVendor = harwareVendor;
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

    public String getDiskDriveSerialNumber() {
        return diskDriveSerialNumber;
    }

    public void setDiskDriveSerialNumber(String diskDriveSerialNumber) {
        this.diskDriveSerialNumber = diskDriveSerialNumber;
    }

    public String getHardwareSerialNumber() {
        return hardwareSerialNumber;
    }

    public void setHardwareSerialNumber(String hardwareSerialNumber) {
        this.hardwareSerialNumber = hardwareSerialNumber;
    }

    public String getMemoryPartNumber() {
        return memoryPartNumber;
    }

    public void setMemoryPartNumber(String memoryPartNumber) {
        this.memoryPartNumber = memoryPartNumber;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getWarranty() {
        return warranty;
    }

    public void setWarranty(String warranty) {
        this.warranty = warranty;
    }

    public String getSkuNumber() {
        return skuNumber;
    }

    public void setSkuNumber(String skuNumber) {
        this.skuNumber = skuNumber;
    }

    public Date getAssetInsertDate() {
        return assetInsertDate;
    }

    public void setAssetInsertDate(Date assetInsertDate) {
        this.assetInsertDate = assetInsertDate;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public Date getLastUpdateSuccess() {
        return lastUpdateSuccess;
    }

    public void setLastUpdateSuccess(Date lastUpdateSuccess) {
        this.lastUpdateSuccess = lastUpdateSuccess;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof UnmappedITInventory)) {
            return false;
        }
        UnmappedITInventory other = (UnmappedITInventory) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.telkom.co.ke.almoptics.entities.UnmappedITInventory[ id=" + id + " ]";
    }
}