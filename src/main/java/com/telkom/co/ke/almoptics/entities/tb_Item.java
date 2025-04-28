/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 *
 * @author jgithu
 */

@Entity
@Table(name = "`tb_Item`", indexes = {
    @Index(name = "PRIMARY", columnList = "recordNo", unique = false)})
public class tb_Item implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recordNo")
    private int recordNo;

    @Column(name = "recordDatetime")
    private Date recordDatetime;

    @Column(name = "itemCode")
    private String itemCode;

    @Column(name = "itemName")
    private String itemName;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "model")
    private String model;

    @Column(name = "make")
    private String make;

    @Column(name = "networkElement")
    private int networkElement;

    @Column(name = "details")
    private String details;

    @Column(name = "status")
    private String status;

    @Column(name = "vendorUnitFamilyType")
    private String vendorUnitFamilyType;

    @Column(name = "depreciationMethod")
    private String depreciationMethod;

    @Column(name = "usefulLife")
    private int usefulLife;

    @Column(name = "isItDevice")
    private String isItDevice;

    public int getRecordNo() {
        return this.recordNo;
    }

    public void setRecordNo(int recordNo) {
        this.recordNo = recordNo;
    }

    public Date getRecordDatetime() {
        return this.recordDatetime;
    }

    public void setRecordDatetime(Date recordDatetime) {
        this.recordDatetime = recordDatetime;
    }

    public String getItemCode() {
        return this.itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemName() {
        return this.itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getQuantity() {
        return this.quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getManufacturer() {
        return this.manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return this.model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getMake() {
        return this.make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public int getNetworkElement() {
        return this.networkElement;
    }

    public void setNetworkElement(int networkElement) {
        this.networkElement = networkElement;
    }

    public String getDetails() {
        return this.details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVendorUnitFamilyType() {
        return this.vendorUnitFamilyType;
    }

    public void setVendorUnitFamilyType(String vendorUnitFamilyType) {
        this.vendorUnitFamilyType = vendorUnitFamilyType;
    }

    public String getDepreciationMethod() {
        return this.depreciationMethod;
    }

    public void setDepreciationMethod(String depreciationMethod) {
        this.depreciationMethod = depreciationMethod;
    }

    public int getUsefulLife() {
        return this.usefulLife;
    }

    public void setUsefulLife(int usefulLife) {
        this.usefulLife = usefulLife;
    }

    public String getIsItDevice() {
        return this.isItDevice;
    }

    public void setIsItDevice(String isItDevice) {
        this.isItDevice = isItDevice;
    }
}
