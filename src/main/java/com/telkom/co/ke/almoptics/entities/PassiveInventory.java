package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.*;

/**
 *
 * @author Gilian
 */
@Entity
@Table(name = "`tb_Passive_Inventory`", indexes = {
        @Index(name = "PRIMARY", columnList = "ObjectID", unique = true)})
public class PassiveInventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ObjectID")
    private String objectId;

    @Column(name = "Id")
    private String id;

    @Column(name = "ParentName")
    private String parentName;

    @Column(name = "SiteId")
    private String siteId;

    @Column(name = "Status")
    private String status;

    @Column(name = "Latitude")
    private Double latitude;

    @Column(name = "Longitude")
    private Double longitude;

    @Column(name = "AuditDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date auditDate;

    @Column(name = "AuditUser")
    private String auditUser;

    @Column(name = "Dimension")
    private String dimension;

    @Column(name = "`Dimension (H)`")
    private String dimensionH;

    @Column(name = "`Dimension (W)`")
    private String dimensionW;

    @Column(name = "`Dimension (L)`")
    private String dimensionL;

    @Column(name = "EntryDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date entryDate;

    @Column(name = "EntryUser")
    private String entryUser;

    @Column(name = "InternalReference")
    private String internalReference;

    @Column(name = "ItemBarCode")
    private String itemBarCode;

    @Column(name = "ItemCapacity")
    private String itemCapacity;

    @Column(name = "ItemClassification")
    private String itemClassification;

    @Column(name = "ItemClassification2")
    private String itemClassification2;

    @Column(name = "ItemCode")
    private String itemCode;

    @Column(name = "ItemMake")
    private String itemMake;

    @Column(name = "ItemStatus")
    private String itemStatus;

    @Column(name = "ItemSupplier")
    private String itemSupplier;

    @Column(name = "LastModifiedDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastModifiedDate;

    @Column(name = "LastModifiedUser")
    private String lastModifiedUser;

    @Column(name = "LocationAddress")
    private String locationAddress;

    @Column(name = "`LocationClassification(floor test)`")
    private String locationClassification;

    @Column(name = "LocationSubType")
    private String locationSubType;

    @Column(name = "Model")
    private String model;

    @Column(name = "Notes")
    private String notes;

    @Column(name = "Part")
    private String part;

    @Column(name = "Serial", unique = true, nullable = false)
    private String serial;

    @Column(name = "ShelterVendor")
    private String shelterVendor;

    @Column(name = "`Shelter/Room ID`")
    private String shelterRoomId;

    @Column(name = "UOM")
    private String uom;

    @Column(name = "CategoryInNEP")
    private String categoryInNEP;

    @Column(name = "Ownership")
    private String ownership;

    @Column(name = "ScrapStatus")
    private String scrapStatus;

    @Column(name = "ScrapUser")
    private String scrapUser;

    @Column(name = "ScrapDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date scrapDate;

    @Column(name = "LastPVstatus")
    private String lastPVStatus;

    @Column(name = "`Last PV User`")
    private String lastPVUser;

    @Column(name = "LastPVdate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastPVDate;

    @Column(name = "`PR/PONo`")
    private String prPoNo;

    @Column(name = "ApprovalStatus")
    private String approvalStatus;

    @PrePersist
    protected void onCreate() {
        this.entryDate = new Date();
    }

    public PassiveInventory() {
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Date getAuditDate() {
        return auditDate;
    }

    public void setAuditDate(Date auditDate) {
        this.auditDate = auditDate;
    }

    public String getAuditUser() {
        return auditUser;
    }

    public void setAuditUser(String auditUser) {
        this.auditUser = auditUser;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getDimensionH() {
        return dimensionH;
    }

    public void setDimensionH(String dimensionH) {
        this.dimensionH = dimensionH;
    }

    public String getDimensionW() {
        return dimensionW;
    }

    public void setDimensionW(String dimensionW) {
        this.dimensionW = dimensionW;
    }

    public String getDimensionL() {
        return dimensionL;
    }

    public void setDimensionL(String dimensionL) {
        this.dimensionL = dimensionL;
    }

    public Date getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(Date entryDate) {
        this.entryDate = entryDate;
    }

    public String getEntryUser() {
        return entryUser;
    }

    public void setEntryUser(String entryUser) {
        this.entryUser = entryUser;
    }

    public String getInternalReference() {
        return internalReference;
    }

    public void setInternalReference(String internalReference) {
        this.internalReference = internalReference;
    }

    public String getItemBarCode() {
        return itemBarCode;
    }

    public void setItemBarCode(String itemBarCode) {
        this.itemBarCode = itemBarCode;
    }

    public String getItemCapacity() {
        return itemCapacity;
    }

    public void setItemCapacity(String itemCapacity) {
        this.itemCapacity = itemCapacity;
    }

    public String getItemClassification() {
        return itemClassification;
    }

    public void setItemClassification(String itemClassification) {
        this.itemClassification = itemClassification;
    }

    public String getItemClassification2() {
        return itemClassification2;
    }

    public void setItemClassification2(String itemClassification2) {
        this.itemClassification2 = itemClassification2;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemMake() {
        return itemMake;
    }

    public void setItemMake(String itemMake) {
        this.itemMake = itemMake;
    }

    public String getItemStatus() {
        return itemStatus;
    }

    public void setItemStatus(String itemStatus) {
        this.itemStatus = itemStatus;
    }

    public String getItemSupplier() {
        return itemSupplier;
    }

    public void setItemSupplier(String itemSupplier) {
        this.itemSupplier = itemSupplier;
    }

    public Date getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Date lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getLastModifiedUser() {
        return lastModifiedUser;
    }

    public void setLastModifiedUser(String lastModifiedUser) {
        this.lastModifiedUser = lastModifiedUser;
    }

    public String getLocationAddress() {
        return locationAddress;
    }

    public void setLocationAddress(String locationAddress) {
        this.locationAddress = locationAddress;
    }

    public String getLocationClassification() {
        return locationClassification;
    }

    public void setLocationClassification(String locationClassification) {
        this.locationClassification = locationClassification;
    }

    public String getLocationSubType() {
        return locationSubType;
    }

    public void setLocationSubType(String locationSubType) {
        this.locationSubType = locationSubType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPart() {
        return part;
    }

    public void setPart(String part) {
        this.part = part;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getShelterVendor() {
        return shelterVendor;
    }

    public void setShelterVendor(String shelterVendor) {
        this.shelterVendor = shelterVendor;
    }

    public String getShelterRoomId() {
        return shelterRoomId;
    }

    public void setShelterRoomId(String shelterRoomId) {
        this.shelterRoomId = shelterRoomId;
    }

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
    }

    public String getCategoryInNEP() {
        return categoryInNEP;
    }

    public void setCategoryInNEP(String categoryInNEP) {
        this.categoryInNEP = categoryInNEP;
    }

    public String getOwnership() {
        return ownership;
    }

    public void setOwnership(String ownership) {
        this.ownership = ownership;
    }

    public String getScrapStatus() {
        return scrapStatus;
    }

    public void setScrapStatus(String scrapStatus) {
        this.scrapStatus = scrapStatus;
    }

    public String getScrapUser() {
        return scrapUser;
    }

    public void setScrapUser(String scrapUser) {
        this.scrapUser = scrapUser;
    }

    public Date getScrapDate() {
        return scrapDate;
    }

    public void setScrapDate(Date scrapDate) {
        this.scrapDate = scrapDate;
    }

    public String getLastPVStatus() {
        return lastPVStatus;
    }

    public void setLastPVStatus(String lastPVStatus) {
        this.lastPVStatus = lastPVStatus;
    }

    public String getLastPVUser() {
        return lastPVUser;
    }

    public void setLastPVUser(String lastPVUser) {
        this.lastPVUser = lastPVUser;
    }

    public Date getLastPVDate() {
        return lastPVDate;
    }

    public void setLastPVDate(Date lastPVDate) {
        this.lastPVDate = lastPVDate;
    }

    public String getPrPoNo() {
        return prPoNo;
    }

    public void setPrPoNo(String prPoNo) {
        this.prPoNo = prPoNo;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getSerialNumber() {
        return serial;
    }

    public void setSerialNumber(String serialNumber) {
        this.serial = serialNumber;
    }
}