package com.telkom.co.ke.almoptics.dto;


import java.math.BigDecimal;

public class ConsolidatedReportResponse {

    private PageResult<ConsolidatedReport> pageResult;
    private BigDecimal totalCost;
    private BigDecimal totalNBV;
    private BigDecimal totalDepreciation;
    private BigDecimal filteredCost;
    private BigDecimal filteredNBV;
    private BigDecimal filteredDepreciation;

    public ConsolidatedReportResponse() {
    }

    public ConsolidatedReportResponse(
            PageResult<ConsolidatedReport> pageResult,
            BigDecimal totalCost,
            BigDecimal totalNBV,
            BigDecimal totalDepreciation,
            BigDecimal filteredCost,
            BigDecimal filteredNBV,
            BigDecimal filteredDepreciation) {
        this.pageResult = pageResult;
        this.totalCost = totalCost;
        this.totalNBV = totalNBV;
        this.totalDepreciation = totalDepreciation;
        this.filteredCost = filteredCost;
        this.filteredNBV = filteredNBV;
        this.filteredDepreciation = filteredDepreciation;
    }

    public PageResult<ConsolidatedReport> getPageResult() {
        return pageResult;
    }

    public void setPageResult(PageResult<ConsolidatedReport> pageResult) {
        this.pageResult = pageResult;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getTotalNBV() {
        return totalNBV;
    }

    public void setTotalNBV(BigDecimal totalNBV) {
        this.totalNBV = totalNBV;
    }

    public BigDecimal getTotalDepreciation() {
        return totalDepreciation;
    }

    public void setTotalDepreciation(BigDecimal totalDepreciation) {
        this.totalDepreciation = totalDepreciation;
    }

    public BigDecimal getFilteredCost() {
        return filteredCost;
    }

    public void setFilteredCost(BigDecimal filteredCost) {
        this.filteredCost = filteredCost;
    }

    public BigDecimal getFilteredNBV() {
        return filteredNBV;
    }

    public void setFilteredNBV(BigDecimal filteredNBV) {
        this.filteredNBV = filteredNBV;
    }

    public BigDecimal getFilteredDepreciation() {
        return filteredDepreciation;
    }

    public void setFilteredDepreciation(BigDecimal filteredDepreciation) {
        this.filteredDepreciation = filteredDepreciation;
    }
}
