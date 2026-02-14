package com.zain.bh.alm.financials.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "`tb_Item`")
public class Item implements Serializable {
	private static final long serialVersionUID = 1L;

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
		return recordNo;
	}

	public void setRecordNo(int recordNo) {
		this.recordNo = recordNo;
	}

	public Date getRecordDatetime() {
		return recordDatetime;
	}

	public void setRecordDatetime(Date recordDatetime) {
		this.recordDatetime = recordDatetime;
	}

	public String getItemCode() {
		return itemCode;
	}

	public void setItemCode(String itemCode) {
		this.itemCode = itemCode;
	}

	public String getItemName() {
		return itemName;
	}

	public void setItemName(String itemName) {
		this.itemName = itemName;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
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

	public String getMake() {
		return make;
	}

	public void setMake(String make) {
		this.make = make;
	}

	public int getNetworkElement() {
		return networkElement;
	}

	public void setNetworkElement(int networkElement) {
		this.networkElement = networkElement;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getVendorUnitFamilyType() {
		return vendorUnitFamilyType;
	}

	public void setVendorUnitFamilyType(String vendorUnitFamilyType) {
		this.vendorUnitFamilyType = vendorUnitFamilyType;
	}

	public String getDepreciationMethod() {
		return depreciationMethod;
	}

	public void setDepreciationMethod(String depreciationMethod) {
		this.depreciationMethod = depreciationMethod;
	}

	public int getUsefulLife() {
		return usefulLife;
	}

	public void setUsefulLife(int usefulLife) {
		this.usefulLife = usefulLife;
	}

	public String getIsItDevice() {
		return isItDevice;
	}

	public void setIsItDevice(String isItDevice) {
		this.isItDevice = isItDevice;
	}
}
