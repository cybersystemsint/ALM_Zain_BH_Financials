package com.telkom.co.ke.almoptics.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telkom.co.ke.almoptics.dto.ApprovalRequest;
import com.telkom.co.ke.almoptics.dto.PageResult;
import com.telkom.co.ke.almoptics.entities.WriteOffReport;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.entities.tb_ApprovalWorkflow;
import com.telkom.co.ke.almoptics.models.ApprovalWorkflow;
import com.telkom.co.ke.almoptics.repository.*;
import com.telkom.co.ke.almoptics.services.FinanceReportFetchService;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
import com.telkom.co.ke.almoptics.services.ApprovalWorkflowService;
import com.telkom.co.ke.almoptics.services.InventorySyncService;
import com.telkom.co.ke.almoptics.services.WriteOffReportService;
import com.telkom.co.ke.almoptics.services.AuditLogService;
import com.telkom.co.ke.almoptics.services.UnmappedReportFetchService;
import com.telkom.co.ke.almoptics.services.WriteOffReportFetchService;
import com.telkom.co.ke.almoptics.schedulers.FinancialSyncScheduler;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.springframework.data.jpa.domain.Specification;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.Predicate;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

@RestController
@RequestMapping("/api/financial")
//@CrossOrigin(origins = "*")
@CrossOrigin(origins = {"http://localhost:3000", "*"}, allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class FinancialReportController {

    private static final Logger logger = LoggerFactory.getLogger(FinancialReportController.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final int BATCH_SIZE = 100;
    @Autowired
    private FinancialReportService financialReportService;

    @Autowired
    private ApprovalWorkflowService approvalWorkflowService;

    @Autowired
    private WriteOffReportService writeOffReportService;

    @Autowired
    private UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;

    @Autowired
    private UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;

    @Autowired
    private UnmappedITInventoryRepository unmappedITInventoryRepository;

    @Autowired
    private UnmappedLicenseRepository unmappedLicenseRepository;

    @Autowired
    private ApprovalWorkflowRepository approvalWorkflowRepository;

    @Autowired
    private FinancialReportRepo financialReportRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventorySyncService inventorySyncService;

    @Autowired
    private FinanceReportFetchService financeReportFetchService;

    @Autowired
    private UnmappedReportFetchService unmappedReportFetchService;

    @Autowired
    private WriteOffReportFetchService writeOffReportFetchService;

    @Autowired(required = false)
    private FinancialSyncScheduler financialSyncScheduler;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    /**
     * The new bulk sync orchestrator. Used to auto-trigger the
     * inventory↔FR sync (with set-based unmapped-add / unmapped-prune /
     * decommission stages) right after a JSON FR upload succeeds.
     */
    @Autowired(required = false)
    private com.telkom.co.ke.almoptics.services.SyncOrchestratorService syncOrchestratorService;

    @PersistenceContext
    private EntityManager entityManager;



    private static final String[] BULK_TEMPLATE_HEADERS = new String[] {
            "AssetName", "AssetSerialNumber", "TAG", "OracleAssetID", "AssetType",
            "NodeType", "InstallationDate", "InitialCost", "SalvageValue",
            "PONumber", "PODate", "FA_Category", "L1", "L2", "L3", "L4",
            "AccumulatedDepreciationCode", "DepreciationCode", "UsefulLife_Months",
            "VendorName", "VendorNumber", "ProjectNumber", "DateOfService",
            "OldFA_Category", "CostCenter", "Adjustment", "TaskID",
            "POLineNumber", "ItemBarCode", "RFID", "InvoiceNumber",
            "Description"
    };

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String asAtDate) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            if (asAtDate != null && !asAtDate.trim().isEmpty()) {
                // Parse asAtDate to LocalDate and format to "d MMM yyyy"
                LocalDate inputDate = LocalDate.parse(asAtDate);
                String formattedDate = inputDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"));

                // Use calculateDepreciationForMonth for as-of-date reports
                Map<String, Object> depResponse = financialReportService.calculateDepreciationForMonth(formattedDate, search, pageable);

                // Override totalNBV to ensure consistency: totalCost - totalDepreciation
                BigDecimal totalCost = (BigDecimal) depResponse.get("totalCost");
                BigDecimal totalDepreciation = (BigDecimal) depResponse.get("totalDepreciation");
                BigDecimal computedTotalNBV = totalCost.subtract(totalDepreciation).setScale(3, RoundingMode.HALF_UP);
                depResponse.put("totalNBV", computedTotalNBV);

                // Adjust keys to match expected response (e.g., "reports" instead of "records")
                Map<String, Object> response = new HashMap<>(depResponse);
                response.put("reports", depResponse.get("records"));
                response.remove("records"); // Remove original key if needed

                // Add any missing keys or adjustments
                response.put("sort", sortBy + "," + sortDir);

                return new ResponseEntity<>(response, HttpStatus.OK);
            } else {
                // Fallback to current (no asAtDate)
                Page<tb_FinancialReport> reportPage = search != null && !search.trim().isEmpty() ?
                        financialReportService.findBySearchTerm(search, pageable) :
                        financialReportService.findAll(pageable);

                String lastMonthDate = null; // No date filter for current

                Map<String, Object> response = new HashMap<>();
                response.put("reports", reportPage.getContent());
                response.put("currentPage", reportPage.getNumber());
                response.put("totalItems", reportPage.getTotalElements());
                response.put("totalPages", reportPage.getTotalPages());
                response.put("first", reportPage.isFirst());
                response.put("last", reportPage.isLast());
                response.put("size", reportPage.getSize());
                response.put("sort", sortBy + "," + sortDir);

                // Calculate filtered values based on the paginated subset
                List<tb_FinancialReport> reports = reportPage.getContent();
                BigDecimal filteredCost = reports.stream()
                        .map(tb_FinancialReport::getInitialCost)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(3, RoundingMode.HALF_UP);
                BigDecimal filteredDepreciation = reports.stream()
                        .map(tb_FinancialReport::getAccumulatedDepreciation)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(3, RoundingMode.HALF_UP);
                BigDecimal filteredNBV = filteredCost.subtract(filteredDepreciation)
                        .setScale(3, RoundingMode.HALF_UP);

                // Use filtered aggregates for total values (to match search)
                BigDecimal totalCost = financialReportService.getFilteredCost(search, lastMonthDate) != null ?
                        financialReportService.getFilteredCost(search, lastMonthDate).setScale(3, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
                BigDecimal totalDepreciation = financialReportService.getFilteredDepreciation(search, lastMonthDate) != null ?
                        financialReportService.getFilteredDepreciation(search, lastMonthDate).setScale(3, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);

                // Override totalNBV to ensure consistency: totalCost - totalDepreciation
                BigDecimal computedTotalNBV = totalCost.subtract(totalDepreciation).setScale(3, RoundingMode.HALF_UP);

                // Use the computed NBV instead of querying it
                response.put("totalCost", totalCost);
                response.put("totalNBV", computedTotalNBV);
                response.put("totalDepreciation", totalDepreciation);
                response.put("filteredCost", filteredCost);
                response.put("filteredNBV", filteredNBV);
                response.put("filteredDepreciation", filteredDepreciation);

                return new ResponseEntity<>(response, HttpStatus.OK);
            }
        } catch (Exception e) {
            logger.error("Error retrieving financial reports", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error retrieving reports: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String getLastMonthDate(String asAtDate) {
        if (asAtDate == null || asAtDate.isEmpty()) return null;
        LocalDate inputDate = LocalDate.parse(asAtDate);
        return YearMonth.from(inputDate).minusMonths(1).atEndOfMonth().toString();
    }


    @DeleteMapping("/reports/{serialNumber}")
    public ResponseEntity<Map<String, Object>> deleteReport(
            @PathVariable String serialNumber,
            Principal principal) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<tb_FinancialReport> existingReportOpt = financialReportService.findBySerialNumber(serialNumber);
            if (!existingReportOpt.isPresent()) {
                response.put("message", "Financial report with serial number " + serialNumber + " not found");
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }

            tb_FinancialReport existingReport = existingReportOpt.get();
            String username = principal != null ? principal.getName() : "system";
            existingReport.setInitialCost(BigDecimal.ZERO);
            existingReport.setChangedBy(username);
            existingReport.setChangeDate(new Date());

//            tb_ApprovalWorkflow workflow = approvalWorkflowService.createDeletionWorkflow(existingReport, existingReport.getNodeType(), "pending deletion");
            tb_ApprovalWorkflow workflow = approvalWorkflowService.createDeletionWorkflow(existingReport, existingReport.getNodeType(), "pending deletion", username);
            financialReportService.save(existingReport);

            response.put("message", "Financial report deletion submitted for approval");
            response.put("workflowId", workflow.getID());
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error deleting financial report with serial number: " + serialNumber, e);
            response.put("message", "Error deleting financial report: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @Transactional
    @PostMapping("/updatechanges")
    public ResponseEntity<Map<String, Object>> updateChanges(
            @RequestBody List<tb_FinancialReport> reports,
            Principal principal) {
        Map<String, Object> response = new HashMap<>();
        if (reports == null || reports.isEmpty()) {
            response.put("message", "No data provided for update!");
            response.put("status", "error");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        try {
            String username = principal != null ? principal.getName() : "system";
            Set<String> identifiersInRequest = new HashSet<>();
            List<Integer> workflowIds = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            int recordsProcessed = 0;
            int recordsUpdated = 0;
            int recordsSkipped = 0;

            // Validate all records upfront
            for (int i = 0; i < reports.size(); i++) {
                tb_FinancialReport report = reports.get(i);
                int recordNumber = i + 1;
                String identifier = report.getAssetSerialNumber() != null && !report.getAssetSerialNumber().isEmpty() ?
                        report.getAssetSerialNumber() : report.getAssetName();

                if (identifier == null || identifier.trim().isEmpty()) {
                    response.put("message", "Record " + recordNumber + ": Invalid or missing identifier");
                    response.put("status", "error");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                if (!identifiersInRequest.add(identifier)) {
                    response.put("message", "Record " + recordNumber + ": Duplicate identifier in update batch: " + identifier);
                    response.put("status", "error");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                // Validate mandatory fields: Asset Name and Oracle Asset ID
                if (report.getAssetName() == null || report.getAssetName().trim().isEmpty()) {
                    response.put("message", "Record " + recordNumber + ": Missing or invalid mandatory field: AssetName");
                    response.put("status", "error");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
                if (report.getOracleAssetId() == null || report.getOracleAssetId().trim().isEmpty()) {
                    response.put("message", "Record " + recordNumber + ": Missing or invalid mandatory field: OracleAssetId");
                    response.put("status", "error");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
            }

            // Process records
            for (tb_FinancialReport report : reports) {
                recordsProcessed++;
                String identifier = report.getAssetSerialNumber() != null && !report.getAssetSerialNumber().isEmpty() ?
                        report.getAssetSerialNumber() : report.getAssetName();
                logger.info("Processing record {} with identifier: {}", recordsProcessed, identifier);

                // Check for pending workflows
                List<tb_ApprovalWorkflow> existingWorkflows = null;
                try {
                    existingWorkflows = approvalWorkflowService.findByAssetId(identifier);
                } catch (Exception e) {
                    logger.error("Error querying workflows for identifier: {}", identifier, e);
                    warnings.add("Record " + recordsProcessed + ": Unable to check workflow status for identifier " + identifier + " due to server error");
                    recordsSkipped++;
                    continue;
                }

                boolean hasPendingWorkflow = existingWorkflows != null && existingWorkflows.stream()
                        .anyMatch(w -> w.getUPDATED_STATUS() != null && w.getUPDATED_STATUS().toLowerCase().startsWith("pending"));

                if (hasPendingWorkflow) {
                    logger.info("Skipping record {} for identifier {} due to pending workflow", recordsProcessed, identifier);
                    warnings.add("Record " + recordsProcessed + ": Identifier " + identifier +
                            " has an existing workflow pending approval process and will be skipped.");
                    recordsSkipped++;
                    continue;
                }

                // Check if record exists in Financial Report
                Optional<tb_FinancialReport> existingReportOpt;
                try {
                    existingReportOpt = financialReportService.findBySerialNumber(identifier)
                            .or(() -> financialReportService.findByAssetName(identifier));
                } catch (Exception e) {
                    logger.error("Error querying financial report for identifier: {}", identifier, e);
                    warnings.add("Record " + recordsProcessed + ": Unable to check financial report for identifier " + identifier + " due to server error");
                    recordsSkipped++;
                    continue;
                }

                if (!existingReportOpt.isPresent()) {
                    warnings.add("Record " + recordsProcessed + ": Identifier " + identifier +
                            " not found in financial reports. Update cannot be processed.");
                    recordsSkipped++;
                    continue;
                }

                tb_FinancialReport existingReport = existingReportOpt.get();

                // Check if no changes
                if (!hasChanges(existingReport, report)) {
                    warnings.add("Record " + recordsProcessed + ": Asset " + identifier +
                            " exists in Financial Report with no changes detected in AssetName or OracleAssetId.");
                    recordsSkipped++;
                    continue;
                }

                // Serialize original state
                try {
                    Map<String, Object> originalState = new HashMap<>();
                    originalState.put("assetName", existingReport.getAssetName());
                    originalState.put("OracleAssetId", existingReport.getOracleAssetId());
                    existingReport.setOriginalState(objectMapper.writeValueAsString(originalState));
                } catch (Exception e) {
                    logger.error("Failed to serialize original state for identifier: {}", identifier, e);
                    warnings.add("Record " + recordsProcessed + ": Failed to serialize original state for identifier: " + identifier);
                    recordsSkipped++;
                    continue;
                }

                // Update only AssetName and OracleAssetId
                existingReport.setAssetName(report.getAssetName());
                existingReport.setOracleAssetId(report.getOracleAssetId());
                existingReport.setFinancialApprovalStatus("Pending L1 Approval");
                existingReport.setChangedBy(username);
                existingReport.setChangeDate(new Date());

                tb_FinancialReport savedReport;
                tb_ApprovalWorkflow workflow;
                try {
                    savedReport = financialReportService.save(existingReport);
                    workflow = approvalWorkflowService.createApprovalWorkflow(savedReport, savedReport.getNodeType(), "pending modification", username);
                } catch (Exception e) {
                    logger.error("Error processing update for identifier: {}", identifier, e);
                    warnings.add("Record " + recordsProcessed + ": Failed to process update for identifier " + identifier);
                    recordsSkipped++;
                    continue;
                }

                if (savedReport == null || workflow == null || workflow.getID() == null) {
                    logger.error("Failed to process record {} for identifier: {}", recordsProcessed, identifier);
                    warnings.add("Record " + recordsProcessed + ": Failed to create workflow for identifier " + identifier);
                    recordsSkipped++;
                    continue;
                }

                workflowIds.add(workflow.getID());
                recordsUpdated++;
            }

            response.put("recordsProcessed", recordsProcessed);
            response.put("recordsUpdated", recordsUpdated);
            response.put("recordsSkipped", recordsSkipped);
            response.put("workflowIds", workflowIds);
            response.put("message", recordsUpdated > 0 ? "Successfully updated financial reports" : "No records updated");
            response.put("status", recordsUpdated > 0 ? "success" : "success_with_warnings");

            if (!warnings.isEmpty()) {
                response.put("warnings", warnings);
                response.put("status", "success_with_warnings");
            }

            logger.info("Update completed: processed={}, updated={}, skipped={}",
                    recordsProcessed, recordsUpdated, recordsSkipped);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Unexpected error processing updated financial reports", e);
            response.put("message", "Update failed due to an unexpected error: " + e.getMessage());
            response.put("status", "error");
            response.put("errorDetails", e.getClass().getName() + ": " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFinancialReports(
            @RequestBody List<tb_FinancialReport> reports,
            Principal principal) {
        Map<String, Object> response = new HashMap<>();
        if (reports == null || reports.isEmpty()) {
            response.put("message", "No data provided for upload!");
            response.put("status", "error");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        try {
            String username = principal != null ? principal.getName() : "system";
            Set<String> identifiersInRequest = new HashSet<>();
            List<Integer> workflowIds = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            int recordsProcessed = 0;
            int recordsCreated = 0;
            int recordsUpdated = 0;
            int recordsSkipped = 0;

            // Validate all records upfront
            for (int i = 0; i < reports.size(); i++) {
                tb_FinancialReport report = reports.get(i);
                int recordNumber = i + 1;
                String identifier = report.getAssetSerialNumber() != null && !report.getAssetSerialNumber().isEmpty() ?
                        report.getAssetSerialNumber() : report.getAssetName();

                if (identifier == null || identifier.trim().isEmpty()) {
                    response.put("message", "Record " + recordNumber + ": Invalid or missing identifier");
                    response.put("status", "error");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                if (!identifiersInRequest.add(identifier)) {
                    response.put("message", "Record " + recordNumber + ": Duplicate identifier in upload batch: " + identifier);
                    response.put("status", "error");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                // Validate mandatory fields based on their types
                Map<String, Object> mandatoryFields = new HashMap<>();
                mandatoryFields.put("NodeType", report.getNodeType());
                mandatoryFields.put("AssetName", report.getAssetName());
                mandatoryFields.put("AssetType", report.getAssetType());
                mandatoryFields.put("InstallationDate", report.getInstallationDate());
                mandatoryFields.put("InitialCost", report.getInitialCost());
                mandatoryFields.put("SalvageValue", report.getSalvageValue());
                mandatoryFields.put("PONumber", report.getPoNumber());
                mandatoryFields.put("PODate", report.getPoDate());
                mandatoryFields.put("L1", report.getL1());
                mandatoryFields.put("L2", report.getL2());
                mandatoryFields.put("L3", report.getL3());
                mandatoryFields.put("L4", report.getL4());
                mandatoryFields.put("AccumulatedDepreciationCode", report.getAccumulatedDepreciationCode());
                mandatoryFields.put("DepreciationCode", report.getDepreciationCode());
                mandatoryFields.put("UsefulLifeMonths", report.getUsefulLifeMonths());
                mandatoryFields.put("VendorName", report.getVendorName());
                mandatoryFields.put("VendorNumber", report.getVendorNumber());
                mandatoryFields.put("ProjectNumber", report.getProjectNumber());
                mandatoryFields.put("DateOfService", report.getDateOfService());
                mandatoryFields.put("PoLineNumber", report.getPoLineNumber());

                for (Map.Entry<String, Object> entry : mandatoryFields.entrySet()) {
                    String fieldName = entry.getKey();
                    Object fieldValue = entry.getValue();

                    // String field validation
                    if (fieldValue instanceof String) {
                        String value = (String) fieldValue;
                        if (value == null || value.trim().isEmpty()) {
//                            response.put("message", "Record " + recordNumber + ": Missing or invalid mandatory field: " + fieldName);
                            response.put("message", "Record " + recordNumber + ": " + fieldName + " cannot be empty");
                            response.put("status", "error");
                            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                        }
                    }
                    // Integer field validation
                    else if (fieldName.equals("UsefulLifeMonths") && (fieldValue == null || (Integer) fieldValue <= 0)) {
//                        response.put("message", "Record " + recordNumber + ": Missing or invalid mandatory field: " + fieldName + " (must be a positive integer)");
                        response.put("message", "Record " + recordNumber + ": " + fieldName + " cannot be empty or zero, must be a positive integer");
                        response.put("status", "error");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }
                    // BigDecimal field validation
                    else if (fieldValue instanceof BigDecimal) {
                        BigDecimal value = (BigDecimal) fieldValue;
                        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
//                            response.put("message", "Record " + recordNumber + ": Missing or invalid mandatory field: " + fieldName + " (must be non-negative)");
                            response.put("message", "Record " + recordNumber + ": " + fieldName + " cannot be empty, must be a non-negative value");
                            response.put("status", "error");
                            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                        }
                    }
                    // Handle any other unexpected null fields
                    else if (fieldValue == null) {
//                        response.put("message", "Record " + recordNumber + ": Missing or invalid mandatory field: " + fieldName);
                        response.put("message", "Record " + recordNumber + ": " + fieldName + " cannot be empty");
                        response.put("status", "error");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }
                }

                // Populate fields from inventory (optional, does not fail upload)
                try {
                    report = inventorySyncService.populateFieldsFromInventory(report);
                } catch (Exception e) {
                    logger.warn("Failed to sync inventory for identifier {}: {}", identifier, e.getMessage());
                    warnings.add("Record " + recordNumber + ": Failed to sync inventory data for identifier " + identifier + ": " + e.getMessage());
                }
            }

            // Process records
            for (tb_FinancialReport report : reports) {
                recordsProcessed++;
                String identifier = report.getAssetSerialNumber() != null && !report.getAssetSerialNumber().isEmpty() ?
                        report.getAssetSerialNumber() : report.getAssetName();
                logger.info("Processing record {} with identifier: {}", recordsProcessed, identifier);

                // Check for pending workflows
                List<tb_ApprovalWorkflow> existingWorkflows = null;
                try {
                    existingWorkflows = approvalWorkflowService.findByAssetId(identifier);
                } catch (Exception e) {
                    logger.error("Error querying workflows for identifier: {}", identifier, e);
                    warnings.add("Record " + recordsProcessed + ": Unable to check workflow status for identifier " + identifier + " due to server error");
                    recordsSkipped++;
                    continue;
                }

                boolean hasPendingWorkflow = existingWorkflows != null && existingWorkflows.stream()
                        .anyMatch(w -> w.getUPDATED_STATUS() != null && w.getUPDATED_STATUS().toLowerCase().startsWith("pending"));

                if (hasPendingWorkflow) {
                    logger.info("Skipping record {} for identifier {} due to pending workflow", recordsProcessed, identifier);
                    warnings.add("Record " + recordsProcessed + ": Identifier " + identifier +
                            " has an existing workflow pending approval process and will be skipped.");
                    recordsSkipped++;
                    continue;
                }

                // Check if record exists in Financial Report
                Optional<tb_FinancialReport> existingReportOpt;
                try {
                    existingReportOpt = financialReportService.findBySerialNumber(identifier)
                            .or(() -> financialReportService.findByAssetName(identifier));
                } catch (Exception e) {
                    logger.error("Error querying financial report for identifier: {}", identifier, e);
                    warnings.add("Record " + recordsProcessed + ": Unable to check financial report for identifier " + identifier + " due to server error");
                    recordsSkipped++;
                    continue;
                }

                // For existing records, check if assetName exists in NELicense
                if (existingReportOpt.isPresent()) {
                    tb_FinancialReport existingReport = existingReportOpt.get();
                    try {
                        boolean inLicense = unmappedLicenseRepository.findByElementID(existingReport.getAssetName()).isPresent();
                        if (!inLicense) {
                            logger.info("Skipping record {} for identifier {}: assetName {} not found in NELicense",
                                    recordsProcessed, identifier, existingReport.getAssetName());
                            warnings.add("Record " + recordsProcessed + ": AssetName " + existingReport.getAssetName() +
                                    " not found in NELicense. Record cannot be modified.");
                            recordsSkipped++;
                            continue;
                        }
                    } catch (Exception e) {
                        logger.error("Error querying NELicense for assetName: {}", existingReport.getAssetName(), e);
                        warnings.add("Record " + recordsProcessed + ": Unable to check NELicense for assetName " +
                                existingReport.getAssetName() + " due to server error");
                        recordsSkipped++;
                        continue;
                    }
                }

                // Check unmapped inventory only for new records
                boolean inUnmapped = true;
                if (!existingReportOpt.isPresent()) {
                    try {
                        inUnmapped = unmappedActiveInventoryRepository.findBySerialNumber(identifier).isPresent() ||
                                unmappedPassiveInventoryRepository.findBySerial(identifier).isPresent() ||
                                unmappedPassiveInventoryRepository.findByObjectId(identifier).isPresent() ||
                                unmappedPassiveInventoryRepository.findByElementType(identifier).isPresent() ||
                                unmappedITInventoryRepository.findByHostSerialNumber(identifier).isPresent() ||
                                unmappedITInventoryRepository.findByHardwareSerialNumber(identifier).isPresent() ||
                                unmappedITInventoryRepository.findByElementId(identifier).isPresent() ||
                                unmappedITInventoryRepository.findByHostName(identifier).isPresent() ||
                                unmappedLicenseRepository.findByElementID(identifier).isPresent();
                    } catch (Exception e) {
                        logger.error("Error querying unmapped inventory for identifier: {}", identifier, e);
                        warnings.add("Record " + recordsProcessed + ": Unable to check unmapped inventory for identifier " + identifier + " due to server error");
                        recordsSkipped++;
                        continue;
                    }
                }

                if (!existingReportOpt.isPresent() && !inUnmapped) {
                    warnings.add("Record " + recordsProcessed + ": Identifier " + identifier +
                            " not found in unmapped inventory. Record cannot be accepted.");
                    recordsSkipped++;
                    continue;
                }

                tb_FinancialReport savedReport = null;
                tb_ApprovalWorkflow workflow = null;

                if (existingReportOpt.isPresent()) {
                    tb_FinancialReport existingReport = existingReportOpt.get();

                    // Check if no changes
                    if (!hasChanges(existingReport, report)) {
                        warnings.add("Record " + recordsProcessed + ": Asset " + identifier +
                                " exists in Financial Report with no changes detected.");
                        recordsSkipped++;
                        continue;
                    }

                    // Check if initial cost is null or zero (deletion)
                    boolean isDeletion = report.getInitialCost() == null ||
                            report.getInitialCost().compareTo(BigDecimal.ZERO) == 0;

                    if (isDeletion) {
                        // Handle deletion
                        existingReport.setInitialCost(BigDecimal.ZERO);
                        existingReport.setChangedBy(username);
                        existingReport.setChangeDate(new Date());
                        try {
                            savedReport = financialReportService.save(existingReport);
                            workflow = approvalWorkflowService.createDeletionWorkflow(savedReport, savedReport.getNodeType(), "pending deletion", username);
                        } catch (Exception e) {
                            logger.error("Error processing deletion for identifier: {}", identifier, e);
                            warnings.add("Record " + recordsProcessed + ": Failed to process deletion for identifier " + identifier);
                            recordsSkipped++;
                            continue;
                        }
                    } else {
                        // Handle modification
                        try {
                            Map<String, Object> originalState = new HashMap<>();
                            originalState.put("siteId", existingReport.getSiteId());
                            originalState.put("zone", existingReport.getZone());
                            originalState.put("nodeType", existingReport.getNodeType());
                            originalState.put("assetName", existingReport.getAssetName());
                            originalState.put("assetType", existingReport.getAssetType());
                            originalState.put("assetCategory", existingReport.getAssetCategory());
                            originalState.put("model", existingReport.getModel());
                            originalState.put("partNumber", existingReport.getPartNumber());
                            originalState.put("assetSerialNumber", existingReport.getAssetSerialNumber());
                            originalState.put("installationDate", existingReport.getInstallationDate());
                            originalState.put("initialCost", existingReport.getInitialCost());
                            originalState.put("monthlyDepreciationAmount", existingReport.getMonthlyDepreciationAmount());
                            originalState.put("accumulatedDepreciation", existingReport.getAccumulatedDepreciation());
                            originalState.put("netCost", existingReport.getNetCost());
                            originalState.put("salvageValue", existingReport.getSalvageValue());
                            originalState.put("poNumber", existingReport.getPoNumber());
                            originalState.put("poDate", existingReport.getPoDate());
                            originalState.put("faCategory", existingReport.getFaCategory());
                            originalState.put("l1", existingReport.getL1());
                            originalState.put("l2", existingReport.getL2());
                            originalState.put("l3", existingReport.getL3());
                            originalState.put("l4", existingReport.getL4());
                            originalState.put("accumulatedDepreciationCode", existingReport.getAccumulatedDepreciationCode());
                            originalState.put("depreciationCode", existingReport.getDepreciationCode());
                            originalState.put("usefulLifeMonths", existingReport.getUsefulLifeMonths());
                            originalState.put("vendorName", existingReport.getVendorName());
                            originalState.put("vendorNumber", existingReport.getVendorNumber());
                            originalState.put("projectNumber", existingReport.getProjectNumber());
                            originalState.put("description", existingReport.getDescription());
                            originalState.put("oracleAssetId", existingReport.getOracleAssetId());
                            originalState.put("dateOfService", existingReport.getDateOfService());
                            originalState.put("technologySupported", existingReport.getTechnologySupported());
                            originalState.put("oldFarCategory", existingReport.getOldFarCategory());
                            originalState.put("costCenterData", existingReport.getCostCenterData());
                            originalState.put("nepAssetId", existingReport.getNepAssetId());
                            originalState.put("deleted", existingReport.getDeleted());
                            originalState.put("adjustment", existingReport.getAdjustment());
                            originalState.put("writeOffDate", existingReport.getWriteOffDate() != null ? existingReport.getWriteOffDate().toString() : null);
                            originalState.put("tag", existingReport.getTag());
                            originalState.put("hostSerialNumber", existingReport.getHostSerialNumber());
                            originalState.put("taskId", existingReport.getTaskId());
                            originalState.put("poLineNumber", existingReport.getPoLineNumber());
                            originalState.put("releaseNumber", existingReport.getReleaseNumber());
                            originalState.put("spectrumLicenseDate", existingReport.getSpectrumLicenseDate() != null ? existingReport.getSpectrumLicenseDate().toString() : null);
                            originalState.put("itemBarCode", existingReport.getItemBarCode());
                            originalState.put("rfid", existingReport.getRfid());
                            originalState.put("invoiceNumber", existingReport.getInvoiceNumber());

                            existingReport.setOriginalState(objectMapper.writeValueAsString(originalState));
                        } catch (Exception e) {
                            logger.error("Failed to serialize original state for identifier: {}", identifier, e);
                            warnings.add("Record " + recordsProcessed + ": Failed to serialize original state for identifier: " + identifier);
                            recordsSkipped++;
                            continue;
                        }

                        // Update fields with non-null values
                        existingReport.setSiteId(report.getSiteId() != null ? report.getSiteId() : existingReport.getSiteId());
                        existingReport.setZone(report.getZone() != null ? report.getZone() : existingReport.getZone());
                        existingReport.setNodeType(report.getNodeType() != null ? report.getNodeType() : existingReport.getNodeType());
                        existingReport.setAssetName(report.getAssetName() != null ? report.getAssetName() : existingReport.getAssetName());
                        existingReport.setAssetType(report.getAssetType() != null ? report.getAssetType() : existingReport.getAssetType());
                        existingReport.setAssetCategory(report.getAssetCategory() != null ? report.getAssetCategory() : existingReport.getAssetCategory());
                        existingReport.setModel(report.getModel() != null ? report.getModel() : existingReport.getModel());
                        existingReport.setPartNumber(report.getPartNumber() != null ? report.getPartNumber() : existingReport.getPartNumber());
                        existingReport.setAssetSerialNumber(report.getAssetSerialNumber() != null ? report.getAssetSerialNumber() : existingReport.getAssetSerialNumber());
                        existingReport.setInstallationDate(report.getInstallationDate() != null ? report.getInstallationDate() : existingReport.getInstallationDate());
                        existingReport.setInitialCost(report.getInitialCost() != null ? report.getInitialCost() : existingReport.getInitialCost());
                        existingReport.setMonthlyDepreciationAmount(report.getMonthlyDepreciationAmount() != null ? report.getMonthlyDepreciationAmount() : existingReport.getMonthlyDepreciationAmount());
                        existingReport.setAccumulatedDepreciation(report.getAccumulatedDepreciation() != null ? report.getAccumulatedDepreciation() : existingReport.getAccumulatedDepreciation());
                        existingReport.setNetCost(report.getNetCost() != null ? report.getNetCost() : existingReport.getNetCost());
                        existingReport.setSalvageValue(report.getSalvageValue() != null ? report.getSalvageValue() : existingReport.getSalvageValue());
                        existingReport.setPoNumber(report.getPoNumber() != null ? report.getPoNumber() : existingReport.getPoNumber());
                        existingReport.setPoDate(report.getPoDate() != null ? report.getPoDate() : existingReport.getPoDate());
                        existingReport.setFaCategory(report.getFaCategory() != null ? report.getFaCategory() : existingReport.getFaCategory());
                        existingReport.setL1(report.getL1() != null ? report.getL1() : existingReport.getL1());
                        existingReport.setL2(report.getL2() != null ? report.getL2() : existingReport.getL2());
                        existingReport.setL3(report.getL3() != null ? report.getL3() : existingReport.getL3());
                        existingReport.setL4(report.getL4() != null ? report.getL4() : existingReport.getL4());
                        existingReport.setAccumulatedDepreciationCode(report.getAccumulatedDepreciationCode() != null ? report.getAccumulatedDepreciationCode() : existingReport.getAccumulatedDepreciationCode());
                        existingReport.setDepreciationCode(report.getDepreciationCode() != null ? report.getDepreciationCode() : existingReport.getDepreciationCode());
                        existingReport.setUsefulLifeMonths(report.getUsefulLifeMonths() != null ? report.getUsefulLifeMonths() : existingReport.getUsefulLifeMonths());
                        existingReport.setVendorName(report.getVendorName() != null ? report.getVendorName() : existingReport.getVendorName());
                        existingReport.setVendorNumber(report.getVendorNumber() != null ? report.getVendorNumber() : existingReport.getVendorNumber());
                        existingReport.setProjectNumber(report.getProjectNumber() != null ? report.getProjectNumber() : existingReport.getProjectNumber());
                        existingReport.setDescription(report.getDescription() != null ? report.getDescription() : existingReport.getDescription());
                        existingReport.setOracleAssetId(report.getOracleAssetId() != null ? report.getOracleAssetId() : existingReport.getOracleAssetId());
                        existingReport.setDateOfService(report.getDateOfService() != null ? report.getDateOfService() : existingReport.getDateOfService());
                        existingReport.setTechnologySupported(report.getTechnologySupported() != null ? report.getTechnologySupported() : existingReport.getTechnologySupported());
                        existingReport.setOldFarCategory(report.getOldFarCategory() != null ? report.getOldFarCategory() : existingReport.getOldFarCategory());
                        existingReport.setCostCenterData(report.getCostCenterData() != null ? report.getCostCenterData() : existingReport.getCostCenterData());
                        existingReport.setNepAssetId(report.getNepAssetId() != null ? report.getNepAssetId() : existingReport.getNepAssetId());
                        existingReport.setDeleted(report.getDeleted() != null ? report.getDeleted() : existingReport.getDeleted());
                        existingReport.setAdjustment(report.getAdjustment() != null ? report.getAdjustment() : existingReport.getAdjustment());
                        existingReport.setWriteOffDate(report.getWriteOffDate() != null ? report.getWriteOffDate() : existingReport.getWriteOffDate());
                        existingReport.setTag(report.getTag() != null ? report.getTag() : existingReport.getTag());
                        existingReport.setHostSerialNumber(report.getHostSerialNumber() != null ? report.getHostSerialNumber() : existingReport.getHostSerialNumber());
                        existingReport.setTaskId(report.getTaskId() != null ? report.getTaskId() : existingReport.getTaskId());
                        existingReport.setPoLineNumber(report.getPoLineNumber() != null ? report.getPoLineNumber() : existingReport.getPoLineNumber());
                        existingReport.setReleaseNumber(report.getReleaseNumber() != null ? report.getReleaseNumber() : existingReport.getReleaseNumber());
                        existingReport.setSpectrumLicenseDate(report.getSpectrumLicenseDate() != null ? report.getSpectrumLicenseDate() : existingReport.getSpectrumLicenseDate());
                        existingReport.setItemBarCode(report.getItemBarCode() != null ? report.getItemBarCode() : existingReport.getItemBarCode());
                        existingReport.setRfid(report.getRfid() != null ? report.getRfid() : existingReport.getRfid());
                        existingReport.setInvoiceNumber(report.getInvoiceNumber() != null ? report.getInvoiceNumber() : existingReport.getInvoiceNumber());

                        existingReport.setFinancialApprovalStatus("Pending L1 Approval");
                        existingReport.setChangedBy(username);
                        existingReport.setChangeDate(new Date());

                        try {
                            savedReport = financialReportService.save(existingReport);
                            workflow = approvalWorkflowService.createApprovalWorkflow(savedReport, savedReport.getNodeType(), "pending modification", username);
                        } catch (Exception e) {
                            logger.error("Error processing modification for identifier: {}", identifier, e);
                            warnings.add("Record " + recordsProcessed + ": Failed to process modification for identifier " + identifier);
                            recordsSkipped++;
                            continue;
                        }
                        recordsUpdated++;
                    }
                } else {
                    // New asset
                    report.setInsertedBy(username);
                    report.setInsertDate(new Date());
                    report.setStatusFlag("NEW");
                    report.setFinancialApprovalStatus("Pending L1 Approval");
                    try {
                        savedReport = financialReportService.save(report);
                        workflow = approvalWorkflowService.createApprovalWorkflow(savedReport, savedReport.getNodeType(), "pending addition", username);
                    } catch (Exception e) {
                        logger.error("Error processing new record for identifier: {}", identifier, e);
                        warnings.add("Record " + recordsProcessed + ": Failed to process new record for identifier " + identifier);
                        recordsSkipped++;
                        continue;
                    }
                    recordsCreated++;
                }

                if (savedReport == null || workflow == null || workflow.getID() == null) {
                    logger.error("Failed to process record {} for identifier: {}", recordsProcessed, identifier);
                    warnings.add("Record " + recordsProcessed + ": Failed to create workflow for identifier " + identifier);
                    recordsSkipped++;
                    continue;
                }

                workflowIds.add(workflow.getID());
            }

            response.put("recordsProcessed", recordsProcessed);
            response.put("recordsCreated", recordsCreated);
            response.put("recordsUpdated", recordsUpdated);
            response.put("recordsSkipped", recordsSkipped);
            response.put("workflowIds", workflowIds);
            response.put("message", (recordsCreated + recordsUpdated) > 0 ? "Successfully uploaded financial reports" : "No records processed");
            response.put("status", (recordsCreated + recordsUpdated) > 0 ? "success" : "success_with_warnings");

            if (!warnings.isEmpty()) {
                response.put("warnings", warnings);
                response.put("status", "success_with_warnings");
            }

            logger.info("Upload completed: processed={}, created={}, updated={}, skipped={}",
                    recordsProcessed, recordsCreated, recordsUpdated, recordsSkipped);

            // Auto-trigger the inventory↔FR sync so the unmapped reports
            // (and any decommission/recover deltas) reflect the new data
            // without the user having to call /trigger-full-sync manually.
            // Runs in the background so the upload response is not held up.
            triggerPostUploadSync(principal, recordsCreated + recordsUpdated);

            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Unexpected error processing uploaded financial reports", e);
            response.put("message", "Upload failed due to an unexpected error: " + e.getMessage());
            response.put("status", "error");
            response.put("errorDetails", e.getClass().getName() + ": " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Helper method to compare fields between existing and incoming reports
    private boolean hasChanges(tb_FinancialReport existing, tb_FinancialReport incoming) {
        return !(Objects.equals(existing.getNodeType(), incoming.getNodeType()) &&
                Objects.equals(existing.getAssetName(), incoming.getAssetName()) &&
                Objects.equals(existing.getAssetSerialNumber(), incoming.getAssetSerialNumber()) &&
                Objects.equals(existing.getAssetType(), incoming.getAssetType()) &&
                Objects.equals(existing.getAssetCategory(), incoming.getAssetCategory()) &&
                Objects.equals(existing.getModel(), incoming.getModel()) &&
                Objects.equals(existing.getPartNumber(), incoming.getPartNumber()) &&
                Objects.equals(existing.getInstallationDate(), incoming.getInstallationDate()) &&
                Objects.equals(existing.getInitialCost(), incoming.getInitialCost()) &&
                Objects.equals(existing.getSalvageValue(), incoming.getSalvageValue()) &&
                Objects.equals(existing.getPoNumber(), incoming.getPoNumber()) &&
                Objects.equals(existing.getPoDate(), incoming.getPoDate()) &&
                Objects.equals(existing.getFaCategory(), incoming.getFaCategory()) &&
                Objects.equals(existing.getL1(), incoming.getL1()) &&
                Objects.equals(existing.getL2(), incoming.getL2()) &&
                Objects.equals(existing.getL3(), incoming.getL3()) &&
                Objects.equals(existing.getL4(), incoming.getL4()) &&
                Objects.equals(existing.getAccumulatedDepreciationCode(), incoming.getAccumulatedDepreciationCode()) &&
                Objects.equals(existing.getDepreciationCode(), incoming.getDepreciationCode()) &&
                Objects.equals(existing.getUsefulLifeMonths(), incoming.getUsefulLifeMonths()) &&
                Objects.equals(existing.getVendorName(), incoming.getVendorName()) &&
                Objects.equals(existing.getVendorNumber(), incoming.getVendorNumber()) &&
                Objects.equals(existing.getProjectNumber(), incoming.getProjectNumber()) &&
                Objects.equals(existing.getDateOfService(), incoming.getDateOfService()) &&
                Objects.equals(existing.getOldFarCategory(), incoming.getOldFarCategory()) &&
                Objects.equals(existing.getCostCenterData(), incoming.getCostCenterData()) &&
                Objects.equals(existing.getAdjustment(), incoming.getAdjustment()) &&
                Objects.equals(existing.getTaskId(), incoming.getTaskId()) &&
                Objects.equals(existing.getPoLineNumber(), incoming.getPoLineNumber()) &&
                Objects.equals(existing.getMonthlyDepreciationAmount(), incoming.getMonthlyDepreciationAmount()) &&
                Objects.equals(existing.getAccumulatedDepreciation(), incoming.getAccumulatedDepreciation()) &&
                Objects.equals(existing.getNetCost(), incoming.getNetCost()) &&
                Objects.equals(existing.getDescription(), incoming.getDescription()) &&
                Objects.equals(existing.getOracleAssetId(), incoming.getOracleAssetId()) &&
                Objects.equals(existing.getTechnologySupported(), incoming.getTechnologySupported()) &&
                Objects.equals(existing.getNepAssetId(), incoming.getNepAssetId()) &&
                Objects.equals(existing.getDeleted(), incoming.getDeleted()) &&
                Objects.equals(existing.getWriteOffDate(), incoming.getWriteOffDate()) &&
                Objects.equals(existing.getTag(), incoming.getTag()) &&
                Objects.equals(existing.getHostSerialNumber(), incoming.getHostSerialNumber()) &&
                Objects.equals(existing.getReleaseNumber(), incoming.getReleaseNumber()) &&
                Objects.equals(existing.getSpectrumLicenseDate(), incoming.getSpectrumLicenseDate()) &&
                Objects.equals(existing.getItemBarCode(), incoming.getItemBarCode()) &&
                Objects.equals(existing.getRfid(), incoming.getRfid()) &&
                Objects.equals(existing.getInvoiceNumber(), incoming.getInvoiceNumber()));
    }




    @PostMapping("/import-writeoff")
    @Transactional
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
            List<Map<String, String>> recordsToProcess = new ArrayList<>();
            List<String> invalidRecords = new ArrayList<>();

            CSVFormat csvFormat = CSVFormat.DEFAULT
                    .withDelimiter(separator)
                    .withFirstRecordAsHeader()
                    .withTrim();
            if (!ignoreHeader) {
                csvFormat = csvFormat.withSkipHeaderRecord(false);
            }

            try (CSVParser parser = new CSVParser(new InputStreamReader(file.getInputStream()), csvFormat)) {
                List<String> headers = parser.getHeaderNames();
                logger.info("CSV headers: {}", headers);
                Map<String, String> columnMapping = new HashMap<>();
                for (String header : headers) {
                    String normHeader = header.toLowerCase().replaceAll("[^a-zA-Z0-9]", "");
                    if (normHeader.contains("serialnumber")) {
                        columnMapping.put("serialNumber", header);
                    } else if (normHeader.contains("assetid")) {
                        columnMapping.put("assetId", header);
                    } else if (normHeader.contains("writeoffdate")) {
                        columnMapping.put("writeOffDate", header);
                    }
                }

                if (!columnMapping.containsKey("serialNumber") || !columnMapping.containsKey("assetId")) {
                    response.put("message", "CSV file must include serialNumber and assetId columns.");
                    response.put("status", "error");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                int recordCount = 0;
                for (CSVRecord record : parser) {
                    recordCount++;
                    String serialNumber = record.get(columnMapping.getOrDefault("serialNumber", "Serial Number"));
                    String assetId = record.get(columnMapping.getOrDefault("assetId", "Asset ID"));
                    String writeOffDateStr = record.get(columnMapping.getOrDefault("writeOffDate", "Write-Off Date"));

                    if (serialNumber == null || serialNumber.trim().isEmpty() ||
                            assetId == null || assetId.trim().isEmpty()) {
                        invalidRecords.add(String.format("Row %d: Missing serialNumber or assetId", recordCount));
                        continue;
                    }

                    Optional<tb_FinancialReport> financialReportOpt = financialReportService
                            .findBySerialNumber(serialNumber)
                            .or(() -> financialReportService.findByAssetName(assetId));
                    if (!financialReportOpt.isPresent()) {
                        invalidRecords.add(String.format("Row %d: No Financial Report found for serialNumber=%s or assetId=%s",
                                recordCount, serialNumber, assetId));
                        continue;
                    }

                    Optional<WriteOffReport> existingWriteOff = writeOffReportService.findBySerialNumber(serialNumber);
                    if (existingWriteOff.isPresent()) {
                        invalidRecords.add(String.format("Row %d: Write-off already exists for serialNumber=%s",
                                recordCount, serialNumber));
                        continue;
                    }

                    if (writeOffDateStr != null && !writeOffDateStr.trim().isEmpty()) {
                        try {
                            DATE_FORMAT.parse(writeOffDateStr);
                        } catch (java.text.ParseException e) {
                            invalidRecords.add(String.format("Row %d: Invalid writeOffDate format: %s (expected yyyy-MM-dd)",
                                    recordCount, writeOffDateStr));
                            continue;
                        }
                    }

                    Map<String, String> recordData = new HashMap<>();
                    recordData.put("serialNumber", serialNumber);
                    recordData.put("assetId", assetId);
                    recordData.put("writeOffDate", writeOffDateStr);
                    recordsToProcess.add(recordData);
                }

                if (!invalidRecords.isEmpty()) {
                    String errorMessage = "Some assets could not be processed. Please verify and try again:\n" +
                            String.join("\n", invalidRecords);
                    response.put("message", errorMessage);
                    response.put("status", "error");
                    response.put("errors", invalidRecords);
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                if (recordsToProcess.isEmpty()) {
                    response.put("message", "No valid records found in the CSV file.");
                    response.put("status", "error");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                List<WriteOffReport> writeOffs = new ArrayList<>();
                List<ApprovalWorkflow> workflows = new ArrayList<>();
                for (Map<String, String> recordData : recordsToProcess) {
                    String serialNumber = recordData.get("serialNumber");
                    String assetId = recordData.get("assetId");
                    String writeOffDateStr = recordData.get("writeOffDate");

                    Optional<tb_FinancialReport> financialReportOpt = financialReportService
                            .findBySerialNumber(serialNumber)
                            .or(() -> financialReportService.findByAssetName(assetId));
                    tb_FinancialReport financialReport = financialReportOpt.get();

                    financialReport.setStatusFlag(financialReport.getStatusFlag());
                    financialReport.setFinancialApprovalStatus("Pending L1 Approval");
                    financialReport.setChangedBy(username);
                    financialReport.setChangeDate(new java.util.Date());
                    financialReportService.save(financialReport);

                    WriteOffReport writeOff = new WriteOffReport();
                    writeOff.setSerialNumber(financialReport.getAssetSerialNumber());
                    writeOff.setAssetId(assetId);
                    writeOff.setAssetType(financialReport.getNodeType());
                    writeOff.setStatusFlag(financialReport.getStatusFlag());
                    if (writeOffDateStr != null && !writeOffDateStr.trim().isEmpty()) {
                        try {
                            writeOff.setWriteOffDate(DATE_FORMAT.parse(writeOffDateStr));
                        } catch (java.text.ParseException e) {
                            logger.error("Failed to parse writeOffDate for serialNumber={}: {}", serialNumber, writeOffDateStr);
                            throw new RuntimeException("Invalid writeOffDate format: " + writeOffDateStr);
                        }
                    } else {
                        writeOff.setWriteOffDate(new java.util.Date());
                    }
                    writeOff.setInsertedBy(username);
                    try {
                        WriteOffReport savedWriteOff = writeOffReportService.save(writeOff);
                        writeOffs.add(savedWriteOff);
                        logger.info("Saved WriteOffReport for serialNumber={}", serialNumber);
                    } catch (Exception e) {
                        logger.error("Failed to save WriteOffReport for serialNumber={}: {}", serialNumber, e.getMessage());
                        throw new RuntimeException("Failed to save write-off report: " + e.getMessage(), e);
                    }

                    ApprovalWorkflow workflow = new ApprovalWorkflow();
                    workflow.setAssetId(String.valueOf(financialReport.getAssetName()));
                    workflow.setObjectType("FinancialReport");
                    workflow.setOriginalStatus(financialReport.getFinancialApprovalStatus());
                    workflow.setUpdatedStatus("Pending L1 Approval");
                    workflow.setComments("Write-off pending L1 approval");
                    workflow.setInsertedBy(username);
                    Integer maxProcessId = approvalWorkflowRepository.findMaxProcessId();
                    workflow.setProcessId(maxProcessId != null ? maxProcessId + 1 : 1);
                    workflows.add(workflow);
                }

                if (!workflows.isEmpty()) {
                    try {
                        approvalWorkflowService.saveAllEntities(workflows);
                        logger.info("Saved {} approval workflows", workflows.size());
                    } catch (Exception e) {
                        logger.error("Failed to save workflows: {}", e.getMessage());
                        throw new RuntimeException("Failed to save approval workflows: " + e.getMessage(), e);
                    }
                }

                response.put("message", String.format(
                        "Successfully imported %d write-off records. They are now pending approval.",
                        writeOffs.size()));
                response.put("recordsImported", writeOffs.size());
                response.put("status", "success");
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
        } catch (Exception e) {
            logger.error("Error importing write-off data from CSV: {}", e.getMessage(), e);
            response.put("message", "Failed to process the CSV file: " + e.getMessage());
            response.put("status", "error");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportFinancialReportsToCsv() {
        try {
            List<tb_FinancialReport> reports = financialReportService.findAll();

            StringWriter writer = new StringWriter();
            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                    .withHeader("Asset Name", "Serial Number", "TAG", "Oracle Asset ID", "Asset Type",
                            "Node Type", "Installation Date", "Initial Cost", "Salvage Value",
                            "PO Number", "PO Date", "FA Category", "L1", "L2", "L3", "L4",
                            "Accumulated Depreciation Code", "Depreciation Code", "Useful Life (Months)",
                            "Vendor Name", "Vendor Number", "Project Number", "Date Of Service",
                            "Old FA Category", "Cost Center", "Adjustment", "Task ID",
                            "PO Line Number", "Monthly Depreciation Amount", "Accumulated Depreciation",
                            "Status Flag", "Net Cost"));

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

            for (tb_FinancialReport report : reports) {
                csvPrinter.printRecord(
                        report.getAssetName(),
                        report.getAssetSerialNumber(),
                        report.getTag(),
                        report.getOracleAssetId(),
                        report.getAssetType(),
                        report.getNodeType(),
                        report.getInstallationDate() != null ? dateFormat.format(report.getInstallationDate()) : "",
                        report.getInitialCost(),
                        report.getSalvageValue(),
                        report.getPoNumber(),
                        report.getPoDate() != null ? dateFormat.format(report.getPoDate()) : "",
                        report.getFaCategory(),
                        report.getL1(),
                        report.getL2(),
                        report.getL3(),
                        report.getL4(),
                        report.getAccumulatedDepreciationCode(),
                        report.getDepreciationCode(),
                        report.getUsefulLifeMonths(),
                        report.getVendorName(),
                        report.getVendorNumber(),
                        report.getProjectNumber(),
                        report.getDateOfService() != null ? dateFormat.format(report.getDateOfService()) : "",
                        report.getOldFarCategory(),
                        report.getCostCenterData(),
                        report.getAdjustment(),
                        report.getTaskId(),
                        report.getPoLineNumber(),
                        report.getMonthlyDepreciationAmount(),
                        report.getAccumulatedDepreciation(),
                        report.getStatusFlag(),
                        report.getNetCost()
                );
            }

            csvPrinter.flush();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=financial_reports.csv");
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);

            return new ResponseEntity<>(writer.toString().getBytes(), headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error exporting financial reports to CSV", e);
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
            if ("json".equalsIgnoreCase(format)) {
                Sort sortObj = Sort.unsorted();
                if (sort != null && !sort.isEmpty()) {
                    String[] sortParts = sort.split(",");
                    String field = sortParts[0];
                    Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("desc") ?
                            Sort.Direction.DESC : Sort.Direction.ASC;
                    sortObj = Sort.by(direction, field);
                }

                Pageable pageable = PageRequest.of(page, size, sortObj);
                Page<WriteOffReport> reportPage = writeOffReportService.findAll(pageable);

                Map<String, Object> response = new HashMap<>();
                response.put("content", reportPage.getContent());
                response.put("totalElements", reportPage.getTotalElements());
                response.put("totalPages", reportPage.getTotalPages());
                response.put("page", page);
                response.put("size", size);

                return new ResponseEntity<>(response, HttpStatus.OK);
            } else {
                List<WriteOffReport> reports = writeOffReportService.findAll();

                StringWriter writer = new StringWriter();
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                        .withHeader("Serial Number", "RFID", "TAG", "Asset Type", "Asset ID",
                                "NE Type", "Write-Off Date", "Status Flag", "Inserted By"));

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

                for (WriteOffReport report : reports) {
                    csvPrinter.printRecord(
                            report.getSerialNumber(),
                            report.getRfid(),
                            report.getTag(),
                            report.getAssetType(),
                            report.getAssetId(),
                            report.getNeType(),
                            report.getWriteOffDate() != null ? dateFormat.format(report.getWriteOffDate()) : "",
                            report.getStatusFlag(),
                            report.getInsertedBy()
                    );
                }

                csvPrinter.flush();

                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=writeoff_reports.csv");
                headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);

                return new ResponseEntity<>(writer.toString().getBytes(), headers, HttpStatus.OK);
            }
        } catch (Exception e) {
            logger.error("Error processing write-off reports", e);
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
            // Parse sort parameter (e.g., "serialNumber,asc")
            Sort sortObj = Sort.unsorted();
            if (sort != null && !sort.isEmpty()) {
                String[] sortParts = sort.split(",");
                String field = sortParts[0];
                Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("desc") ?
                        Sort.Direction.DESC : Sort.Direction.ASC;
                sortObj = Sort.by(direction, field);
            }

            // Create pageable request
            Pageable pageable = PageRequest.of(page, size, sortObj);

            // Search logic
            Page<WriteOffReport> reportPage;
            if (query != null && !query.trim().isEmpty()) {
                reportPage = writeOffReportService.search(query.trim(), pageable);
            } else {
                reportPage = writeOffReportService.findAll(pageable);
            }

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("content", reportPage.getContent());
            response.put("totalElements", reportPage.getTotalElements());
            response.put("totalPages", reportPage.getTotalPages());
            response.put("page", page);
            response.put("size", size);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error searching write-off reports", e);
            return new ResponseEntity<>(Map.of("error", "Failed to search data: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/reports/modify/{identifier}")
    @Transactional
    public ResponseEntity<?> modifyReport(@PathVariable String identifier, @RequestBody tb_FinancialReport updatedReport) {
        logger.info("Received modify request for identifier: {}", identifier);
        Map<String, Object> response = new HashMap<>();

        Optional<tb_FinancialReport> existingReportOpt = financialReportRepo.findByAssetSerialNumber(identifier);
        if (!existingReportOpt.isPresent()) {
            existingReportOpt = financialReportRepo.findByAssetName(identifier);
        }

        if (!existingReportOpt.isPresent()) {
            logger.warn("Financial report not found for identifier: {}", identifier);
            response.put("message", "Financial report not found for identifier: " + identifier);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        tb_FinancialReport existingReport = existingReportOpt.get();

        // Check for existing pending workflows
        String assetId = existingReport.getAssetName() != null && !existingReport.getAssetName().trim().isEmpty() ?
                existingReport.getAssetName() : existingReport.getAssetSerialNumber();
        List<tb_ApprovalWorkflow> existingWorkflows = approvalWorkflowService.findByAssetId(assetId);
        if (existingWorkflows.stream().anyMatch(w -> w.getUPDATED_STATUS().startsWith("Pending"))) {
            logger.warn("Pending workflow exists for asset ID: {}", assetId);
            response.put("message", "Cannot modify: Asset with identifier " + identifier + " has pending workflow approvals");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        // Save original state as JSON
        try {
            Map<String, Object> originalState = new HashMap<>();
            originalState.put("siteId", existingReport.getSiteId());
            originalState.put("zone", existingReport.getZone());
            originalState.put("nodeType", existingReport.getNodeType());
            originalState.put("assetName", existingReport.getAssetName());
            originalState.put("assetType", existingReport.getAssetType());
            originalState.put("assetCategory", existingReport.getAssetCategory());
            originalState.put("model", existingReport.getModel());
            originalState.put("partNumber", existingReport.getPartNumber());
            originalState.put("assetSerialNumber", existingReport.getAssetSerialNumber());
            originalState.put("installationDate", existingReport.getInstallationDate());
            originalState.put("initialCost", existingReport.getInitialCost());
            originalState.put("monthlyDepreciationAmount", existingReport.getMonthlyDepreciationAmount());
            originalState.put("accumulatedDepreciation", existingReport.getAccumulatedDepreciation());
            originalState.put("netCost", existingReport.getNetCost());
            originalState.put("salvageValue", existingReport.getSalvageValue());
            originalState.put("poNumber", existingReport.getPoNumber());
            originalState.put("poDate", existingReport.getPoDate());
            originalState.put("faCategory", existingReport.getFaCategory());
            originalState.put("l1", existingReport.getL1());
            originalState.put("l2", existingReport.getL2());
            originalState.put("l3", existingReport.getL3());
            originalState.put("l4", existingReport.getL4());
            originalState.put("accumulatedDepreciationCode", existingReport.getAccumulatedDepreciationCode());
            originalState.put("depreciationCode", existingReport.getDepreciationCode());
            originalState.put("usefulLifeMonths", existingReport.getUsefulLifeMonths());
            originalState.put("vendorName", existingReport.getVendorName());
            originalState.put("vendorNumber", existingReport.getVendorNumber());
            originalState.put("projectNumber", existingReport.getProjectNumber());
            originalState.put("description", existingReport.getDescription());
            originalState.put("oracleAssetId", existingReport.getOracleAssetId());
            originalState.put("dateOfService", existingReport.getDateOfService());
            originalState.put("technologySupported", existingReport.getTechnologySupported());
            originalState.put("oldFarCategory", existingReport.getOldFarCategory());
            originalState.put("costCenterData", existingReport.getCostCenterData());
            originalState.put("nepAssetId", existingReport.getNepAssetId());
            originalState.put("deleted", existingReport.getDeleted());
            originalState.put("adjustment", existingReport.getAdjustment());
            originalState.put("writeOffDate", existingReport.getWriteOffDate() != null ? existingReport.getWriteOffDate().toString() : null);
            originalState.put("tag", existingReport.getTag());
            originalState.put("hostSerialNumber", existingReport.getHostSerialNumber());
            originalState.put("taskId", existingReport.getTaskId());
            originalState.put("poLineNumber", existingReport.getPoLineNumber());
            originalState.put("releaseNumber", existingReport.getReleaseNumber());
            originalState.put("spectrumLicenseDate", existingReport.getSpectrumLicenseDate() != null ? existingReport.getSpectrumLicenseDate().toString() : null);
            originalState.put("itemBarCode", existingReport.getItemBarCode());
            originalState.put("rfid", existingReport.getRfid());
            originalState.put("invoiceNumber", existingReport.getInvoiceNumber());

            existingReport.setOriginalState(objectMapper.writeValueAsString(originalState));
        } catch (Exception e) {
            logger.error("Failed to serialize original state for report ID: {}", existingReport.getId(), e);
            response.put("message", "Failed to serialize original state: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        // Update report with new values
        existingReport.setSiteId(updatedReport.getSiteId() != null ? updatedReport.getSiteId() : existingReport.getSiteId());
        existingReport.setZone(updatedReport.getZone() != null ? updatedReport.getZone() : existingReport.getZone());
        existingReport.setNodeType(updatedReport.getNodeType() != null ? updatedReport.getNodeType() : existingReport.getNodeType());
        existingReport.setAssetName(updatedReport.getAssetName() != null ? updatedReport.getAssetName() : existingReport.getAssetName());
        existingReport.setAssetType(updatedReport.getAssetType() != null ? updatedReport.getAssetType() : existingReport.getAssetType());
        existingReport.setAssetCategory(updatedReport.getAssetCategory() != null ? updatedReport.getAssetCategory() : existingReport.getAssetCategory());
        existingReport.setModel(updatedReport.getModel() != null ? updatedReport.getModel() : existingReport.getModel());
        existingReport.setPartNumber(updatedReport.getPartNumber() != null ? updatedReport.getPartNumber() : existingReport.getPartNumber());
        existingReport.setAssetSerialNumber(updatedReport.getAssetSerialNumber() != null ? updatedReport.getAssetSerialNumber() : existingReport.getAssetSerialNumber());
        existingReport.setInstallationDate(updatedReport.getInstallationDate() != null ? updatedReport.getInstallationDate() : existingReport.getInstallationDate());
        existingReport.setInitialCost(updatedReport.getInitialCost() != null ? updatedReport.getInitialCost() : existingReport.getInitialCost());
        existingReport.setMonthlyDepreciationAmount(updatedReport.getMonthlyDepreciationAmount() != null ? updatedReport.getMonthlyDepreciationAmount() : existingReport.getMonthlyDepreciationAmount());
        existingReport.setAccumulatedDepreciation(updatedReport.getAccumulatedDepreciation() != null ? updatedReport.getAccumulatedDepreciation() : existingReport.getAccumulatedDepreciation());
        existingReport.setNetCost(updatedReport.getNetCost() != null ? updatedReport.getNetCost() : existingReport.getNetCost());
        existingReport.setSalvageValue(updatedReport.getSalvageValue() != null ? updatedReport.getSalvageValue() : existingReport.getSalvageValue());
        existingReport.setPoNumber(updatedReport.getPoNumber() != null ? updatedReport.getPoNumber() : existingReport.getPoNumber());
        existingReport.setPoDate(updatedReport.getPoDate() != null ? updatedReport.getPoDate() : existingReport.getPoDate());
        existingReport.setFaCategory(updatedReport.getFaCategory() != null ? updatedReport.getFaCategory() : existingReport.getFaCategory());
        existingReport.setL1(updatedReport.getL1() != null ? updatedReport.getL1() : existingReport.getL1());
        existingReport.setL2(updatedReport.getL2() != null ? updatedReport.getL2() : existingReport.getL2());
        existingReport.setL3(updatedReport.getL3() != null ? updatedReport.getL3() : existingReport.getL3());
        existingReport.setL4(updatedReport.getL4() != null ? updatedReport.getL4() : existingReport.getL4());
        existingReport.setAccumulatedDepreciationCode(updatedReport.getAccumulatedDepreciationCode() != null ? updatedReport.getAccumulatedDepreciationCode() : existingReport.getAccumulatedDepreciationCode());
        existingReport.setDepreciationCode(updatedReport.getDepreciationCode() != null ? updatedReport.getDepreciationCode() : existingReport.getDepreciationCode());
        existingReport.setUsefulLifeMonths(updatedReport.getUsefulLifeMonths() != null ? updatedReport.getUsefulLifeMonths() : existingReport.getUsefulLifeMonths());
        existingReport.setVendorName(updatedReport.getVendorName() != null ? updatedReport.getVendorName() : existingReport.getVendorName());
        existingReport.setVendorNumber(updatedReport.getVendorNumber() != null ? updatedReport.getVendorNumber() : existingReport.getVendorNumber());
        existingReport.setProjectNumber(updatedReport.getProjectNumber() != null ? updatedReport.getProjectNumber() : existingReport.getProjectNumber());
        existingReport.setDescription(updatedReport.getDescription() != null ? updatedReport.getDescription() : existingReport.getDescription());
        existingReport.setOracleAssetId(updatedReport.getOracleAssetId() != null ? updatedReport.getOracleAssetId() : existingReport.getOracleAssetId());
        existingReport.setDateOfService(updatedReport.getDateOfService() != null ? updatedReport.getDateOfService() : existingReport.getDateOfService());
        existingReport.setTechnologySupported(updatedReport.getTechnologySupported() != null ? updatedReport.getTechnologySupported() : existingReport.getTechnologySupported());
        existingReport.setOldFarCategory(updatedReport.getOldFarCategory() != null ? updatedReport.getOldFarCategory() : existingReport.getOldFarCategory());
        existingReport.setCostCenterData(updatedReport.getCostCenterData() != null ? updatedReport.getCostCenterData() : existingReport.getCostCenterData());
        existingReport.setNepAssetId(updatedReport.getNepAssetId() != null ? updatedReport.getNepAssetId() : existingReport.getNepAssetId());
        existingReport.setDeleted(updatedReport.getDeleted() != null ? updatedReport.getDeleted() : existingReport.getDeleted());
        existingReport.setAdjustment(updatedReport.getAdjustment() != null ? updatedReport.getAdjustment() : existingReport.getAdjustment());
        existingReport.setWriteOffDate(updatedReport.getWriteOffDate() != null ? updatedReport.getWriteOffDate() : existingReport.getWriteOffDate());
        existingReport.setTag(updatedReport.getTag() != null ? updatedReport.getTag() : existingReport.getTag());
        existingReport.setHostSerialNumber(updatedReport.getHostSerialNumber() != null ? updatedReport.getHostSerialNumber() : existingReport.getHostSerialNumber());
        existingReport.setTaskId(updatedReport.getTaskId() != null ? updatedReport.getTaskId() : existingReport.getTaskId());
        existingReport.setPoLineNumber(updatedReport.getPoLineNumber() != null ? updatedReport.getPoLineNumber() : existingReport.getPoLineNumber());
        existingReport.setReleaseNumber(updatedReport.getReleaseNumber() != null ? updatedReport.getReleaseNumber() : existingReport.getReleaseNumber());
        existingReport.setSpectrumLicenseDate(updatedReport.getSpectrumLicenseDate() != null ? updatedReport.getSpectrumLicenseDate() : existingReport.getSpectrumLicenseDate());
        existingReport.setItemBarCode(updatedReport.getItemBarCode() != null ? updatedReport.getItemBarCode() : existingReport.getItemBarCode());
        existingReport.setRfid(updatedReport.getRfid() != null ? updatedReport.getRfid() : existingReport.getRfid());
        existingReport.setInvoiceNumber(updatedReport.getInvoiceNumber() != null ? updatedReport.getInvoiceNumber() : existingReport.getInvoiceNumber());

        // Set audit fields
        existingReport.setFinancialApprovalStatus("Pending L1 Approval");
        existingReport.setChangedBy(updatedReport.getChangedBy() != null ? updatedReport.getChangedBy() : existingReport.getChangedBy());
        existingReport.setChangeDate(new Date());

        // Save the updated report
        tb_FinancialReport savedReport = financialReportRepo.save(existingReport);

        // Create appropriate workflow
//        tb_ApprovalWorkflow workflow;
//        if (savedReport.getInitialCost() == null || savedReport.getInitialCost().compareTo(BigDecimal.ZERO) == 0) {
//            workflow = approvalWorkflowService.createDeletionWorkflow(savedReport, savedReport.getNodeType(), "pending deletion");
//        } else {
//            workflow = approvalWorkflowService.createApprovalWorkflow(savedReport, savedReport.getNodeType(), "pending modification");
//        }
        String username = updatedReport.getChangedBy() != null ? updatedReport.getChangedBy() : "system";
        existingReport.setFinancialApprovalStatus("Pending L1 Approval");
        existingReport.setChangedBy(username);
        existingReport.setChangeDate(new Date());
        tb_ApprovalWorkflow workflow;
        if (savedReport.getInitialCost() == null || savedReport.getInitialCost().compareTo(BigDecimal.ZERO) == 0) {
            workflow = approvalWorkflowService.createDeletionWorkflow(savedReport, savedReport.getNodeType(), "pending deletion", username);
        } else {
            workflow = approvalWorkflowService.createApprovalWorkflow(savedReport, savedReport.getNodeType(), "pending modification", username);
        }
        logger.info("Financial report {} modified and submitted for approval with workflow ID: {}", savedReport.getId(), workflow.getID());
        response.put("message", "Financial report modification submitted for approval");
        response.put("report", savedReport);
        response.put("workflowId", workflow.getID());
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
            Sort sort = sortDir.equalsIgnoreCase("asc") ?
                    Sort.by(sortBy).ascending() :
                    Sort.by(sortBy).descending();

            Pageable pageable = PageRequest.of(page, size, sort);
            Page<tb_FinancialReport> reportPage = financialReportService.findByAssetNameOrSerialNumber(query, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("reports", reportPage.getContent());
            response.put("currentPage", reportPage.getNumber());
            response.put("totalItems", reportPage.getTotalElements());
            response.put("totalPages", reportPage.getTotalPages());
            response.put("first", reportPage.isFirst());
            response.put("last", reportPage.isLast());
            response.put("size", reportPage.getSize());
            response.put("sort", sortBy + "," + sortDir);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error searching financial reports", e);
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
            Sort sort = sortDir.equalsIgnoreCase("asc") ?
                    Sort.by(sortBy).ascending() :
                    Sort.by(sortBy).descending();

            Pageable pageable = PageRequest.of(page, size, sort);

            // Build Specification for dynamic query
            Specification<tb_FinancialReport> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                // Existing filters
                if (filters.containsKey("l1") && !filters.get("l1").isEmpty()) {
                    predicates.add(cb.equal(root.get("l1"), filters.get("l1")));
                }
                if (filters.containsKey("l2") && !filters.get("l2").isEmpty()) {
                    predicates.add(cb.equal(root.get("l2"), filters.get("l2")));
                }
                if (filters.containsKey("l3") && !filters.get("l3").isEmpty()) {
                    predicates.add(cb.equal(root.get("l3"), filters.get("l3")));
                }
                if (filters.containsKey("l4") && !filters.get("l4").isEmpty()) {
                    predicates.add(cb.equal(root.get("l4"), filters.get("l4")));
                }
                if (filters.containsKey("itemBarCode") && !filters.get("itemBarCode").isEmpty()) {
                    predicates.add(cb.equal(root.get("itemBarCode"), filters.get("itemBarCode")));
                }
                if (filters.containsKey("invoiceNumber") && !filters.get("invoiceNumber").isEmpty()) {
                    predicates.add(cb.equal(root.get("invoiceNumber"), filters.get("invoiceNumber")));
                }
                if (filters.containsKey("assetSerialNumber") && !filters.get("assetSerialNumber").isEmpty()) {
                    predicates.add(cb.equal(root.get("assetSerialNumber"), filters.get("assetSerialNumber")));
                }
                if (filters.containsKey("assetName") && !filters.get("assetName").isEmpty()) {
                    predicates.add(cb.equal(root.get("assetName"), filters.get("assetName")));
                }
                if (filters.containsKey("poNumber") && !filters.get("poNumber").isEmpty()) {
                    predicates.add(cb.equal(root.get("poNumber"), filters.get("poNumber")));
                }
                if (filters.containsKey("OracleAssetID") && !filters.get("OracleAssetID").isEmpty()) {
                    predicates.add(cb.equal(root.get("OracleAssetID"), filters.get("OracleAssetID")));
                }


                // New date range filters
                try {
                    if (filters.containsKey("insertDateStart") && !filters.get("insertDateStart").isEmpty()) {
                        Date startDate = sdf.parse(filters.get("insertDateStart"));
                        predicates.add(cb.greaterThanOrEqualTo(root.get("insertDate"), startDate));
                    }
                    if (filters.containsKey("insertDateEnd") && !filters.get("insertDateEnd").isEmpty()) {
                        Date endDate = sdf.parse(filters.get("insertDateEnd"));
                        predicates.add(cb.lessThanOrEqualTo(root.get("insertDate"), endDate));
                    }
                    if (filters.containsKey("changeDateStart") && !filters.get("changeDateStart").isEmpty()) {
                        Date startDate = sdf.parse(filters.get("changeDateStart"));
                        predicates.add(cb.greaterThanOrEqualTo(root.get("changeDate"), startDate));
                    }
                    if (filters.containsKey("changeDateEnd") && !filters.get("changeDateEnd").isEmpty()) {
                        Date endDate = sdf.parse(filters.get("changeDateEnd"));
                        predicates.add(cb.lessThanOrEqualTo(root.get("changeDate"), endDate));
                    }
                } catch (Exception e) {
                    logger.error("Error parsing date filters", e);
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            };

            Page<tb_FinancialReport> reportPage = financialReportService.findAll(spec, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("reports", reportPage.getContent());
            response.put("currentPage", reportPage.getNumber());
            response.put("totalItems", reportPage.getTotalElements());
            response.put("totalPages", reportPage.getTotalPages());
            response.put("first", reportPage.isFirst());
            response.put("last", reportPage.isLast());
            response.put("size", reportPage.getSize());
            response.put("sort", sortBy + "," + sortDir);

            // QA bug: previously the filter endpoint returned no totals so the Summary
            // tab on the UI showed Total Cost / NBV / Depreciation as 0 whenever any
            // filter was applied. We now aggregate using the SAME Specification so the
            // totals reflect the entire filtered result set, not just the current page.
            List<tb_FinancialReport> allFiltered = financialReportRepo.findAll(spec);
            BigDecimal totalCost = allFiltered.stream()
                    .map(tb_FinancialReport::getInitialCost)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(3, RoundingMode.HALF_UP);
            BigDecimal totalDepreciation = allFiltered.stream()
                    .map(tb_FinancialReport::getAccumulatedDepreciation)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(3, RoundingMode.HALF_UP);
            BigDecimal totalNBV = totalCost.subtract(totalDepreciation).setScale(3, RoundingMode.HALF_UP);

            // Per-page (visible rows) aggregates – kept for the "filtered" sub-row
            // shown next to the totals on the UI.
            List<tb_FinancialReport> pageReports = reportPage.getContent();
            BigDecimal filteredCost = pageReports.stream()
                    .map(tb_FinancialReport::getInitialCost)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(3, RoundingMode.HALF_UP);
            BigDecimal filteredDepreciation = pageReports.stream()
                    .map(tb_FinancialReport::getAccumulatedDepreciation)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(3, RoundingMode.HALF_UP);
            BigDecimal filteredNBV = filteredCost.subtract(filteredDepreciation).setScale(3, RoundingMode.HALF_UP);

            response.put("totalCost", totalCost);
            response.put("totalNBV", totalNBV);
            response.put("totalDepreciation", totalDepreciation);
            response.put("filteredCost", filteredCost);
            response.put("filteredNBV", filteredNBV);
            response.put("filteredDepreciation", filteredDepreciation);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error filtering financial reports", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error filtering reports: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentValue = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(currentValue.toString().trim());
                currentValue = new StringBuilder();
            } else {
                currentValue.append(c);
            }
        }
        values.add(currentValue.toString().trim());
        return values;
    }

    private tb_FinancialReport createReportFromCsv(List<String> headers, List<String> values) throws ParseException {
        tb_FinancialReport report = new tb_FinancialReport();
        for (int i = 0; i < headers.size() && i < values.size(); i++) {
            String header = headers.get(i).trim();
            String value = values.get(i).trim();
            if (value.isEmpty()) continue;
            try {
                switch (header.toLowerCase()) {
                    case "asset name": report.setAssetName(value); break;
                    case "AssetSerialNumber": report.setAssetSerialNumber(value); break;
                    case "tag": report.setTag(value); break;
                    case "asset type": report.setAssetType(value); break;
                    case "node type": report.setNodeType(value); break;
                    case "installation date": report.setInstallationDate(String.valueOf(DATE_FORMAT.parse(value))); break;
                    case "initial cost": report.setInitialCost(new BigDecimal(value)); break;
                    case "salvage value": report.setSalvageValue(new BigDecimal(value)); break;
                    case "po number": report.setPoNumber(value); break;
                    case "po date": report.setPoDate(String.valueOf(DATE_FORMAT.parse(value))); break;
                    case "fa category": report.setFaCategory(value); break;
                    case "l1": report.setL1(value); break;
                    case "l2": report.setL2(value); break;
                    case "l3": report.setL3(value); break;
                    case "l4": report.setL4(value); break;
                    case "accumulated depreciation code": report.setAccumulatedDepreciationCode(value); break;
                    case "depreciation code": report.setDepreciationCode(value); break;
                    case "useful life (months)": report.setUsefulLifeMonths(Integer.parseInt(value)); break;
                    case "vendor name": report.setVendorName(value); break;
                    case "vendor number": report.setVendorNumber(value); break;
                    case "project number": report.setProjectNumber(value); break;
                    case "date of service": report.setDateOfService(String.valueOf(DATE_FORMAT.parse(value))); break;
                    case "old fa category": report.setOldFarCategory(value); break;
                    case "cost center": report.setCostCenterData(value); break;
                    case "adjustment": report.setAdjustment(new BigDecimal(value)); break;
                    case "task id": report.setTaskId(value); break;
                    case "po line number": report.setPoLineNumber(value); break;
                    case "monthly depreciation amount": report.setMonthlyDepreciationAmount(new BigDecimal(value)); break;
                    case "accumulated depreciation": report.setAccumulatedDepreciation(new BigDecimal(value)); break;
                    case "net cost": report.setNetCost(new BigDecimal(value)); break;
                }
            } catch (NumberFormatException | ParseException e) {
                logger.error("Error parsing value '{}' for field '{}': {}", value, header, e.getMessage());
            }
        }
        if (report.getAssetSerialNumber() == null || report.getAssetSerialNumber().isEmpty()) {
            throw new IllegalArgumentException("Serial Number is required");
        }
        return report;
    }

    private tb_FinancialReport cloneReport(tb_FinancialReport original) {
        tb_FinancialReport clone = new tb_FinancialReport();
        clone.setId(original.getId());
        clone.setSiteId(original.getSiteId());
        clone.setZone(original.getZone());
        clone.setNodeType(original.getNodeType());
        clone.setAssetName(original.getAssetName());
        clone.setAssetType(original.getAssetType());
        clone.setAssetCategory(original.getAssetCategory());
        clone.setModel(original.getModel());
        clone.setPartNumber(original.getPartNumber());
        clone.setAssetSerialNumber(original.getAssetSerialNumber());
        clone.setInstallationDate(original.getInstallationDate());
        clone.setInitialCost(original.getInitialCost());
        clone.setSalvageValue(original.getSalvageValue());
        clone.setPoNumber(original.getPoNumber());
        clone.setPoDate(original.getPoDate());
        clone.setFaCategory(original.getFaCategory());
        clone.setL1(original.getL1());
        clone.setL2(original.getL2());
        clone.setL3(original.getL3());
        clone.setL4(original.getL4());
        clone.setAccumulatedDepreciationCode(original.getAccumulatedDepreciationCode());
        clone.setDepreciationCode(original.getDepreciationCode());
        clone.setUsefulLifeMonths(original.getUsefulLifeMonths());
        clone.setVendorName(original.getVendorName());
        clone.setVendorNumber(original.getVendorNumber());
        clone.setProjectNumber(original.getProjectNumber());
        clone.setDateOfService(original.getDateOfService());
        clone.setOldFarCategory(original.getOldFarCategory());
        clone.setCostCenterData(original.getCostCenterData());
        clone.setAdjustment(original.getAdjustment());
        clone.setTaskId(original.getTaskId());
        clone.setPoLineNumber(original.getPoLineNumber());
        clone.setMonthlyDepreciationAmount(original.getMonthlyDepreciationAmount());
        clone.setAccumulatedDepreciation(original.getAccumulatedDepreciation());
        clone.setNetCost(original.getNetCost());
        clone.setStatusFlag(original.getStatusFlag());
        clone.setDescription(original.getDescription());
        clone.setOracleAssetId(original.getOracleAssetId());
        clone.setInsertDate(original.getInsertDate());
        clone.setInsertedBy(original.getInsertedBy());
        clone.setChangeDate(original.getChangeDate());
        clone.setChangedBy(original.getChangedBy());
        clone.setTechnologySupported(original.getTechnologySupported());
        clone.setRetirementDate(original.getRetirementDate());
        clone.setFinancialApprovalStatus(original.getFinancialApprovalStatus());
        clone.setNepAssetId(original.getNepAssetId());
        clone.setDeleted(original.getDeleted());
        clone.setWriteOffDate(original.getWriteOffDate());
        clone.setTag(original.getTag());
        clone.setHostSerialNumber(original.getHostSerialNumber());
        clone.setReleaseNumber(original.getReleaseNumber());
        clone.setSpectrumLicenseDate(original.getSpectrumLicenseDate());
        clone.setItemBarCode(original.getItemBarCode());
        clone.setRfid(original.getRfid());
        clone.setInvoiceNumber(original.getInvoiceNumber());
        return clone;
    }

    @GetMapping("/monthly-report-V2")
    public ResponseEntity<Map<String, Object>> getMonthlyFinancialReportV2(
            @RequestParam String date,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        try {
            Sort sort = Sort.by("id").ascending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Map<String, Object> calculations = financialReportService.calculateDepreciationForMonthV2(date, search, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("filteredCost", calculations.get("filteredCost").toString());
            response.put("filteredDepreciation", calculations.get("filteredDepreciation").toString());
            response.put("filteredMonthlyDepreciation", calculations.get("filteredMonthlyDepreciation").toString());
            response.put("filteredNBV", calculations.get("filteredNBV").toString());
            response.put("totalCost", calculations.get("totalCost").toString());
            response.put("totalDepreciation", calculations.get("totalDepreciation").toString());
            response.put("totalMonthlyDepreciation", calculations.get("totalMonthlyDepreciation").toString());
            response.put("totalNBV", calculations.get("totalNBV").toString());
            response.put("currentPage", calculations.get("currentPage"));
            response.put("totalPages", calculations.get("totalPages"));
            response.put("totalItems", calculations.get("totalItems"));
            response.put("first", calculations.get("first"));
            response.put("last", calculations.get("last"));
            response.put("size", calculations.get("size"));
            response.put("sort", calculations.get("sort"));
            response.put("records", calculations.get("records"));

            logger.info("Monthly financial report generated for {} at {}", date, new SimpleDateFormat("hh:mm a zzz").format(new Date()));
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error generating monthly financial report", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @GetMapping("/monthly-report")
    public ResponseEntity<Map<String, Object>> getMonthlyFinancialReport(
            @RequestParam String date, // e.g., "24 Jul 2025"
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        try {
            Sort sort = Sort.by("id").ascending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Map<String, Object> calculations = financialReportService.calculateDepreciationForMonth(date, search, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("filteredCost", calculations.get("filteredCost").toString());
            response.put("filteredDepreciation", calculations.get("filteredDepreciation").toString());
            response.put("filteredNBV", calculations.get("filteredNBV").toString());
            response.put("totalCost", calculations.get("totalCost").toString());
            response.put("totalDepreciation", calculations.get("totalDepreciation").toString());
            response.put("totalNBV", calculations.get("totalNBV").toString());
            response.put("currentPage", calculations.get("currentPage"));
            response.put("totalPages", calculations.get("totalPages"));
            response.put("totalItems", calculations.get("totalItems"));
            response.put("first", calculations.get("first"));
            response.put("last", calculations.get("last"));
            response.put("size", calculations.get("size"));
            response.put("sort", calculations.get("sort"));
            response.put("records", calculations.get("records"));

            logger.info("Monthly financial report generated for {} at {}", date, new SimpleDateFormat("hh:mm a zzz").format(new Date()));
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error generating monthly financial report", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportFinancialReports(@RequestBody Map<String, Object> body) {
        try {
            String format = (String) body.get("format");
            if (format == null || (!"CSV".equalsIgnoreCase(format) && !"EXCEL".equalsIgnoreCase(format))) {
                return ResponseEntity.badRequest().body(null);
            }
            Map<String, String> filters = (Map<String, String>) body.getOrDefault("filters", new HashMap<>());

            // Build Specification from filters (same as /filter)
            Specification<tb_FinancialReport> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                // Existing filters
                if (filters.containsKey("l1") && !filters.get("l1").isEmpty()) {
                    predicates.add(cb.equal(root.get("l1"), filters.get("l1")));
                }
                if (filters.containsKey("l2") && !filters.get("l2").isEmpty()) {
                    predicates.add(cb.equal(root.get("l2"), filters.get("l2")));
                }
                if (filters.containsKey("l3") && !filters.get("l3").isEmpty()) {
                    predicates.add(cb.equal(root.get("l3"), filters.get("l3")));
                }
                if (filters.containsKey("l4") && !filters.get("l4").isEmpty()) {
                    predicates.add(cb.equal(root.get("l4"), filters.get("l4")));
                }
                if (filters.containsKey("itemBarCode") && !filters.get("itemBarCode").isEmpty()) {
                    predicates.add(cb.equal(root.get("itemBarCode"), filters.get("itemBarCode")));
                }
                if (filters.containsKey("invoiceNumber") && !filters.get("invoiceNumber").isEmpty()) {
                    predicates.add(cb.equal(root.get("invoiceNumber"), filters.get("invoiceNumber")));
                }
                if (filters.containsKey("assetSerialNumber") && !filters.get("assetSerialNumber").isEmpty()) {
                    predicates.add(cb.equal(root.get("assetSerialNumber"), filters.get("assetSerialNumber")));
                }
                if (filters.containsKey("assetName") && !filters.get("assetName").isEmpty()) {
                    predicates.add(cb.equal(root.get("assetName"), filters.get("assetName")));
                }
                if (filters.containsKey("poNumber") && !filters.get("poNumber").isEmpty()) {
                    predicates.add(cb.equal(root.get("poNumber"), filters.get("poNumber")));
                }

                // Date range filters
                try {
                    if (filters.containsKey("insertDateStart") && !filters.get("insertDateStart").isEmpty()) {
                        Date startDate = sdf.parse(filters.get("insertDateStart"));
                        predicates.add(cb.greaterThanOrEqualTo(root.get("insertDate"), startDate));
                    }
                    if (filters.containsKey("insertDateEnd") && !filters.get("insertDateEnd").isEmpty()) {
                        Date endDate = sdf.parse(filters.get("insertDateEnd"));
                        predicates.add(cb.lessThanOrEqualTo(root.get("insertDate"), endDate));
                    }
                    if (filters.containsKey("changeDateStart") && !filters.get("changeDateStart").isEmpty()) {
                        Date startDate = sdf.parse(filters.get("changeDateStart"));
                        predicates.add(cb.greaterThanOrEqualTo(root.get("changeDate"), startDate));
                    }
                    if (filters.containsKey("changeDateEnd") && !filters.get("changeDateEnd").isEmpty()) {
                        Date endDate = sdf.parse(filters.get("changeDateEnd"));
                        predicates.add(cb.lessThanOrEqualTo(root.get("changeDate"), endDate));
                    }
                } catch (Exception e) {
                    logger.error("Error parsing date filters", e);
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            };

            List<tb_FinancialReport> reports = financialReportRepo.findAll(spec);

            String filename;
            byte[] content;
            String contentType;

            if ("CSV".equalsIgnoreCase(format)) {
                filename = "financial_reports.csv";
                contentType = "text/csv";
                content = generateCsv(reports);
            } else { // EXCEL
                filename = "financial_reports.xlsx";
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                content = generateExcel(reports);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            headers.set(HttpHeaders.CONTENT_TYPE, contentType);

            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error exporting financial reports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private byte[] generateCsv(List<tb_FinancialReport> reports) throws Exception {
        StringWriter writer = new StringWriter();
        CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                .withHeader("Asset Name", "Serial Number", "TAG", "Oracle Asset ID", "Asset Type",
                        "Node Type", "Installation Date", "Initial Cost", "Salvage Value",
                        "PO Number", "PO Date", "FA Category", "L1", "L2", "L3", "L4",
                        "Accumulated Depreciation Code", "Depreciation Code", "Useful Life (Months)",
                        "Vendor Name", "Vendor Number", "Project Number", "Date Of Service",
                        "Old FA Category", "Cost Center", "Adjustment", "Task ID",
                        "PO Line Number", "Monthly Depreciation Amount", "Accumulated Depreciation",
                        "Status Flag", "Net Cost"));

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        for (tb_FinancialReport report : reports) {
            // Removed dateFormat.format() and just use the value (cast to String if needed)
            csvPrinter.printRecord(
                    report.getAssetName(),
                    report.getAssetSerialNumber(),
                    report.getTag(),
                    report.getOracleAssetId(),
                    report.getAssetType(),
                    report.getNodeType(),
                    report.getInstallationDate() != null ? report.getInstallationDate() : "",  // Changed
                    report.getInitialCost(),
                    report.getSalvageValue(),
                    report.getPoNumber(),
                    report.getPoDate() != null ? report.getPoDate() : "",  // Changed
                    report.getFaCategory(),
                    report.getL1(),
                    report.getL2(),
                    report.getL3(),
                    report.getL4(),
                    report.getAccumulatedDepreciationCode(),
                    report.getDepreciationCode(),
                    report.getUsefulLifeMonths(),
                    report.getVendorName(),
                    report.getVendorNumber(),
                    report.getProjectNumber(),
                    report.getDateOfService() != null ? report.getDateOfService() : "",  // Changed
                    report.getOldFarCategory(),
                    report.getCostCenterData(),
                    report.getAdjustment(),
                    report.getTaskId(),
                    report.getPoLineNumber(),
                    report.getMonthlyDepreciationAmount(),
                    report.getAccumulatedDepreciation(),
                    report.getStatusFlag(),
                    report.getNetCost()
            );
        }

        csvPrinter.flush();
        return writer.toString().getBytes();
    }

    private byte[] generateExcel(List<tb_FinancialReport> reports) throws Exception {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
            int rowNum = 0;
            int sheetNum = 0;
            Sheet sheet = workbook.createSheet("Sheet " + (++sheetNum));
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Asset Name", "Serial Number", "TAG", "Oracle Asset ID", "Asset Type",
                    "Node Type", "Installation Date", "Initial Cost", "Salvage Value",
                    "PO Number", "PO Date", "FA Category", "L1", "L2", "L3", "L4",
                    "Accumulated Depreciation Code", "Depreciation Code", "Useful Life (Months)",
                    "Vendor Name", "Vendor Number", "Project Number", "Date Of Service",
                    "Old FA Category", "Cost Center", "Adjustment", "Task ID",
                    "PO Line Number", "Monthly Depreciation Amount", "Accumulated Depreciation",
                    "Status Flag", "Net Cost"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

            for (tb_FinancialReport report : reports) {
                if (rowNum >= 1000000) { // Excel sheet limit ~1M rows
                    sheet = workbook.createSheet("Sheet " + (++sheetNum));
                    rowNum = 0;
                    headerRow = sheet.createRow(rowNum++);
                    for (int i = 0; i < headers.length; i++) {
                        headerRow.createCell(i).setCellValue(headers[i]);
                    }
                }

                Row dataRow = sheet.createRow(rowNum++);
                int cellNum = 0;
                dataRow.createCell(cellNum++).setCellValue(report.getAssetName());
                dataRow.createCell(cellNum++).setCellValue(report.getAssetSerialNumber());
                dataRow.createCell(cellNum++).setCellValue(report.getTag());
                dataRow.createCell(cellNum++).setCellValue(report.getOracleAssetId());
                dataRow.createCell(cellNum++).setCellValue(report.getAssetType());
                dataRow.createCell(cellNum++).setCellValue(report.getNodeType());
                dataRow.createCell(cellNum++).setCellValue(report.getInstallationDate() != null ? report.getInstallationDate() : "");  // Changed
                dataRow.createCell(cellNum++).setCellValue(report.getInitialCost() != null ? report.getInitialCost().doubleValue() : 0);
                dataRow.createCell(cellNum++).setCellValue(report.getSalvageValue() != null ? report.getSalvageValue().doubleValue() : 0);
                dataRow.createCell(cellNum++).setCellValue(report.getPoNumber());
                dataRow.createCell(cellNum++).setCellValue(report.getPoDate() != null ? report.getPoDate() : "");  // Changed
                dataRow.createCell(cellNum++).setCellValue(report.getFaCategory());
                dataRow.createCell(cellNum++).setCellValue(report.getL1());
                dataRow.createCell(cellNum++).setCellValue(report.getL2());
                dataRow.createCell(cellNum++).setCellValue(report.getL3());
                dataRow.createCell(cellNum++).setCellValue(report.getL4());
                dataRow.createCell(cellNum++).setCellValue(report.getAccumulatedDepreciationCode());
                dataRow.createCell(cellNum++).setCellValue(report.getDepreciationCode());
                dataRow.createCell(cellNum++).setCellValue(report.getUsefulLifeMonths() != null ? report.getUsefulLifeMonths() : 0);
                dataRow.createCell(cellNum++).setCellValue(report.getVendorName());
                dataRow.createCell(cellNum++).setCellValue(report.getVendorNumber());
                dataRow.createCell(cellNum++).setCellValue(report.getProjectNumber());
                dataRow.createCell(cellNum++).setCellValue(report.getDateOfService() != null ? report.getDateOfService() : "");  // Changed
                dataRow.createCell(cellNum++).setCellValue(report.getOldFarCategory());
                dataRow.createCell(cellNum++).setCellValue(report.getCostCenterData());
                dataRow.createCell(cellNum++).setCellValue(report.getAdjustment() != null ? report.getAdjustment().doubleValue() : 0);
                dataRow.createCell(cellNum++).setCellValue(report.getTaskId());
                dataRow.createCell(cellNum++).setCellValue(report.getPoLineNumber());
                dataRow.createCell(cellNum++).setCellValue(report.getMonthlyDepreciationAmount() != null ? report.getMonthlyDepreciationAmount().doubleValue() : 0);
                dataRow.createCell(cellNum++).setCellValue(report.getAccumulatedDepreciation() != null ? report.getAccumulatedDepreciation().doubleValue() : 0);
                dataRow.createCell(cellNum++).setCellValue(report.getStatusFlag());
                dataRow.createCell(cellNum++).setCellValue(report.getNetCost() != null ? report.getNetCost().doubleValue() : 0);
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                workbook.write(baos);
                return baos.toByteArray();
            } finally {
                workbook.dispose();
            }
        } catch (Exception e) {
            logger.error("Error generating Excel", e);
            throw e;
        }
    }



    @PostMapping("/reports/single")
    @Transactional
    public ResponseEntity<Map<String, Object>> createSingleReport(
            @RequestBody tb_FinancialReport report,
            Principal principal) {
        if (report == null) {
            return new ResponseEntity<>(
                    Collections.singletonMap("message", "No record provided"),
                    HttpStatus.BAD_REQUEST);
        }
        return uploadFinancialReports(Collections.singletonList(report), principal);
    }


    @PostMapping("/reports/single-edit/{identifier}")
    @Transactional
    public ResponseEntity<?> singleEditReport(
            @PathVariable String identifier,
            @RequestBody tb_FinancialReport updatedReport,
            Principal principal) {
        ResponseEntity<?> response = modifyReport(identifier, updatedReport);
        if (auditLogService != null && response.getStatusCode().is2xxSuccessful()) {
            try {
                String username = principal != null ? principal.getName()
                        : (updatedReport.getChangedBy() != null ? updatedReport.getChangedBy() : "system");
                auditLogService.logAction(
                        "FinancialReport",
                        "SINGLE_EDIT",
                        username,
                        "Edited FR record with identifier: " + identifier);
            } catch (Exception ignore) {
                logger.warn("Failed to write audit log for single edit on {}", identifier);
            }
        }
        return response;
    }

    // ----- helpers -----


    private void triggerPostUploadSync(Principal principal, int affectedRows) {
        if (affectedRows <= 0) return; // nothing changed → nothing to sync
        String user = principal != null ? principal.getName() : "system";
        Thread t = new Thread(() -> {
            try {
                if (syncOrchestratorService != null) {
                    logger.info("Auto-triggering SyncOrchestrator after FR upload by {} ({} rows affected)",
                            user, affectedRows);
                    com.telkom.co.ke.almoptics.services.SyncOrchestratorService.RunSummary summary =
                            syncOrchestratorService.runFullCycle("post-upload:" + user);
                    logger.info("Post-upload sync result: {}",
                            summary == null ? "skipped" : summary.toShortString());
                    if (auditLogService != null) {
                        // tb_AuditLog.details is VARCHAR(255) — keep the
                        // message short so the insert never trips the
                        // "Data too long for column 'details'" error.
                        String details = "FR upload of " + affectedRows
                                + " rows triggered orchestrator sync. "
                                + (summary == null ? "" : summary.toShortString());
                        if (details.length() > 240) {
                            details = details.substring(0, 239) + "…";
                        }
                        auditLogService.logAction(
                                "FinancialReport", "AUTO_SYNC_AFTER_UPLOAD", user, details);
                    }
                } else if (financialSyncScheduler != null) {
                    logger.info("Auto-triggering legacy FinancialSyncScheduler after FR upload by {}", user);
                    String summary = financialSyncScheduler.triggerManualSync();
                    logger.info("Post-upload legacy sync result: {}", summary);
                } else {
                    logger.debug("No sync orchestrator wired — skipping post-upload sync");
                }
            } catch (Exception e) {
                logger.error("Post-upload sync failed: {}", e.getMessage(), e);
            }
        }, "fr-post-upload-sync");
        t.setDaemon(true);
        t.start();
    }

    @PostMapping("/fetch-financereport")
    public ResponseEntity<?> fetchFinancialReports(
            @RequestBody ApprovalRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletResponse response) throws Exception {

        try {
            // Merge query params into request
            if (request.getPage() == null) request.setPage(page);
            if (request.getSize() == null || request.getSize() <= 0) request.setSize(size);

            // ====================== EXPORT HANDLING ======================
            if (Boolean.TRUE.equals(request.isExportAll()) && request.getFormat() != null) {

                String format = request.getFormat().toLowerCase().trim();

                if ("csv".equals(format)) {
                    // CSV Export
                    response.setContentType("text/csv");
                    response.setCharacterEncoding("UTF-8");
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"financial_reports_" +
                                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv\"");

                    try (PrintWriter writer = response.getWriter()) {
                        financeReportFetchService.streamExportToCsv(request, writer);
                    }
                    return null; // Response already written directly

                } else if ("xlsx".equals(format) || "excel".equals(format)) {
                    // Excel Export
                    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"financial_reports_" +
                                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx\"");

                    try (OutputStream out = response.getOutputStream()) {
                        financeReportFetchService.streamExportToExcel(request, out);
                    }
                    return null; // Response already written
                }
            }

            // ====================== NORMAL PAGINATED FETCH ======================
            // If not exporting, return JSON with pagination + totals

            PageResult<Map<String, Object>> pageResult = financeReportFetchService.multiFilterSearch(request);

            Map<String, BigDecimal> grandTotals = financeReportFetchService.getAggregateTotals(request);
            Map<String, BigDecimal> pageAggregates = financeReportFetchService.calculatePageAggregates(
                    pageResult.getdata());

            Map<String, Object> result = new HashMap<>();

            result.put("reports", pageResult.getdata());
            result.put("currentPage", pageResult.getPage());
            result.put("totalItems", pageResult.getTotalElements());
            result.put("totalPages", pageResult.getTotalPages());
            result.put("first", pageResult.getPage() == 0);
            result.put("last", pageResult.getPage() >= pageResult.getTotalPages() - 1);
            result.put("size", pageResult.getSize());
            result.put("sort", sortBy + "," + sortDir);

            // Grand totals (full filtered set)
            result.put("totalCost", grandTotals.get("totalCost"));
            result.put("totalDepreciation", grandTotals.get("totalDepreciation"));
            result.put("totalNBV", grandTotals.get("totalNBV"));

            // Current page aggregates
            result.put("filteredCost", pageAggregates.get("filteredCost"));
            result.put("filteredDepreciation", pageAggregates.get("filteredDepreciation"));
            result.put("filteredNBV", pageAggregates.get("filteredNBV"));

            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Error in /fetch-financereport", e);
            return new ResponseEntity<>(
                    Collections.singletonMap("message", "Error: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // =====================================================================
    // UNMAPPED ACTIVE INVENTORY ENDPOINTS
    // =====================================================================

    @PostMapping("/fetch-unmappedactive")
    public ResponseEntity<?> fetchUnmappedActive(
            @RequestBody ApprovalRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletResponse response) throws Exception {

        try {
            // Merge query params into request
            if (request.getPage() == null) request.setPage(page);
            if (request.getSize() == null || request.getSize() <= 0) request.setSize(size);

            // ====================== EXPORT HANDLING ======================
            if (Boolean.TRUE.equals(request.isExportAll()) && request.getFormat() != null) {

                String format = request.getFormat().toLowerCase().trim();

                if ("csv".equals(format)) {
                    // CSV Export
                    response.setContentType("text/csv");
                    response.setCharacterEncoding("UTF-8");
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"unmapped_active_" +
                                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv\"");

                    try (PrintWriter writer = response.getWriter()) {
                        unmappedReportFetchService.streamUnmappedActiveToCsv(request, writer);
                    }
                    return null; // Response already written directly

                } else if ("xlsx".equals(format) || "excel".equals(format)) {
                    // Excel Export
                    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"unmapped_active_" +
                                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx\"");

                    try (OutputStream out = response.getOutputStream()) {
                        unmappedReportFetchService.streamUnmappedActiveToExcel(request, out);
                    }
                    return null; // Response already written
                }
            }

            // ====================== NORMAL PAGINATED FETCH ======================
            PageResult<Map<String, Object>> pageResult = unmappedReportFetchService.searchUnmappedActive(request);

            Map<String, Object> result = new HashMap<>();
            result.put("reports", pageResult.getdata());
            result.put("currentPage", pageResult.getPage());
            result.put("totalItems", pageResult.getTotalElements());
            result.put("totalPages", pageResult.getTotalPages());
            result.put("first", pageResult.getPage() == 0);
            result.put("last", pageResult.getPage() >= pageResult.getTotalPages() - 1);
            result.put("size", pageResult.getSize());
            result.put("sort", sortBy + "," + sortDir);

            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Error in /fetch-unmappedactive", e);
            return new ResponseEntity<>(
                    Collections.singletonMap("message", "Error: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // =====================================================================
    // UNMAPPED PASSIVE INVENTORY ENDPOINTS
    // =====================================================================

    @PostMapping("/fetch-unmappedpassive")
    public ResponseEntity<?> fetchUnmappedPassive(
            @RequestBody ApprovalRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "ID") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletResponse response) throws Exception {

        try {
            // Merge query params into request
            if (request.getPage() == null) request.setPage(page);
            if (request.getSize() == null || request.getSize() <= 0) request.setSize(size);

            // ====================== EXPORT HANDLING ======================
            if (Boolean.TRUE.equals(request.isExportAll()) && request.getFormat() != null) {

                String format = request.getFormat().toLowerCase().trim();

                if ("csv".equals(format)) {
                    // CSV Export
                    response.setContentType("text/csv");
                    response.setCharacterEncoding("UTF-8");
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"unmapped_passive_" +
                                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv\"");

                    try (PrintWriter writer = response.getWriter()) {
                        unmappedReportFetchService.streamUnmappedPassiveToCsv(request, writer);
                    }
                    return null; // Response already written directly

                } else if ("xlsx".equals(format) || "excel".equals(format)) {
                    // Excel Export
                    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"unmapped_passive_" +
                                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx\"");

                    try (OutputStream out = response.getOutputStream()) {
                        unmappedReportFetchService.streamUnmappedPassiveToExcel(request, out);
                    }
                    return null; // Response already written
                }
            }

            // ====================== NORMAL PAGINATED FETCH ======================
            PageResult<Map<String, Object>> pageResult = unmappedReportFetchService.searchUnmappedPassive(request);

            Map<String, Object> result = new HashMap<>();
            result.put("reports", pageResult.getdata());
            result.put("currentPage", pageResult.getPage());
            result.put("totalItems", pageResult.getTotalElements());
            result.put("totalPages", pageResult.getTotalPages());
            result.put("first", pageResult.getPage() == 0);
            result.put("last", pageResult.getPage() >= pageResult.getTotalPages() - 1);
            result.put("size", pageResult.getSize());
            result.put("sort", sortBy + "," + sortDir);

            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Error in /fetch-unmappedpassive", e);
            return new ResponseEntity<>(
                    Collections.singletonMap("message", "Error: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // =====================================================================
    // UNMAPPED IT INVENTORY ENDPOINTS
    // =====================================================================

    @PostMapping("/fetch-unmappedit")
    public ResponseEntity<?> fetchUnmappedIT(
            @RequestBody ApprovalRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "ID") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletResponse response) throws Exception {

        try {
            // Merge query params into request
            if (request.getPage() == null) request.setPage(page);
            if (request.getSize() == null || request.getSize() <= 0) request.setSize(size);

            // ====================== EXPORT HANDLING ======================
            if (Boolean.TRUE.equals(request.isExportAll()) && request.getFormat() != null) {

                String format = request.getFormat().toLowerCase().trim();

                if ("csv".equals(format)) {
                    // CSV Export
                    response.setContentType("text/csv");
                    response.setCharacterEncoding("UTF-8");
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"unmapped_it_" +
                                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv\"");

                    try (PrintWriter writer = response.getWriter()) {
                        unmappedReportFetchService.streamUnmappedITToCsv(request, writer);
                    }
                    return null; // Response already written directly

                } else if ("xlsx".equals(format) || "excel".equals(format)) {
                    // Excel Export
                    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"unmapped_it_" +
                                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx\"");

                    try (OutputStream out = response.getOutputStream()) {
                        unmappedReportFetchService.streamUnmappedITToExcel(request, out);
                    }
                    return null; // Response already written
                }
            }

            // ====================== NORMAL PAGINATED FETCH ======================
            PageResult<Map<String, Object>> pageResult = unmappedReportFetchService.searchUnmappedIT(request);

            Map<String, Object> result = new HashMap<>();
            result.put("reports", pageResult.getdata());
            result.put("currentPage", pageResult.getPage());
            result.put("totalItems", pageResult.getTotalElements());
            result.put("totalPages", pageResult.getTotalPages());
            result.put("first", pageResult.getPage() == 0);
            result.put("last", pageResult.getPage() >= pageResult.getTotalPages() - 1);
            result.put("size", pageResult.getSize());
            result.put("sort", sortBy + "," + sortDir);

            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Error in /fetch-unmappedit", e);
            return new ResponseEntity<>(
                    Collections.singletonMap("message", "Error: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // =====================================================================
    // WRITE-OFF REPORT ENDPOINTS
    // =====================================================================

    @PostMapping("/fetch-writeoff")
    public ResponseEntity<?> fetchWriteOff(
            @RequestBody ApprovalRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletResponse response) throws Exception {

        try {
            // Merge query params into request
            if (request.getPage() == null) request.setPage(page);
            if (request.getSize() == null || request.getSize() <= 0) request.setSize(size);

            // ====================== EXPORT HANDLING ======================
            if (Boolean.TRUE.equals(request.isExportAll()) && request.getFormat() != null) {

                String format = request.getFormat().toLowerCase().trim();

                if ("csv".equals(format)) {
                    // CSV Export
                    response.setContentType("text/csv");
                    response.setCharacterEncoding("UTF-8");
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"writeoff_report_" +
                                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv\"");

                    try (PrintWriter writer = response.getWriter()) {
                        writeOffReportFetchService.streamWriteOffToCsv(request, writer);
                    }
                    return null; // Response already written directly

                } else if ("xlsx".equals(format) || "excel".equals(format)) {
                    // Excel Export
                    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"writeoff_report_" +
                                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx\"");

                    try (OutputStream out = response.getOutputStream()) {
                        writeOffReportFetchService.streamWriteOffToExcel(request, out);
                    }
                    return null; // Response already written
                }
            }

            // ====================== NORMAL PAGINATED FETCH ======================
            PageResult<Map<String, Object>> pageResult = writeOffReportFetchService.searchWriteOff(request);

            Map<String, Object> result = new HashMap<>();
            result.put("reports", pageResult.getdata());
            result.put("currentPage", pageResult.getPage());
            result.put("totalItems", pageResult.getTotalElements());
            result.put("totalPages", pageResult.getTotalPages());
            result.put("first", pageResult.getPage() == 0);
            result.put("last", pageResult.getPage() >= pageResult.getTotalPages() - 1);
            result.put("size", pageResult.getSize());
            result.put("sort", sortBy + "," + sortDir);

            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Error in /fetch-writeoff", e);
            return new ResponseEntity<>(
                    Collections.singletonMap("message", "Error: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}