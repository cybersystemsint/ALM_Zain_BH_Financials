package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.*;

/**
 *
 * @author Gilian
 */
@Entity
@Table(name = "`tb_unmappedPassive_Inventory`", indexes = {
        @Index(name = "PRIMARY", columnList = "ID", unique = true)})
public class UnmappedPassiveInventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    @Column(name = "ID")
    private Long id;

    @Column(name = "ElementID")
    private String objectId;

    @Column(name = "Element_Type")
    private String elementType;

    @Column(name = "Parent_NE_Type")
    private String parentNEType;

    @Column(name = "Site_ID")
    private String siteId;

    @Column(name = "Category")
    private String category;

    @Column(name = "Item_BarCode")
    private String itemBarCode;

    @Column(name = "Serial", unique = true, nullable = false)
    private String serial;

    @Column(name = "UOM")
    private String uom;

    @Column(name = "Entry_Date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date entryDate;

    @Column(name = "Entry_User")
    private String entryUser;

    @Column(name = "Model")
    private String model;

    @Column(name = "Item_Classification")
    private String itemClassification;

    @Column(name = "Item_Classification_2")
    private String itemClassification2;

    @Column(name = "Notes")
    private String notes;

    @Column(name = "PR_PONo")
    private String prPoNo;

    @PrePersist
    protected void onCreate() {
        this.entryDate = new Date();
    }

    public UnmappedPassiveInventory() {
    }

    public Long getId() {
        return  (id);
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getItemBarCode() {
        return itemBarCode;
    }

    public void setItemBarCode(String itemBarCode) {
        this.itemBarCode = itemBarCode;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPrPoNo() {
        return prPoNo;
    }

    public void setPrPoNo(String prPoNo) {
        this.prPoNo = prPoNo;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof UnmappedPassiveInventory)) {
            return false;
        }
        UnmappedPassiveInventory other = (UnmappedPassiveInventory) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.telkom.co.ke.almoptics.entities.UnmappedPassiveInventory[ id=" + id + " ]";
    }
}