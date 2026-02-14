package com.zain.bh.alm.financials.controller;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.service.FinancialReportDeletionService;
import com.zain.bh.alm.financials.service.FinancialReportExportService;
import com.zain.bh.alm.financials.service.FinancialReportModificationService;
import com.zain.bh.alm.financials.service.FinancialReportQueryService;
import com.zain.bh.alm.financials.service.FinancialReportService;
import com.zain.bh.alm.financials.service.FinancialReportUploadService;
import com.zain.bh.alm.financials.service.WriteOffImportService;
import com.zain.bh.alm.financials.service.WriteOffSearchService;

@RestController
@RequestMapping("/api/financial")
@CrossOrigin(origins = "*")
public class FinancialReportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialReportController.class);

    private final FinancialReportService financialReportService;
    private final FinancialReportQueryService queryService;
    private final FinancialReportDeletionService deletionService;
    private final FinancialReportUploadService uploadService;
    private final WriteOffImportService writeOffImportService;
    private final FinancialReportExportService exportService;
    private final FinancialReportModificationService modificationService;
    private final WriteOffSearchService writeOffSearchService;

    public FinancialReportController(FinancialReportService financialReportService,
                                      FinancialReportQueryService queryService,
                                      FinancialReportDeletionService deletionService,
                                      FinancialReportUploadService uploadService,
                                      WriteOffImportService writeOffImportService,
                                      FinancialReportExportService exportService,
                                      FinancialReportModificationService modificationService,
                                      WriteOffSearchService writeOffSearchService) {
        this.financialReportService = financialReportService;
        this.queryService = queryService;
        this.deletionService = deletionService;
        this.uploadService = uploadService;
        this.writeOffImportService = writeOffImportService;
        this.exportService = exportService;
        this.modificationService = modificationService;
        this.writeOffSearchService = writeOffSearchService;
    }

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String asAtDate) {
        try {
            Map<String, Object> response = queryService.getAllReports(page, size, search, sortBy, sortDir, asAtDate);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            LOGGER.error("Error retrieving financial reports", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error retrieving reports: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/reports/serial/{serialNumber}")
    public ResponseEntity<FinancialReport> getReportBySerialNumber(@PathVariable String serialNumber) {
        try {
            Optional<FinancialReport> report = financialReportService.findBySerialNumber(serialNumber);
            return report.map(r -> new ResponseEntity<>(r, HttpStatus.OK))
                    .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
        } catch (Exception e) {
            LOGGER.error("Error retrieving report by serial number: {}", serialNumber, e);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/reports/asset/{assetName}")
    public ResponseEntity<?> getReportsByAssetName(@PathVariable String assetName) {
        try {
            Optional<FinancialReport> reportOpt = financialReportService.findByAssetName(assetName);
            if (!reportOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No reports found for asset: " + assetName);
            }
            return ResponseEntity.ok(reportOpt.get());
        } catch (Exception e) {
            LOGGER.error("Error retrieving reports by asset name: {}", assetName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while fetching the reports.");
        }
    }

    @DeleteMapping("/reports/{serialNumber}")
    public ResponseEntity<Map<String, Object>> deleteReport(@PathVariable String serialNumber, Principal principal) {
        try {
            String username = principal != null ? principal.getName() : "system";
            Map<String, Object> response = deletionService.deleteReport(serialNumber, username);
            String status = (String) response.get("status");
            if ("NOT_FOUND".equals(status)) {
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            LOGGER.error("Error deleting financial report with serial number: {}", serialNumber, e);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Error deleting financial report: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFinancialReports(@RequestBody List<FinancialReport> reports, Principal principal) {
        if (reports == null || reports.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "No data provided for upload!");
            response.put("status", "error");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        try {
            String username = principal != null ? principal.getName() : "system";
            Map<String, Object> response = uploadService.uploadReports(reports, username);
            HttpStatus status = "error".equals(response.get("status")) ? HttpStatus.BAD_REQUEST : HttpStatus.CREATED;
            return new ResponseEntity<>(response, status);
        } catch (Exception e) {
            LOGGER.error("Unexpected error processing uploaded financial reports", e);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Upload failed: " + e.getMessage());
            response.put("status", "error");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/import-writeoff")
    public ResponseEntity<Map<String, Object>> importWriteOffFromCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "separator", defaultValue = ",") char separator,
            @RequestParam(value = "ignoreHeader", defaultValue = "true") boolean ignoreHeader,
            Principal principal) {
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("message", "Please upload a CSV file.");
            response.put("status", "error");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        if (!file.getOriginalFilename().endsWith(".csv")) {
            response.put("message", "Only CSV files are supported.");
            response.put("status", "error");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        try {
            String username = principal != null ? principal.getName() : "system";
            response = writeOffImportService.importFromCsv(file, separator, ignoreHeader, username);
            HttpStatus status = "error".equals(response.get("status")) ? HttpStatus.BAD_REQUEST : HttpStatus.OK;
            return new ResponseEntity<>(response, status);
        } catch (Exception e) {
            LOGGER.error("Error importing write-off data from CSV: {}", e.getMessage(), e);
            response.put("message", "Failed to process the CSV file: " + e.getMessage());
            response.put("status", "error");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportFinancialReportsToCsv() {
        try {
            byte[] csvData = exportService.exportFinancialReportsToCsv();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=financial_reports.csv");
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            return new ResponseEntity<>(csvData, headers, HttpStatus.OK);
        } catch (Exception e) {
            LOGGER.error("Error exporting financial reports to CSV", e);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/export-writeoffs")
    public ResponseEntity<?> exportWriteOffReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "csv") String format) {
        try {
            Object result = exportService.exportWriteOffReports(page, size, sort, format);
            if (result instanceof Map) {
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=writeoff_reports.csv");
                headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
                return new ResponseEntity<>((byte[]) result, headers, HttpStatus.OK);
            }
        } catch (Exception e) {
            LOGGER.error("Error processing write-off reports", e);
            return new ResponseEntity<>(Map.of("error", "Failed to process request"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/search-writeoffs")
    public ResponseEntity<Map<String, Object>> searchWriteOffReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String query) {
        try {
            Map<String, Object> response = writeOffSearchService.searchWriteOffReports(page, size, sort, query);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            LOGGER.error("Error searching write-off reports", e);
            return new ResponseEntity<>(Map.of("error", "Failed to search data: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/reports/modify/{identifier}")
    public ResponseEntity<?> modifyReport(@PathVariable String identifier, @RequestBody FinancialReport updatedReport) {
        LOGGER.info("Received modify request for identifier: {}", identifier);
        Map<String, Object> response = modificationService.modifyReport(identifier, updatedReport);
        
        String status = (String) response.get("status");
        if ("NOT_FOUND".equals(status)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } else if ("CONFLICT".equals(status)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } else if ("ERROR".equals(status)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports/search")
    public ResponseEntity<Map<String, Object>> searchByAssetNameOrSerial(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            Map<String, Object> response = queryService.searchReports(query, page, size, sortBy, sortDir);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            LOGGER.error("Error searching financial reports", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error searching reports: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/reports/filter")
    public ResponseEntity<Map<String, Object>> filterReports(
            @RequestBody Map<String, String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            Map<String, Object> response = queryService.filterReports(filters, page, size, sortBy, sortDir);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            LOGGER.error("Error filtering financial reports", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error filtering reports: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
