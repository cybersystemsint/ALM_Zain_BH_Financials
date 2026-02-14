package com.zain.bh.alm.financials.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zain.bh.alm.financials.entity.WFFinancialApprovalRequest;
import com.zain.bh.alm.financials.repository.WFFinancialApprovalRequestRepository;
import com.zain.bh.alm.financials.service.AssetStatusService;
import com.zain.bh.alm.financials.service.AssetSyncBusinessService;
import com.zain.bh.alm.financials.service.UnmappedAssetRetrievalService;

@RestController
@RequestMapping("/api/asset-sync")
public class AssetSyncController {

	private static final Logger LOGGER = LoggerFactory.getLogger(AssetSyncController.class);

	private final AssetSyncBusinessService assetSyncBusinessService;
	private final AssetStatusService assetStatusService;
	private final UnmappedAssetRetrievalService unmappedAssetRetrievalService;
	private final WFFinancialApprovalRequestRepository approvalWorkflowRepository;

	public AssetSyncController(AssetSyncBusinessService assetSyncBusinessService,
			AssetStatusService assetStatusService, UnmappedAssetRetrievalService unmappedAssetRetrievalService,
			WFFinancialApprovalRequestRepository approvalWorkflowRepository) {
		this.assetSyncBusinessService = assetSyncBusinessService;
		this.assetStatusService = assetStatusService;
		this.unmappedAssetRetrievalService = unmappedAssetRetrievalService;
		this.approvalWorkflowRepository = approvalWorkflowRepository;
	}

	@PostMapping("/trigger-full-sync")
	public ResponseEntity<Map<String, Object>> triggerFullSync() {
		Map<String, Object> response = new HashMap<>();
		response.put("timestamp", LocalDateTime.now());

		try {
			CompletableFuture<Void> missingSync = assetSyncBusinessService.syncMissingAssetsAsync();
			CompletableFuture<Void> activeSync = assetSyncBusinessService.syncActiveAssetsAsync();
			CompletableFuture<Void> passiveSync = assetSyncBusinessService.syncPassiveAssetsAsync();
			CompletableFuture<Void> itSync = assetSyncBusinessService.syncItAssetsAsync();
			CompletableFuture<Void> unmappedSync = assetSyncBusinessService.rebuildUnmappedInventoriesAsync();

			CompletableFuture.allOf(missingSync, activeSync, passiveSync, itSync, unmappedSync).join();

			response.put("status", "success");
			response.put("message", "Full asset synchronization completed successfully");
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			LOGGER.error("Error during full sync: {}", e.getMessage());
			response.put("status", "error");
			response.put("message", "Error during synchronization: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	@PostMapping("/sync-active")
	public ResponseEntity<Map<String, Object>> syncActive() {
		Map<String, Object> response = new HashMap<>();
		response.put("timestamp", LocalDateTime.now());

		try {
			assetSyncBusinessService.syncActiveAssetsAsync().join();
			response.put("status", "success");
			response.put("message", "Active inventory synchronization completed");
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			LOGGER.error("Error during active sync: {}", e.getMessage());
			response.put("status", "error");
			response.put("message", "Error during active synchronization: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	@PostMapping("/sync-passive")
	public ResponseEntity<Map<String, Object>> syncPassive() {
		Map<String, Object> response = new HashMap<>();
		response.put("timestamp", LocalDateTime.now());

		try {
			assetSyncBusinessService.syncPassiveAssetsAsync().join();
			response.put("status", "success");
			response.put("message", "Passive inventory synchronization completed");
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			LOGGER.error("Error during passive sync: {}", e.getMessage());
			response.put("status", "error");
			response.put("message", "Error during passive synchronization: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	@PostMapping("/sync-it")
	public ResponseEntity<Map<String, Object>> syncIt() {
		Map<String, Object> response = new HashMap<>();
		response.put("timestamp", LocalDateTime.now());

		try {
			assetSyncBusinessService.syncItAssetsAsync().join();
			response.put("status", "success");
			response.put("message", "IT inventory synchronization completed");
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			LOGGER.error("Error during IT sync: {}", e.getMessage());
			response.put("status", "error");
			response.put("message", "Error during IT synchronization: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	@PostMapping("/sync-asset/{identifier}")
	public ResponseEntity<Map<String, Object>> syncSpecificAsset(@PathVariable String identifier) {
		try {
			Map<String, Object> response = assetStatusService.syncSpecificAsset(identifier);
			response.put("timestamp", LocalDateTime.now());
			HttpStatus status = "warning".equals(response.get("status")) ? HttpStatus.NOT_FOUND : HttpStatus.OK;
			return ResponseEntity.status(status).body(response);
		} catch (Exception e) {
			LOGGER.error("Error during asset synchronization: {}", e.getMessage());
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("timestamp", LocalDateTime.now());
			errorResponse.put("status", "error");
			errorResponse.put("message", "An error occurred during asset synchronization: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
		}
	}

	@GetMapping("/check-status/{identifier}")
	public ResponseEntity<Map<String, Object>> checkAssetStatus(@PathVariable String identifier) {
		try {
			Map<String, Object> response = assetStatusService.checkAssetStatus(identifier);
			response.put("timestamp", LocalDateTime.now());
			HttpStatus status = "error".equals(response.get("status")) ? HttpStatus.NOT_FOUND : HttpStatus.OK;
			return ResponseEntity.status(status).body(response);
		} catch (Exception e) {
			LOGGER.error("Error checking asset status: {}", e.getMessage());
			Map<String, Object> response = new HashMap<>();
			response.put("status", "error");
			response.put("message", "Error checking asset status: " + e.getMessage());
			response.put("timestamp", LocalDateTime.now());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	@GetMapping("/unmapped-assets")
	public ResponseEntity<Map<String, Object>> getUnmappedAssets(@RequestParam(defaultValue = "ALL") String type,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		try {
			Map<String, Object> response = unmappedAssetRetrievalService.getUnmappedAssets(type,
					PageRequest.of(page, size, Sort.by("insertDate").descending()));
			response.put("status", "success");
			response.put("timestamp", LocalDateTime.now());
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			LOGGER.error("Error retrieving unmapped assets: {}", e.getMessage());
			Map<String, Object> response = new HashMap<>();
			response.put("status", "error");
			response.put("message", "Error retrieving unmapped assets: " + e.getMessage());
			response.put("timestamp", LocalDateTime.now());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	@GetMapping("/pending-approvals")
	public ResponseEntity<Map<String, Object>> getPendingApprovals(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		try {
			Map<String, Object> response = new HashMap<>();
			Page<WFFinancialApprovalRequest> approvalsPage = approvalWorkflowRepository.findByUpdatedStatus("PENDING",
					PageRequest.of(page, size, Sort.by("insertDate").descending()));

			response.put("status", "success");
			response.put("timestamp", LocalDateTime.now());
			response.put("approvals", approvalsPage.getContent());
			response.put("totalItems", approvalsPage.getTotalElements());
			response.put("totalPages", approvalsPage.getTotalPages());
			response.put("currentPage", approvalsPage.getNumber());
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			LOGGER.error("Error retrieving pending approvals: {}", e.getMessage());
			Map<String, Object> response = new HashMap<>();
			response.put("status", "error");
			response.put("message", "Error retrieving pending approvals: " + e.getMessage());
			response.put("timestamp", LocalDateTime.now());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
}
