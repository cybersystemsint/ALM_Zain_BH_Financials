package com.telkom.co.ke.almoptics.controllers;


import com.telkom.co.ke.almoptics.dto.ConsolidatedReportResponse;
import com.telkom.co.ke.almoptics.dto.FilterRequest;
import com.telkom.co.ke.almoptics.services.ConsolidatedFinancialReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/financial")
@CrossOrigin(origins = "*")
@Tag(name = "Financial Reports",
     description = "Paged FAR / consolidated / allocation / depreciation / journal / write-off / missing reports + exports")
public class ConsolidatedFinancialReportController {

    private static final Logger log =
            LoggerFactory.getLogger(ConsolidatedFinancialReportController.class);

    private static final String TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss";

    private final ConsolidatedFinancialReportService service;

    public ConsolidatedFinancialReportController(
            ConsolidatedFinancialReportService service) {
        this.service = service;
    }

    @Operation(
        summary = "Consolidated Financial Report – fetch or export",
        description = """
                Dual-behaviour endpoint.

                **Fetch (format absent):** Returns a paginated JSON response with
                report rows and summary totals.

                **Export (format = csv | excel | xlsx):** Streams the full matching
                dataset as a downloadable file; `page` and `size` are ignored.

                All `filters[]` entries are combined with AND logic.
                `dateFrom` / `dateTo` filter on `dateOfService` (inclusive).
                Multi-value filtering is supported using `IS_ANY_OF` with comma-separated values.
                """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "Paginated fetch",
                        value = """
                                {
                                  "page": 0,
                                  "size": 100,
                                  "dateFrom": "01-JAN-2025",
                                  "dateTo": "31-DEC-2025",
                                  "filters": [
                                    { "column": "vendorName", "value": "acme", "operator": "CONTAINS" },
                                    { "column": "oracleAssetId", "value": "4630888,4674068", "operator": "IS_ANY_OF" }
                                  ]
                                }
                                """),
                    @ExampleObject(name = "CSV export",
                        value = """
                                {
                                  "format": "csv",
                                  "dateFrom": "01-JAN-2025",
                                  "dateTo": "31-DEC-2025"
                                }
                                """),
                    @ExampleObject(name = "Excel export",
                        value = """
                                { "format": "xlsx" }
                                """)
                }
            )
        ),
        responses = {
            @ApiResponse(responseCode = "200",
                description = "Paginated JSON data with summary totals",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ConsolidatedReportResponse.class))),
            @ApiResponse(responseCode = "200",
                description = "File download (CSV or Excel)",
                content = @Content(mediaType = "application/octet-stream")),
            @ApiResponse(responseCode = "400",
                description = "Unsupported export format"),
            @ApiResponse(responseCode = "500",
                description = "Internal server error")
        }
    )
    @PostMapping("/consolidated-report")
    public ResponseEntity<?> consolidatedReport(
            @RequestBody(required = false) FilterRequest request) {

        log.info("POST /consolidated-report – format={}, page={}, size={}",
                request != null ? request.getFormat() : null,
                request != null ? request.getPage() : null,
                request != null ? request.getSize() : null);

        String format = request != null && request.getFormat() != null
                ? request.getFormat().trim().toLowerCase()
                : "";

        try {
            return switch (format) {
                case "csv" -> csvExport(request);
                case "excel", "xlsx" -> excelExport(request);
                case "" -> jsonFetch(request);
                default -> ResponseEntity
                        .badRequest()
                        .body("Unsupported format '" + format
                                + "'. Supported values: csv, excel, xlsx.");
            };
        } catch (Exception e) {
            log.error("Error processing consolidated report request", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process request: " + e.getMessage());
        }
    }

    private ResponseEntity<ConsolidatedReportResponse> jsonFetch(FilterRequest request) {
        ConsolidatedReportResponse result = service.getReport(request);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<byte[]> csvExport(FilterRequest request) {
        byte[] csv = service.exportCsv(request);
        String filename = "consolidated_report_" + timestamp() + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(csv.length);

        log.info("CSV export complete – {} bytes, file={}", csv.length, filename);
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    private ResponseEntity<byte[]> excelExport(FilterRequest request) {
        byte[] xlsx = service.exportExcel(request);
        String filename = "consolidated_report_" + timestamp() + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(xlsx.length);

        log.info("Excel export complete – {} bytes, file={}", xlsx.length, filename);
        return new ResponseEntity<>(xlsx, headers, HttpStatus.OK);
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN));
    }
}
