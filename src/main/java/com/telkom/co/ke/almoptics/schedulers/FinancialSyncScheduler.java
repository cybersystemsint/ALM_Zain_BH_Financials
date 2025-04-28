package com.telkom.co.ke.almoptics.schedulers;

import com.telkom.co.ke.almoptics.services.AssetSyncService;
import com.telkom.co.ke.almoptics.services.UnmappedInventoryService;
import com.telkom.co.ke.almoptics.services.MissingAssetCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduler component that coordinates all financial asset synchronization processes.
 * Runs daily to ensure assets are properly tracked between inventory and financial systems.
 */
@Component
public class FinancialSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(FinancialSyncScheduler.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private AssetSyncService assetSyncService;

    @Autowired
    private UnmappedInventoryService unmappedInventoryService;

    @Autowired
    private MissingAssetCheckService missingAssetCheckService;

    /**
     * Main scheduled job that coordinates all asset synchronization processes.
     * Runs daily at 12:01 AM
     */
    @Scheduled(cron = "0 1 0 * * ?")
    public void runDailyFinancialSync() {
        LocalDateTime startTime = LocalDateTime.now();
        logger.info("Starting daily financial sync process at {}", formatter.format(startTime));

        try {
            // Step 1: Sync assets in inventory with Financial Report
            syncInventoryWithFinancialReport();

            // Step 2: Process unmapped inventory (assets in inventory but not in FR)
            processUnmappedInventory();

            // Step 3: Check for missing assets (assets in FR but not in inventory)
            checkForMissingAssets();

            LocalDateTime endTime = LocalDateTime.now();
            long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
            logger.info("Completed daily financial sync process at {}. Duration: {} seconds",
                    formatter.format(endTime), durationSeconds);

        } catch (Exception e) {
            logger.error("Error during daily financial sync process", e);
            // Consider sending an alert notification here
        }
    }

    /**
     * Syncs all inventory assets with Financial Report
     * Updates status_flag based on last_checked date
     */
    private void syncInventoryWithFinancialReport() {
        logger.info("Starting inventory to Financial Report synchronization");

        try {
            // Sync active assets
            assetSyncService.syncActiveAssets();

            // Sync passive assets
            assetSyncService.syncPassiveAssets();

            // Sync IT assets
            assetSyncService.syncItAssets();

            logger.info("Completed inventory to Financial Report synchronization");
        } catch (Exception e) {
            logger.error("Error during inventory to Financial Report sync", e);
            throw e; // Rethrow to abort the entire process
        }
    }

    /**
     * Processes unmapped inventory
     * Checks if assets in unmapped inventory now appear in Financial Report
     */
    private void processUnmappedInventory() {
        logger.info("Starting unmapped inventory processing");

        try {
            // Process unmapped active inventory
            unmappedInventoryService.processUnmappedActiveInventory();

            // Process unmapped passive inventory
            unmappedInventoryService.processUnmappedPassiveInventory();

            // Process unmapped IT inventory
            unmappedInventoryService.processUnmappedItInventory();  // This needs a corresponding method

            logger.info("Completed unmapped inventory processing");
        } catch (Exception e) {
            logger.error("Error during unmapped inventory processing", e);
            throw e; // Rethrow to abort the entire process
        }
    }


    /**
     * Checks for assets missing from inventory but present in Financial Report
     * Updates status after grace period and triggers notifications
     */
    private void checkForMissingAssets() {
        logger.info("Starting missing asset check");

        try {
            // Check for newly missing assets
            missingAssetCheckService.checkForMissingAssets();

            // Update status of assets missing beyond grace period
            missingAssetCheckService.updateMissingAssetStatus();

            logger.info("Completed missing asset check");
        } catch (Exception e) {
            logger.error("Error during missing asset check", e);
            throw e; // Rethrow to abort the entire process
        }
    }

    /**
     * Manual trigger method for running the sync process on demand
     * Can be exposed via REST API or management interface
     *
     * @return Summary of the sync process
     */
    public String triggerManualSync() {
        LocalDateTime startTime = LocalDateTime.now();
        logger.info("Starting manually triggered financial sync process at {}", formatter.format(startTime));

        try {
            syncInventoryWithFinancialReport();
            processUnmappedInventory();
            checkForMissingAssets();

            LocalDateTime endTime = LocalDateTime.now();
            long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();

            String summary = String.format(
                    "Financial sync completed successfully at %s. Duration: %d seconds",
                    formatter.format(endTime), durationSeconds
            );

            logger.info(summary);
            return summary;

        } catch (Exception e) {
            String errorMessage = "Error during manually triggered financial sync: " + e.getMessage();
            logger.error(errorMessage, e);
            return errorMessage;
        }
    }
}