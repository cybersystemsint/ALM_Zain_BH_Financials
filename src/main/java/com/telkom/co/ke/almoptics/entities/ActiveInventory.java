package com.telkom.co.ke.almoptics.entities;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.*;

@Entity
@Table(name = "`tb_Node`", indexes = {
        @Index(name = "PRIMARY", columnList = "id", unique = true)})
public class ActiveInventory implements Serializable {

    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "siteId")
    private String siteId;

    @Column(name = "Zone")
    private String zone;

    @Column(name = "NodeId")
    private String nodeId;

    @Column(name = "NodeName")
    private String nodeName;

    @Column(name = "NodeType")
    private String nodeType;

    @Column(name = "Manufacturer")
    private String manufacturer;

    @Column(name = "Element")
    private String element;

    @Column(name = "Model")
    private String model;

    @Column(name = "PartNumber")
    private String partNumber;

    @Column(name = "SerialNumber", unique = true, nullable = false)
    private String serialNumber;

    @Column(name = "Description")
    private String description;

    @Column(name = "ManufacturingDate")
    private Date manufacturingDate;

    @Column(name = "IssueNumber")
    private String issueNumber;

    @Column(name = "TypeCategory")
    private String typeCategory;

    @Column(name = "ChangedBy")
    private String changedBy;

    @Column(name = "ChangeDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date changeDate;

    @Column(name = "InsertedBy")
    private String insertedBy;

    @Column(name = "InsertDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date insertDate;

    @Column(name = "ApprovalStatus")
    private String approvalStatus;

    @Column(name = "XMLUpdateDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date xmlUpdateDate;

    @Column(name = "SiteName")
    private String siteName;

    @PrePersist
    protected void onCreate() {
        this.insertDate = new Date();
    }

    public ActiveInventory() {
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

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

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

    public String getElement() {
        return element;
    }

    public void setElement(String element) {
        this.element = element;
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

    public String getIssueNumber() {
        return issueNumber;
    }

    public void setIssueNumber(String issueNumber) {
        this.issueNumber = issueNumber;
    }

    public String getTypeCategory() {
        return typeCategory;
    }

    public void setTypeCategory(String typeCategory) {
        this.typeCategory = typeCategory;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public Date getChangeDate() {
        return changeDate;
    }

    public void setChangeDate(Date changeDate) {
        this.changeDate = changeDate;
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

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Date getXmlUpdateDate() {
        return xmlUpdateDate;
    }

    public void setXmlUpdateDate(Date xmlUpdateDate) {
        this.xmlUpdateDate = xmlUpdateDate;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }
}