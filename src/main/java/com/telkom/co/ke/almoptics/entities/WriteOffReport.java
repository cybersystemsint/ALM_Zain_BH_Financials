package com.telkom.co.ke.almoptics.entities;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "tb_WriteOffReport")
public class WriteOffReport implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SerialNumber")
    private String serialNumber;

    @Column(name = "RFID")
    private String rfid;

    @Column(name = "TAG")
    private String tag;

    @Column(name = "AssetType")
    private String assetType;

    @Column(name = "AssetID")
    private String assetId;

    @Column(name = "NEType")
    private String neType;

    @Column(name = "WriteOffDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date writeOffDate;

    @Column(name = "StatusFlag")
    private String statusFlag;

    @Column(name = "InsertedBy")
    private String insertedBy;

    @Column(name = "InsertDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date insertDate;

    // Constructors
    public WriteOffReport() {}

    @PrePersist
    protected void onCreate() {
        this.insertDate = new Date();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getRfid() { return rfid; }
    public void setRfid(String rfid) { this.rfid = rfid; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }

    public String getNeType() { return neType; }
    public void setNeType(String neType) { this.neType = neType; }

    public Date getWriteOffDate() { return writeOffDate; }
    public void setWriteOffDate(Date writeOffDate) { this.writeOffDate = writeOffDate; }

    public String getStatusFlag() { return statusFlag; }
    public void setStatusFlag(String statusFlag) { this.statusFlag = statusFlag; }

    public String getInsertedBy() { return insertedBy; }
    public void setInsertedBy(String insertedBy) { this.insertedBy = insertedBy; }

    public Date getInsertDate() { return insertDate; }
    public void setInsertDate(Date insertDate) { this.insertDate = insertDate; }
}