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

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
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


    @Autowired(required = false)
    private SyncOrchestratorService syncOrchestratorService;

    /**
     * Trigger a full synchronization of all assets.
     *
     * <p>This now routes through {@link SyncOrchestratorService} when
     * available — that path runs the entire cycle as set-based bulk SQL
     * (~12 queries total) instead of the legacy per-row loop (which fired
     * 3-7 queries per FR record). The legacy {@code AssetSyncService}
     * fan-out is kept as a fallback for environments where the
     * orchestrator bean isn't wired (graceful degradation).</p>
     */
    @PostMapping("/trigger-full-sync")
    public ResponseEntity<Map<String, Object>> triggerFullSync(Principal principal) {
        String user = principal != null ? principal.getName() : "anonymous";
        logger.info("Manual trigger: Full asset synchronization started by {}", user);
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            if (syncOrchestratorService != null) {
                SyncOrchestratorService.RunSummary summary =
                        syncOrchestratorService.runFullCycle("api:" + user);
                response.put("status", summary.failure == null ? "success" : "error");
                response.put("message", "Full asset synchronization completed via orchestrator");
                response.put("triggeredBy", summary.getTriggeredBy());
                response.put("durationMs", summary.durationMillis());
                response.put("stages", summary.getStages());
                if (summary.failure != null) {
                    response.put("error", summary.getFailureMessage());
                }
                return ResponseEntity.ok(response);
            }
            // Legacy fallback
            assetSyncService.scheduledAssetSync();
            response.put("status", "success");
            response.put("message", "Full asset synchronization completed (legacy path)");
            logger.info("Full asset synchronization completed (legacy)");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during full sync: ", e);
            response.put("status", "error");
            response.put("message", "Error during synchronization: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ==================================================================
    // Orchestrator-aware endpoints (status, history, control)
    // ==================================================================

    /**
     * Snapshot of the orchestrator's current state — paused?, running?,
     * last run timestamp, last run duration, last run status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        if (syncOrchestratorService == null) {
            body.put("orchestrator", "not-installed");
            return ResponseEntity.ok(body);
        }
        body.put("orchestrator", syncOrchestratorService.getStatus());
        return ResponseEntity.ok(body);
    }

    /**
     * Recent run history (newest first). Each entry contains per-stage
     * timing &amp; row-count data.
     *
     * @param limit max records to return; 0 means all (default 10)
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(defaultValue = "10") int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        if (syncOrchestratorService == null) {
            body.put("history", java.util.Collections.emptyList());
            body.put("note", "Sync orchestrator not installed");
            return ResponseEntity.ok(body);
        }
        body.put("history", syncOrchestratorService.getHistory(limit));
        return ResponseEntity.ok(body);
    }

    /**
     * Pause future scheduled and manual runs. Useful when doing a bulk
     * data import and you want to suppress sync interference.
     */
    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pauseSync(Principal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (syncOrchestratorService == null) {
            body.put("status", "error");
            body.put("message", "Sync orchestrator not installed");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        syncOrchestratorService.pause();
        body.put("status", "paused");
        body.put("by", principal != null ? principal.getName() : "anonymous");
        body.put("at", LocalDateTime.now());
        logger.info("Sync orchestrator paused by {}", principal != null ? principal.getName() : "anonymous");
        return ResponseEntity.ok(body);
    }

    /**
     * Resume scheduled and manual runs after a {@code /pause}.
     */
    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resumeSync(Principal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (syncOrchestratorService == null) {
            body.put("status", "error");
            body.put("message", "Sync orchestrator not installed");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        syncOrchestratorService.resume();
        body.put("status", "resumed");
        body.put("by", principal != null ? principal.getName() : "anonymous");
        body.put("at", LocalDateTime.now());
        logger.info("Sync orchestrator resumed by {}", principal != null ? principal.getName() : "anonymous");
        return ResponseEntity.ok(body);
    }

    /**
     * Run a single stage of the cycle on demand. Useful for one-off
     * corrections (e.g., after a manual data fix you can re-run just
     * {@code unmapped-prune}).
     *
     * <p>Valid stages: {@code promote}, {@code recover},
     * {@code mark-missing}, {@code decommission},
     * {@code unmapped-add}, {@code unmapped-prune}.</p>
     */
    @PostMapping("/run-stage/{stage}")
    public ResponseEntity<Map<String, Object>> runStage(
            @PathVariable String stage,
            Principal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        if (syncOrchestratorService == null) {
            body.put("status", "error");
            body.put("message", "Sync orchestrator not installed");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        try {
            String user = principal != null ? principal.getName() : "anonymous";
            SyncOrchestratorService.RunSummary summary =
                    syncOrchestratorService.runSingleStage(stage, "api:" + user);
            body.put("status", summary.failure == null ? "success" : "error");
            body.put("durationMs", summary.durationMillis());
            body.put("stages", summary.getStages());
            if (summary.failure != null) body.put("error", summary.getFailureMessage());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException iae) {
            body.put("status", "error");
            body.put("message", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        } catch (Exception e) {
            logger.error("Error running stage {}", stage, e);
            body.put("status", "error");
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
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