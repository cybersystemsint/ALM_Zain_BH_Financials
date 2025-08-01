package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.entities.tb_ApprovalWorkflow;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.services.ApprovalWorkflowService;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
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

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/finance-approval")
@CrossOrigin(origins = "http://localhost:3000")
public class FinanceApprovals {

    private static final Logger logger = LoggerFactory.getLogger(FinanceApprovals.class);

    @Autowired
    private ApprovalWorkflowService approvalWorkflowService;

    @Autowired
    private FinancialReportService financialReportService;

    // DTO for the POST request body
    public static class ApprovalRequest {
        private int page = 0;
        private int size = 100;
        private String team;
        private String objectType;
        private String assetId;
        private String originalStatus;
        private String updatedStatus;
        private String processId;
        private String startDate;
        private String endDate;

        // Getters and setters
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
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
    }

    @PostMapping("/approvals")
    public ResponseEntity<Map<String, Object>> getFinanceApprovals(@RequestBody ApprovalRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            int page = request.getPage();
            int size = request.getSize();
            String team = request.getTeam();
            String objectType = request.getObjectType();
            String assetId = request.getAssetId();
            String originalStatus = request.getOriginalStatus();
            String updatedStatus = request.getUpdatedStatus();
            String processId = request.getProcessId();
            String startDate = request.getStartDate();
            String endDate = request.getEndDate();

            Pageable pageable = PageRequest.of(page, size, Sort.by("insertDate").descending());

            // Fetch with optional filters (objectStatus is null since not in DTO)
            Page<tb_ApprovalWorkflow> workflowPage = approvalWorkflowService.findByFilters(
                    team, objectType, assetId, originalStatus, updatedStatus, processId, startDate, endDate, null, pageable
            );

            logger.debug("Retrieved {} workflows with filters", workflowPage.getContent().size());

            // Batch-fetch related reports
            List<String> assetIds = workflowPage.getContent().stream()
                    .map(tb_ApprovalWorkflow::getASSET_ID)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            List<tb_FinancialReport> reports = financialReportService.findAllByAssetNameInOrAssetSerialNumberIn(assetIds);

            Map<String, tb_FinancialReport> reportMap = new HashMap<>();
            for (tb_FinancialReport report : reports) {
                if (report.getAssetName() != null) {
                    reportMap.putIfAbsent(report.getAssetName(), report);
                }
                if (report.getAssetSerialNumber() != null) {
                    reportMap.putIfAbsent(report.getAssetSerialNumber(), report);
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            List<Map<String, Object>> items = workflowPage.getContent().stream().map(workflow -> {
                Map<String, Object> item = new HashMap<>();
                tb_FinancialReport report = reportMap.get(workflow.getASSET_ID());

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
            response.put("totalCount", workflowPage.getTotalElements());
            response.put("currentPage", page);
            response.put("totalPages", workflowPage.getTotalPages());

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Returning {} items with filters. Took {} ms", items.size(), duration);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error fetching finance approvals", e);
            return new ResponseEntity<>(Map.of("message", "Error fetching approvals: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchFinanceApprovals(@RequestBody ApprovalRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            int page = request.getPage();
            int size = request.getSize();
            String query = request.getAssetId(); // Search by assetId or serialNumber

            if (query == null || query.trim().isEmpty()) {
                return new ResponseEntity<>(Map.of("message", "Search query is required"), HttpStatus.BAD_REQUEST);
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by("insertDate").descending());

            // Search by assetId or serialNumber
            Page<tb_ApprovalWorkflow> workflowPage = approvalWorkflowService.searchByAssetIdOrSerialNumber(query, pageable);

            logger.debug("Retrieved {} workflows for search query '{}'", workflowPage.getContent().size(), query);

            // Batch-fetch related reports
            List<String> assetIds = workflowPage.getContent().stream()
                    .map(tb_ApprovalWorkflow::getASSET_ID)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            List<tb_FinancialReport> reports = financialReportService.findAllByAssetNameInOrAssetSerialNumberIn(assetIds);

            Map<String, tb_FinancialReport> reportMap = new HashMap<>();
            for (tb_FinancialReport report : reports) {
                if (report.getAssetName() != null) {
                    reportMap.putIfAbsent(report.getAssetName(), report);
                }
                if (report.getAssetSerialNumber() != null) {
                    reportMap.putIfAbsent(report.getAssetSerialNumber(), report);
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            List<Map<String, Object>> items = workflowPage.getContent().stream().map(workflow -> {
                Map<String, Object> item = new HashMap<>();
                tb_FinancialReport report = reportMap.get(workflow.getASSET_ID());

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
            response.put("totalCount", workflowPage.getTotalElements());
            response.put("currentPage", page);
            response.put("totalPages", workflowPage.getTotalPages());

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Returning {} items for search query '{}'. Took {} ms", items.size(), query, duration);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error searching finance approvals", e);
            return new ResponseEntity<>(Map.of("message", "Error searching approvals: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


}