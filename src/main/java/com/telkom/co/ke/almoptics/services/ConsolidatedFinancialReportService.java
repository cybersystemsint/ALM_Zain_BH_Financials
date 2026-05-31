package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.dto.ConsolidatedReport;
import com.telkom.co.ke.almoptics.dto.ConsolidatedReportResponse;
import com.telkom.co.ke.almoptics.dto.FilterRequest;
import com.telkom.co.ke.almoptics.dto.PageResult;

public interface ConsolidatedFinancialReportService {

    PageResult<ConsolidatedReport> getPage(FilterRequest request);

    ConsolidatedReportResponse getReport(FilterRequest request);

    byte[] exportCsv(FilterRequest request);

    byte[] exportExcel(FilterRequest request);
}
