package com.telkom.co.ke.almoptics.dto;

import java.util.List;

public class ApprovalRequest {

    private String searchTerm;
    private String searchQuery;
    private String columnName;
    private Integer page;
    private Integer afterId;
    private Integer size;
    private String level;

    private List<Filter> filters;

    private String startdate;
    private String enddate;

    /**
     * Date-range filter for the Financial Report fetch endpoint
     * ({@code POST /api/financial/fetch-financereport}). Both fields
     * apply to the {@code DateOfService} column on
     * {@code tb_FinancialReport}, which mirrors the filter target used
     * by {@code GET /monthly-report}.
     *
     * <p>Format: ISO date string ({@code yyyy-MM-dd}). Either side may
     * be omitted for an open-ended range.</p>
     *
     * <p>Note: the existing {@code startDate}/{@code endDate} fields
     * (used by the approvals endpoint) keep filtering on
     * {@code InsertDate}; we keep them separate so a caller can combine
     * the two if desired.</p>
     */
    private String dateFrom;
    private String dateTo;

    private Integer startRecordNo;
    private Integer endRecordNo;

    private String format;                    // ← This field exists

    private String siteId;
    private String inventoryType;

    // Finance Approval Specific Fields
    private String team;
    private String objectType;
    private String assetId;
    private String originalStatus;
    private String updatedStatus;
    private String processId;
    private String startDate;
    private String endDate;
    private String objectStatus;

    private boolean exportAll = false;

    // ====================== GETTERS & SETTERS ======================

    public String getSearchTerm() { return searchTerm; }
    public void setSearchTerm(String searchTerm) { this.searchTerm = searchTerm; }

    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getAfterId() { return afterId; }
    public void setAfterId(Integer afterId) { this.afterId = afterId; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public List<Filter> getFilters() { return filters; }
    public void setFilters(List<Filter> filters) { this.filters = filters; }

    public String getStartdate() { return startdate; }
    public void setStartdate(String startdate) { this.startdate = startdate; }

    public String getEnddate() { return enddate; }
    public void setEnddate(String enddate) { this.enddate = enddate; }

    public String getDateFrom() { return dateFrom; }
    public void setDateFrom(String dateFrom) { this.dateFrom = dateFrom; }

    public String getDateTo() { return dateTo; }
    public void setDateTo(String dateTo) { this.dateTo = dateTo; }

    public Integer getStartRecordNo() { return startRecordNo; }
    public void setStartRecordNo(Integer startRecordNo) { this.startRecordNo = startRecordNo; }

    public Integer getEndRecordNo() { return endRecordNo; }
    public void setEndRecordNo(Integer endRecordNo) { this.endRecordNo = endRecordNo; }

    /** THIS IS THE METHOD YOU NEED */
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }

    public String getInventoryType() { return inventoryType; }
    public void setInventoryType(String inventoryType) { this.inventoryType = inventoryType; }

    // Finance Approval getters/setters
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }

    public String getOriginalStatus() { return originalStatus; }
    public void setOriginalStatus(String originalStatus) { this.originalStatus = originalStatus; }

    public String getUpdatedStatus() { return updatedStatus; }
    public void setUpdatedStatus(String updatedStatus) { this.updatedStatus = updatedStatus; }

    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getObjectStatus() { return objectStatus; }
    public void setObjectStatus(String objectStatus) { this.objectStatus = objectStatus; }

    public boolean isExportAll() {
        return exportAll;
    }

    public void setExportAll(boolean exportAll) {
        this.exportAll = exportAll;
    }

    // ====================== INNER CLASSES ======================
    public static class Filter {
        private String column;
        private String value;
        private FOperator operator = FOperator.CONTAINS;

        public String getColumn() { return column; }
        public void setColumn(String column) { this.column = column; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public FOperator getOperator() { return operator; }
        public void setOperator(FOperator operator) {
            this.operator = operator != null ? operator : FOperator.CONTAINS;
        }
    }

    public enum FOperator {
        CONTAINS, EQUALS, STARTS_WITH, ENDS_WITH,
        IS_EMPTY, IS_NOT_EMPTY, IS_ANY_OF
    }
}