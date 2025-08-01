package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.entities.*;
import com.telkom.co.ke.almoptics.models.ApprovalWorkflow;
import com.telkom.co.ke.almoptics.models.AuditLog;
import com.telkom.co.ke.almoptics.repository.*;
import com.telkom.co.ke.almoptics.services.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for Asset Synchronization operations
 * Provides API endpoints for sync, status checks, and approval workflows
 */
@RestController
@RequestMapping("/api/asset-sync")
public class AssetSyncController {

    private static final Logger logger = LoggerFactory.getLogger(AssetSyncController.class);

    @Autowired
    private AssetSyncService assetSyncService;

    @Autowired
    private FinancialReportRepo financialReportRepo;

    @Autowired
    private ApprovalWorkflowRepository approvalWorkflowRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Trigger a full synchronization of all assets asynchronously
     */
    @PostMapping("/trigger-full-sync")
    public ResponseEntity<Map<String, Object>> triggerFullSync() {
        logger.info("Manual trigger: Full asset synchronization started");
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            assetSyncService.scheduledAssetSync(); // Delegate to service
            response.put("status", "success");
            response.put("message", "Full asset synchronization completed successfully");
            logger.info("Full asset synchronization completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during full sync: ", e);
            response.put("status", "error");
            response.put("message", "Error during synchronization: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Sync only Active inventory
     */
    @PostMapping("/sync-active")
    public ResponseEntity<Map<String, Object>> syncActive() {
        logger.info("Manual trigger: Active inventory synchronization started");
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            assetSyncService.syncActiveAssetsAsync().join();
            response.put("status", "success");
            response.put("message", "Active inventory synchronization completed");
            logger.info("Active inventory synchronization completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during active sync: ", e);
            response.put("status", "error");
            response.put("message", "Error during active synchronization: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Sync only Passive inventory
     */
    @PostMapping("/sync-passive")
    public ResponseEntity<Map<String, Object>> syncPassive() {
        logger.info("Manual trigger: Passive inventory synchronization started");
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            assetSyncService.syncPassiveAssetsAsync().join();
            response.put("status", "success");
            response.put("message", "Passive inventory synchronization completed");
            logger.info("Passive inventory synchronization completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during passive sync: ", e);
            response.put("status", "error");
            response.put("message", "Error during passive synchronization: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Sync only IT inventory
     */
    @PostMapping("/sync-it")
    public ResponseEntity<Map<String, Object>> syncIt() {
        logger.info("Manual trigger: IT inventory synchronization started");
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            assetSyncService.syncItAssetsAsync().join();
            response.put("status", "success");
            response.put("message", "IT inventory synchronization completed");
            logger.info("IT inventory synchronization completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during IT sync: ", e);
            response.put("status", "error");
            response.put("message", "Error during IT synchronization: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Map financial report asset details to a simplified object
     */
    private Map<String, Object> mapAssetDetails(tb_FinancialReport asset) {
        Map<String, Object> details = new HashMap<>();
        details.put("id", asset.getId());
        details.put("siteId", asset.getSiteId());
        details.put("assetSerialNumber", asset.getAssetSerialNumber());
        details.put("statusFlag", asset.getStatusFlag());
        details.put("financialApprovalStatus", asset.getFinancialApprovalStatus());
        details.put("nodeType", asset.getNodeType());
        details.put("changeDate", asset.getChangeDate());
        details.put("retirementDate", asset.getRetirementDate());
        details.put("netCost", asset.getNetCost());
        details.put("writeOffDate", asset.getWriteOffDate());
        return details;
    }
}