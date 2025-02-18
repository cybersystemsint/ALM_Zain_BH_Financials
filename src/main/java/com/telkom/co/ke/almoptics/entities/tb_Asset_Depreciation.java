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
@Table(name = "`tb_Asset_Depreciation`", indexes = {@Index(name = "PRIMARY", columnList = "recordId", unique = false)})
public class tb_Asset_Depreciation implements Serializable {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "recordId")
  private int recordId;
  
  @Column(name = "recordDatetime")
  private Date recordDatetime;
  
  @Column(name = "assetCode")
  private String assetCode;
  
  @Column(name = "depreciationDate")
  private String depreciationDate;
  
  @Column(name = "assetBookValue")
  private double assetBookValue;
  
  public tb_Asset_Depreciation() {}
  
  public tb_Asset_Depreciation(int recordId, Date recordDatetime, String assetCode, String depreciationDate, double assetBookValue) {}
  
  public int getRecordId() {
    return this.recordId;
  }
  
  public void setRecordId(int recordId) {
    this.recordId = recordId;
  }
  
  public Date getRecordDatetime() {
    return this.recordDatetime;
  }
  
  public void setRecordDatetime(Date recordDatetime) {
    this.recordDatetime = recordDatetime;
  }
  
  public String getAssetCode() {
    return this.assetCode;
  }
  
  public void setAssetCode(String assetCode) {
    this.assetCode = assetCode;
  }
  
  public String getDepreciationDate() {
    return this.depreciationDate;
  }
  
  public void setDepreciationDate(String depreciationDate) {
    this.depreciationDate = depreciationDate;
  }
  
  public double getAssetBookValue() {
    return this.assetBookValue;
  }
  
  public void setAssetBookValue(double assetBookValue) {
    this.assetBookValue = assetBookValue;
  }
}

