package com.telkom.co.ke.almoptics.dto;


import java.util.List;
 
public class FilterRequest {
 
    private Integer page;
 
    private Integer size;
 
    private String format;
 
    private boolean exportAll;
 
    private String dateFrom;
 
    private String dateTo;
 
    private List<Filter> filters;
 
    // ── Constructors ────────────────────────────────────────────────────────
 
    public FilterRequest() {}
 
    public FilterRequest(Integer page, Integer size, String format, boolean exportAll) {
        this.page      = page;
        this.size      = size;
        this.format    = format;
        this.exportAll = exportAll;
    }
 
    // ── Getters / Setters ───────────────────────────────────────────────────
 
    public Integer getPage()              { return page; }
    public void    setPage(Integer page)  { this.page = page; }
 
    public Integer getSize()              { return size; }
    public void    setSize(Integer size)  { this.size = size; }
 
    public String  getFormat()                { return format; }
    public void    setFormat(String format)   { this.format = format; }
 
    public boolean isExportAll()              { return exportAll; }
    public void    setExportAll(boolean v)    { this.exportAll = v; }
 
    public String  getDateFrom()              { return dateFrom; }
    public void    setDateFrom(String d)      { this.dateFrom = d; }
 
    public String  getDateTo()                { return dateTo; }
    public void    setDateTo(String d)        { this.dateTo = d; }
 
    public List<Filter> getFilters()               { return filters; }
    public void         setFilters(List<Filter> f) { this.filters = f; }
 
    // ── Nested DTO ──────────────────────────────────────────────────────────
 
    public static class Filter {
 
        private String         column;
        private String         value;
        private FilterOperator operator;
 
        public Filter() {}
 
        public Filter(String column, String value, FilterOperator operator) {
            this.column   = column;
            this.value    = value;
            this.operator = operator;
        }
 
        public String         getColumn()               { return column; }
        public void           setColumn(String column)  { this.column = column; }
 
        public String         getValue()                { return value; }
        public void           setValue(String value)    { this.value = value; }
 
        public FilterOperator getOperator()                   { return operator; }
        public void           setOperator(FilterOperator op)  { this.operator = op; }
 
        /**
         * Supported filter operators.
         *
         * EQUALS        – exact case-insensitive match
         * CONTAINS      – LIKE %value%  (default when operator is omitted)
         * STARTS_WITH   – LIKE value%
         * ENDS_WITH     – LIKE %value
         * IS_EMPTY      – NULL or blank
         * IS_NOT_EMPTY  – NOT NULL and not blank
         * IS_ANY_OF     – value is comma-separated list; matches any token (OR)
         */
        public enum FilterOperator {
            EQUALS,
            CONTAINS,
            STARTS_WITH,
            ENDS_WITH,
            IS_EMPTY,
            IS_NOT_EMPTY,
            IS_ANY_OF
        }
    }
}
