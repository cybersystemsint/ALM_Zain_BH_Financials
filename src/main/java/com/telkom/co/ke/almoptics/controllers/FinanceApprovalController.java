package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.entities.tb_ApprovalWorkflow;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
import com.telkom.co.ke.almoptics.services.ApprovalWorkflowService;
import com.telkom.co.ke.almoptics.services.WriteOffReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/finance-approval")
@CrossOrigin(origins = "http://localhost:3000")
public class FinanceApprovalController {

    private static final Logger logger = LoggerFactory.getLogger(FinanceApprovalController.class);

    @Autowired
    private FinancialReportService financialReportService;

    @Autowired
    private ApprovalWorkflowService approvalWorkflowService;

    @Autowired
    private WriteOffReportService writeOffReportService;

    @GetMapping("/approvals")
    public ResponseEntity<Map<String, Object>> getFinanceApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam String team,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String originalStatus,
            @RequestParam(required = false) String objectStatus,
            @RequestParam(required = false) String processId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            SimpleDateFormat sdfFilter = new SimpleDateFormat("yyyy-MM-dd");

            Sort sort = Sort.by("insertDate").descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<tb_ApprovalWorkflow> workflowPage = approvalWorkflowService.findAll(pageable);
            List<tb_ApprovalWorkflow> allWorkflows = workflowPage.getContent();

            Date start = startDate != null ? sdfFilter.parse(startDate) : null;
            Date end = endDate != null ? sdfFilter.parse(endDate) : null;

            String expectedStatus;
            switch (team) {
                case "Financial L1":
                    expectedStatus = "Pending L1 Approval";
                    break;
                case "Financial L2":
                    expectedStatus = "Pending L2 Approval";
                    break;
                case "Financial L3":
                    expectedStatus = "Pending L3 Approval";
                    break;
                default:
                    return new ResponseEntity<>(Collections.singletonMap("message", "Invalid team"), HttpStatus.BAD_REQUEST);
            }

            List<tb_ApprovalWorkflow> filteredWorkflows = allWorkflows.stream()
                    .filter(workflow -> expectedStatus.equals(workflow.getUPDATED_STATUS()))
                    .filter(workflow -> processId == null || processId.equals(String.valueOf(workflow.getPROCESS_ID())))
                    .filter(workflow -> originalStatus == null || originalStatus.equals(workflow.getORIGINAL_STATUS()))
                    .filter(workflow -> {
                        tb_FinancialReport report = null;
                        if (workflow.getASSET_ID() != null) {
                            Optional<tb_FinancialReport> reportOpt = financialReportService.findByAssetName(workflow.getASSET_ID());
                            if (!reportOpt.isPresent()) {
                                reportOpt = financialReportService.findBySerialNumber(workflow.getASSET_ID());
                            }
                            report = reportOpt.orElse(null);
                        }

                        boolean matchesObjectType = objectType == null || objectType.equals(workflow.getObjectType());
                        boolean matchesAssetId = assetId == null || assetId.equals(workflow.getASSET_ID());
                        boolean matchesObjectStatus = objectStatus == null || (report != null && objectStatus.equals(report.getStatusFlag()));
                        boolean matchesStartDate = start == null || (workflow.getINSERTDATE() != null && !workflow.getINSERTDATE().before(start));
                        boolean matchesEndDate = end == null || (workflow.getCHANGEDATE() != null && !workflow.getCHANGEDATE().after(end));

                        return matchesObjectType && matchesAssetId && matchesObjectStatus && matchesStartDate && matchesEndDate;
                    })
                    .collect(Collectors.toList());

            List<Map<String, Object>> items = filteredWorkflows.stream().map(workflow -> {
                Map<String, Object> item = new HashMap<>();
                tb_FinancialReport report = null;
                if (workflow.getASSET_ID() != null) {
                    Optional<tb_FinancialReport> reportOpt = financialReportService.findByAssetName(workflow.getASSET_ID());
                    if (!reportOpt.isPresent()) {
                        reportOpt = financialReportService.findBySerialNumber(workflow.getASSET_ID());
                    }
                    report = reportOpt.orElse(null);
                }

                item.put("OBJECT_TYPE", workflow.getObjectType());
                item.put("ASSET_ID", workflow.getASSET_ID());
                item.put("ORIGINAL_STATUS", workflow.getORIGINAL_STATUS());
                item.put("UPDATED_STATUS", workflow.getUPDATED_STATUS());
                item.put("REQUESTER", workflow.getINSERTEDBY());
                item.put("UPDATER", workflow.getCHANGEDBY());
                item.put("PROCESS_ID", workflow.getPROCESS_ID());
                item.put("COMMENTS", workflow.getCOMMENTS());
                item.put("INSERTDATE", workflow.getINSERTDATE() != null ? sdf.format(workflow.getINSERTDATE()) : "");
                item.put("CHANGEDATE", workflow.getCHANGEDATE() != null ? sdf.format(workflow.getCHANGEDATE()) : "");
                item.put("WAREHOUSE_ID", report != null ? report.getSiteId() : null);
                item.put("WORKFLOW_ID", workflow.getID());
                item.put("ASSET_SERIAL_NUMBER", report != null ? report.getAssetSerialNumber() : null);
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("items", items);
            response.put("totalCount", filteredWorkflows.size());
            response.put("currentPage", page);
            response.put("totalPages", (int) Math.ceil((double) filteredWorkflows.size() / size));

            logger.info("Returning {} items for team {} with status {}", items.size(), team, expectedStatus);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (ParseException e) {
            logger.error("Error parsing date parameters", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Invalid date format: " + e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Error fetching finance approvals", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error fetching approvals: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/approve")
    public ResponseEntity<Map<String, Object>> approveItems(
            @RequestBody Map<String, Object> requestBody,
            Principal principal) {
        try {
            List<Integer> workflowIds = ((List<?>) requestBody.get("items")).stream()
                    .map(id -> Integer.parseInt(id.toString()))
                    .collect(Collectors.toList());
            String comment = (String) requestBody.get("comment");
            String username = principal != null ? principal.getName() : "system";

            if (workflowIds.isEmpty()) {
                return new ResponseEntity<>(Collections.singletonMap("message", "No items selected"), HttpStatus.BAD_REQUEST);
            }

            List<Integer> processedWorkflows = new ArrayList<>();
            List<String> failedWorkflows = new ArrayList<>();

            for (Integer workflowId : workflowIds) {
                Optional<tb_ApprovalWorkflow> workflowOpt = approvalWorkflowService.findById(workflowId);
                if (!workflowOpt.isPresent()) {
                    failedWorkflows.add("Workflow " + workflowId + " not found");
                    logger.warn("Workflow ID {} not found", workflowId);
                    continue;
                }

                tb_ApprovalWorkflow workflow = workflowOpt.get();
                String currentStatus = workflow.getUPDATED_STATUS();

                if ("REJECTED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
                    failedWorkflows.add("Workflow " + workflowId + " is already " + currentStatus);
                    logger.info("Workflow ID {} skipped: already {}", workflowId, currentStatus);
                    continue;
                }

                if (workflow.getASSET_ID() == null || workflow.getASSET_ID().trim().isEmpty()) {
                    failedWorkflows.add("Workflow " + workflowId + ": ASSET_ID is null or empty");
                    logger.warn("Workflow ID {} has null or empty ASSET_ID", workflowId);
                    continue;
                }

                try {
                    if (approvalWorkflowService.approveWorkflow(workflowId, comment, username)) {
                        processedWorkflows.add(workflowId);
                        logger.info("Workflow ID {} approved successfully", workflowId);
                    } else {
                        failedWorkflows.add("Workflow " + workflowId + ": Failed to approve, check ASSET_ID " + workflow.getASSET_ID());
                        logger.warn("Workflow ID {} failed to approve for ASSET_ID {}", workflowId, workflow.getASSET_ID());
                    }
                } catch (Exception e) {
                    failedWorkflows.add("Workflow " + workflowId + ": " + e.getMessage());
                    logger.error("Error approving workflow ID {}: {}", workflowId, e.getMessage(), e);
                }
            }

            Map<String, Object> response = new HashMap<>();
            String message = processedWorkflows.isEmpty() ?
                    "No workflows approved" :
                    "Successfully approved " + processedWorkflows.size() + " item(s)";
            if (!failedWorkflows.isEmpty()) {
                message += ". Failed: " + String.join("; ", failedWorkflows);
                response.put("failedWorkflows", failedWorkflows);
            }
            response.put("message", message);
            response.put("processedWorkflows", processedWorkflows);

            logger.info("Approval result: {}", message);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error approving items", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error approving items: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/reject")
    public ResponseEntity<Map<String, Object>> rejectItems(
            @RequestBody Map<String, Object> requestBody,
            Principal principal) {
        try {
            List<Integer> workflowIds = ((List<?>) requestBody.get("items")).stream()
                    .map(id -> Integer.parseInt(id.toString()))
                    .collect(Collectors.toList());
            String comment = (String) requestBody.get("comment");
            String username = principal != null ? principal.getName() : "system";

            if (workflowIds.isEmpty()) {
                return new ResponseEntity<>(Collections.singletonMap("message", "No items selected"), HttpStatus.BAD_REQUEST);
            }

            List<Integer> processedWorkflows = new ArrayList<>();
            List<String> failedWorkflows = new ArrayList<>();

            for (Integer workflowId : workflowIds) {
                Optional<tb_ApprovalWorkflow> workflowOpt = approvalWorkflowService.findById(workflowId);
                if (!workflowOpt.isPresent()) {
                    failedWorkflows.add("Workflow " + workflowId + " not found");
                    logger.warn("Workflow ID {} not found", workflowId);
                    continue;
                }

                tb_ApprovalWorkflow workflow = workflowOpt.get();
                String currentStatus = workflow.getUPDATED_STATUS();

                if ("REJECTED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
                    failedWorkflows.add("Workflow " + workflowId + " is already " + currentStatus);
                    logger.info("Workflow ID {} skipped: already {}", workflowId, currentStatus);
                    continue;
                }

                if (workflow.getASSET_ID() == null || workflow.getASSET_ID().trim().isEmpty()) {
                    failedWorkflows.add("Workflow " + workflowId + ": ASSET_ID is null or empty");
                    logger.warn("Workflow ID {} has null or empty ASSET_ID", workflowId);
                    continue;
                }

                try {
                    if (approvalWorkflowService.rejectWorkflow(workflowId, comment, username)) {
                        processedWorkflows.add(workflowId);
                        logger.info("Workflow ID {} rejected successfully", workflowId);
                    } else {
                        failedWorkflows.add("Workflow " + workflowId + ": Failed to reject, check ASSET_ID " + workflow.getASSET_ID());
                        logger.warn("Workflow ID {} failed to reject for ASSET_ID {}", workflowId, workflow.getASSET_ID());
                    }
                } catch (Exception e) {
                    failedWorkflows.add("Workflow " + workflowId + ": " + e.getMessage());
                    logger.error("Error rejecting workflow ID {}: {}", workflowId, e.getMessage(), e);
                }
            }

            Map<String, Object> response = new HashMap<>();
            String message = processedWorkflows.isEmpty() ?
                    "No workflows rejected" :
                    "Successfully rejected " + processedWorkflows.size() + " item(s)";
            if (!failedWorkflows.isEmpty()) {
                message += ". Failed: " + String.join("; ", failedWorkflows);
                response.put("failedWorkflows", failedWorkflows);
            }
            response.put("message", message);
            response.put("processedWorkflows", processedWorkflows);

            logger.info("Rejection result: {}", message);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error rejecting items", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error rejecting items: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelItems(
            @RequestBody Map<String, Object> requestBody,
            Principal principal) {
        try {
            List<Integer> workflowIds = ((List<?>) requestBody.get("items")).stream()
                    .map(id -> Integer.parseInt(id.toString()))
                    .collect(Collectors.toList());
            String comment = (String) requestBody.get("comment");
            String username = principal != null ? principal.getName() : "system";

            if (workflowIds.isEmpty()) {
                return new ResponseEntity<>(Collections.singletonMap("message", "No items selected"), HttpStatus.BAD_REQUEST);
            }

            List<Integer> processedWorkflows = new ArrayList<>();
            List<String> failedWorkflows = new ArrayList<>();

            for (Integer workflowId : workflowIds) {
                Optional<tb_ApprovalWorkflow> workflowOpt = approvalWorkflowService.findById(workflowId);
                if (!workflowOpt.isPresent()) {
                    failedWorkflows.add("Workflow " + workflowId + " not found");
                    logger.warn("Workflow ID {} not found", workflowId);
                    continue;
                }

                tb_ApprovalWorkflow workflow = workflowOpt.get();
                String currentStatus = workflow.getUPDATED_STATUS();

                if ("CANCELLED".equals(currentStatus) || "REJECTED".equals(currentStatus)) {
                    failedWorkflows.add("Workflow " + workflowId + " is already " + currentStatus);
                    logger.info("Workflow ID {} skipped: already {}", workflowId, currentStatus);
                    continue;
                }

                if (workflow.getASSET_ID() == null || workflow.getASSET_ID().trim().isEmpty()) {
                    failedWorkflows.add("Workflow " + workflowId + ": ASSET_ID is null or empty");
                    logger.warn("Workflow ID {} has null or empty ASSET_ID", workflowId);
                    continue;
                }

                try {
                    if (approvalWorkflowService.cancelWorkflow(workflowId, comment, username)) {
                        processedWorkflows.add(workflowId);
                        logger.info("Workflow ID {} cancelled successfully", workflowId);
                    } else {
                        failedWorkflows.add("Workflow " + workflowId + ": Failed to cancel, check ASSET_ID " + workflow.getASSET_ID());
                        logger.warn("Workflow ID {} failed to cancel for ASSET_ID {}", workflowId, workflow.getASSET_ID());
                    }
                } catch (Exception e) {
                    failedWorkflows.add("Workflow " + workflowId + ": " + e.getMessage());
                    logger.error("Error cancelling workflow ID {}: {}", workflowId, e.getMessage(), e);
                }
            }

            Map<String, Object> response = new HashMap<>();
            String message = processedWorkflows.isEmpty() ?
                    "No workflows cancelled" :
                    "Successfully cancelled " + processedWorkflows.size() + " item(s)";
            if (!failedWorkflows.isEmpty()) {
                message += ". Failed: " + String.join("; ", failedWorkflows);
                response.put("failedWorkflows", failedWorkflows);
            }
            response.put("message", message);
            response.put("processedWorkflows", processedWorkflows);

            logger.info("Cancellation result: {}", message);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error cancelling items", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error cancelling items: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/modify")
    public ResponseEntity<Map<String, Object>> modifyFinancialReport(
            @RequestBody tb_FinancialReport updatedReport,
            Principal principal) {
        try {
            if (updatedReport.getId() == null) {
                return new ResponseEntity<>(Collections.singletonMap("message", "Financial report ID is required"), HttpStatus.BAD_REQUEST);
            }

            Optional<tb_FinancialReport> existingReportOpt = financialReportService.findById(Math.toIntExact(updatedReport.getId()));
            if (!existingReportOpt.isPresent()) {
                return new ResponseEntity<>(Collections.singletonMap("message", "Financial report not found"), HttpStatus.NOT_FOUND);
            }

            tb_FinancialReport existingReport = existingReportOpt.get();
            String username = principal != null ? principal.getName() : "system";

            if (updatedReport.getInitialCost() != null) {
                existingReport.setInitialCost(updatedReport.getInitialCost());
            }
            if (updatedReport.getAssetSerialNumber() != null) {
                existingReport.setAssetSerialNumber(updatedReport.getAssetSerialNumber());
            }
            if (updatedReport.getSiteId() != null) {
                existingReport.setSiteId(updatedReport.getSiteId());
            }
            if (updatedReport.getWriteOffDate() != null) {
                existingReport.setWriteOffDate(updatedReport.getWriteOffDate());
            }
            existingReport.setChangedBy(username);
            existingReport.setChangeDate(new java.sql.Timestamp(System.currentTimeMillis()));

            String nodeType = existingReport.getNodeType() != null ? existingReport.getNodeType() : "default";

            if (existingReport.getInitialCost() != null && existingReport.getInitialCost().compareTo(BigDecimal.ZERO) == 0) {
                approvalWorkflowService.createDeletionWorkflow(existingReport, nodeType, "pending deletion");
                financialReportService.save(existingReport);
                logger.info("Created deletion workflow for financial report ID: {}", existingReport.getId());
            } else {
                approvalWorkflowService.createApprovalWorkflow(existingReport, nodeType, "pending modification");
                financialReportService.save(existingReport);
                logger.info("Created modification workflow for financial report ID: {}", existingReport.getId());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Financial report updated and workflow created successfully");
            response.put("reportId", existingReport.getId());
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error modifying financial report", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error modifying financial report: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchFinanceApprovals(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        try {
            Sort sort = Sort.by("insertDate").descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<tb_ApprovalWorkflow> workflowPage = approvalWorkflowService.findAll(pageable);
            List<tb_ApprovalWorkflow> allWorkflows = workflowPage.getContent();

            String queryLower = query.toLowerCase().trim();
            List<tb_ApprovalWorkflow> filteredWorkflows = allWorkflows.stream()
                    .filter(workflow -> {
                        boolean matchesAssetId = workflow.getASSET_ID() != null && workflow.getASSET_ID().toLowerCase().contains(queryLower);
                        boolean matchesSerial = false;
                        if (workflow.getASSET_ID() != null) {
                            Optional<tb_FinancialReport> reportOpt = financialReportService.findByAssetName(workflow.getASSET_ID());
                            if (!reportOpt.isPresent()) {
                                reportOpt = financialReportService.findBySerialNumber(workflow.getASSET_ID());
                            }
                            if (reportOpt.isPresent()) {
                                tb_FinancialReport report = reportOpt.get();
                                matchesSerial = report.getAssetSerialNumber() != null &&
                                        report.getAssetSerialNumber().toLowerCase().contains(queryLower);
                            }
                        }
                        return matchesAssetId || matchesSerial;
                    })
                    .collect(Collectors.toList());

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            List<Map<String, Object>> items = filteredWorkflows.stream().map(workflow -> {
                Map<String, Object> item = new HashMap<>();
                tb_FinancialReport report = null;
                if (workflow.getASSET_ID() != null) {
                    Optional<tb_FinancialReport> reportOpt = financialReportService.findByAssetName(workflow.getASSET_ID());
                    if (!reportOpt.isPresent()) {
                        reportOpt = financialReportService.findBySerialNumber(workflow.getASSET_ID());
                    }
                    report = reportOpt.orElse(null);
                }

                item.put("OBJECT_TYPE", workflow.getObjectType());
                item.put("ASSET_ID", workflow.getASSET_ID());
                item.put("ORIGINAL_STATUS", workflow.getORIGINAL_STATUS());
                item.put("UPDATED_STATUS", workflow.getUPDATED_STATUS());
                item.put("REQUESTER", workflow.getINSERTEDBY());
                item.put("UPDATER", workflow.getCHANGEDBY());
                item.put("PROCESS_ID", workflow.getPROCESS_ID());
                item.put("COMMENTS", workflow.getCOMMENTS());
                item.put("INSERTDATE", workflow.getINSERTDATE() != null ? sdf.format(workflow.getINSERTDATE()) : "");
                item.put("CHANGEDATE", workflow.getCHANGEDATE() != null ? sdf.format(workflow.getCHANGEDATE()) : "");
                item.put("WAREHOUSE_ID", report != null ? report.getSiteId() : null);
                item.put("WORKFLOW_ID", workflow.getID());
                item.put("ASSET_SERIAL_NUMBER", report != null ? report.getAssetSerialNumber() : null);
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("items", items);
            response.put("totalCount", filteredWorkflows.size());
            response.put("currentPage", page);
            response.put("totalPages", (int) Math.ceil((double) filteredWorkflows.size() / size));

            logger.info("Search returned {} items for query '{}'", items.size(), query);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error searching finance approvals", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error searching approvals: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}