package com.zain.bh.alm.financials.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zain.bh.alm.financials.service.AssetSyncService;
import com.zain.bh.alm.financials.service.MissingAssetCheckService;
import com.zain.bh.alm.financials.service.UnmappedInventoryService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class FinancialSyncScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialSyncScheduler.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AssetSyncService assetSyncService;
    private final UnmappedInventoryService unmappedInventoryService;
    private final MissingAssetCheckService missingAssetCheckService;

    public FinancialSyncScheduler(AssetSyncService assetSyncService,
                                  UnmappedInventoryService unmappedInventoryService,
                                  MissingAssetCheckService missingAssetCheckService) {
        this.assetSyncService = assetSyncService;
        this.unmappedInventoryService = unmappedInventoryService;
        this.missingAssetCheckService = missingAssetCheckService;
    }

    @Scheduled(cron = "0 1 0 * * ?")
    public void runDailyFinancialSync() {
        LocalDateTime startTime = LocalDateTime.now();
        LOGGER.info("Starting daily financial sync process at {}", FORMATTER.format(startTime));

        try {
            syncInventoryWithFinancialReport();
            processUnmappedInventory();
            checkForMissingAssets();

            LocalDateTime endTime = LocalDateTime.now();
            long durationSeconds = Duration.between(startTime, endTime).getSeconds();
            LOGGER.info("Completed daily financial sync process at {}. Duration: {} seconds",
                    FORMATTER.format(endTime), durationSeconds);
        } catch (Exception e) {
            LOGGER.error("Error during daily financial sync process: {}", e.getMessage(), e);
        }
    }

    private void syncInventoryWithFinancialReport() {
        LOGGER.info("Starting inventory to Financial Report synchronization");
        assetSyncService.syncActiveAssets();
        assetSyncService.syncPassiveAssets();
        assetSyncService.syncItAssets();
        LOGGER.info("Completed inventory to Financial Report synchronization");
    }

    private void processUnmappedInventory() {
        LOGGER.info("Starting unmapped inventory processing");
        unmappedInventoryService.processUnmappedActiveInventory();
        unmappedInventoryService.processUnmappedPassiveInventory();
        unmappedInventoryService.processUnmappedItInventory();
        LOGGER.info("Completed unmapped inventory processing");
    }

    private void checkForMissingAssets() {
        LOGGER.info("Starting missing asset check");
        missingAssetCheckService.checkForMissingAssets();
        missingAssetCheckService.updateMissingAssetStatus();
        LOGGER.info("Completed missing asset check");
    }

    public String triggerManualSync() {
        LocalDateTime startTime = LocalDateTime.now();
        LOGGER.info("Starting manually triggered financial sync process at {}", FORMATTER.format(startTime));

        try {
            syncInventoryWithFinancialReport();
            processUnmappedInventory();
            checkForMissingAssets();

            LocalDateTime endTime = LocalDateTime.now();
            long durationSeconds = Duration.between(startTime, endTime).getSeconds();
            String summary = String.format("Financial sync completed successfully at %s. Duration: %d seconds",
                    FORMATTER.format(endTime), durationSeconds);
            LOGGER.info(summary);
            return summary;
        } catch (Exception e) {
            String errorMessage = "Error during manually triggered financial sync: " + e.getMessage();
            LOGGER.error(errorMessage, e);
            return errorMessage;
        }
    }
}
