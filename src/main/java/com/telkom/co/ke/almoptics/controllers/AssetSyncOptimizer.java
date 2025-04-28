//package com.telkom.co.ke.almoptics.controllers;
//
//import com.telkom.co.ke.almoptics.entities.*;
//import com.telkom.co.ke.almoptics.repository.*;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.Objects;
//import java.util.concurrent.CompletableFuture;
//import java.util.stream.Collectors;
//
///**
// * Optimized syncing methods extracted into a component for better modularity
// */
//@Component
//public class AssetSyncOptimizer {
//
//    private static final Logger logger = LoggerFactory.getLogger(AssetSyncOptimizer.class);
//    private static final int BATCH_SIZE = 5000;
//
//    @Autowired
//    private ActiveInventoryRepository activeInventoryRepository;
//
//    @Autowired
//    private PassiveInventoryRepository passiveInventoryRepository;
//
//    @Autowired
//    private ItInventoryRepository itInventoryRepository;
//
//    @Autowired
//    private FinancialReportRepo financialReportRepo;
//
//    @Autowired
//    private UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;
//
//    @Autowired
//    private UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;
//
//    @Autowired
//    private UnmappedITInventoryRepository unmappedITInventoryRepository;
//
//    @Autowired
//    private AssetSyncController assetSyncController; // For accessing private methods
//
//    /**
//     * Async method to sync Active assets with optimized batching
//     */
//    @Async("taskExecutor")
//    @Transactional(readOnly = true) // Read-only since we only fetch and delegate processing
//    public CompletableFuture<Void> syncActiveAssetsAsync() {
//        logger.info("Starting async sync for Active assets");
//        long totalRecords = activeInventoryRepository.count();
//        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);
//
//        return CompletableFuture.supplyAsync(() -> {
//            for (int page = 0; page < totalPages; page++) {
//                Pageable pageable = PageRequest.of(page, BATCH_SIZE);
//                List<String> serials = activeInventoryRepository.findAllSerialNumbers(pageable); // Optimized query
//                assetSyncController.processBatchSync(serials, "ACTIVE");
//                logger.debug("Processed Active batch {}/{}", page + 1, totalPages);
//            }
//            logger.info("Completed async sync for Active assets");
//            return null;
//        });
//    }
//
//    /**
//     * Async method to sync Passive assets with optimized batching
//     */
//    @Async("taskExecutor")
//    @Transactional(readOnly = true)
//    public CompletableFuture<Void> syncPassiveAssetsAsync() {
//        logger.info("Starting async sync for Passive assets");
//        long totalRecords = passiveInventoryRepository.count();
//        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);
//
//        return CompletableFuture.supplyAsync(() -> {
//            for (int page = 0; page < totalPages; page++) {
//                Pageable pageable = PageRequest.of(page, BATCH_SIZE);
//                List<String> serials = passiveInventoryRepository.findAllSerials(pageable); // Optimized query
//                assetSyncController.processBatchSync(serials, "PASSIVE");
//                logger.debug("Processed Passive batch {}/{}", page + 1, totalPages);
//            }
//            logger.info("Completed async sync for Passive assets");
//            return null;
//        });
//    }
//
//    /**
//     * Async method to sync IT assets with optimized batching
//     */
//    @Async("taskExecutor")
//    @Transactional(readOnly = true)
//    public CompletableFuture<Void> syncItAssetsAsync() {
//        logger.info("Starting async sync for IT assets");
//        long totalRecords = itInventoryRepository.count();
//        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);
//
//        return CompletableFuture.supplyAsync(() -> {
//            for (int page = 0; page < totalPages; page++) {
//                Pageable pageable = PageRequest.of(page, BATCH_SIZE);
//                List<String> serials = itInventoryRepository.findAllHostSerialNumbers(pageable); // Optimized query
//                assetSyncController.processBatchSync(serials, "IT");
//                logger.debug("Processed IT batch {}/{}", page + 1, totalPages);
//            }
//            logger.info("Completed async sync for IT assets");
//            return null;
//        });
//    }
//
//    /**
//     * Async method to sync missing assets with optimized processing
//     */
//    @Async("taskExecutor")
//    @Transactional
//    public CompletableFuture<Void> syncMissingAssetsAsync() {
//        logger.info("Starting async sync for missing assets");
//        long totalRecords = financialReportRepo.count();
//        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);
//
//        return CompletableFuture.supplyAsync(() -> {
//            for (int page = 0; page < totalPages; page++) {
//                Pageable pageable = PageRequest.of(page, BATCH_SIZE);
//                List<tb_FinancialReport> batch = financialReportRepo.findAll(pageable).getContent();
//                batch.parallelStream().forEach(assetSyncController::processFRAsset); // Parallel processing
//                logger.debug("Processed missing assets batch {}/{}", page + 1, totalPages);
//            }
//            logger.info("Completed async sync for missing assets");
//            return null;
//        });
//    }
//
//    /**
//     * Rebuild unmapped inventories with optimized batching and parallel processing
//     */
//    @Async("taskExecutor")
//    @Transactional
//    public CompletableFuture<Void> rebuildUnmappedInventoriesAsync() {
//        logger.info("Rebuilding unmapped inventories");
//
//        // Clear unmapped tables in one go
//        unmappedActiveInventoryRepository.deleteAllInBatch();
//        unmappedPassiveInventoryRepository.deleteAllInBatch();
//        unmappedITInventoryRepository.deleteAllInBatch();
//
//        return CompletableFuture.allOf(
//                rebuildUnmappedType("ACTIVE", activeInventoryRepository::findAllSerialNumbers),
//                rebuildUnmappedType("PASSIVE", passiveInventoryRepository::findAllSerials),
//                rebuildUnmappedType("IT", itInventoryRepository::findAllHostSerialNumbers)
//        ).thenRun(() -> logger.info("Completed rebuilding unmapped inventories"));
//    }
//
//    /**
//     * Helper method to rebuild unmapped inventory for a specific type
//     */
//    private CompletableFuture<Void> rebuildUnmappedType(String type, SerialNumberFetcher fetcher) {
//        long totalRecords;
//        switch (type) {
//            case "ACTIVE": totalRecords = activeInventoryRepository.count(); break;
//            case "PASSIVE": totalRecords = passiveInventoryRepository.count(); break;
//            case "IT": totalRecords = itInventoryRepository.count(); break;
//            default: throw new IllegalArgumentException("Unknown type: " + type);
//        }
//        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);
//
//        return CompletableFuture.supplyAsync(() -> {
//            for (int page = 0; page < totalPages; page++) {
//                Pageable pageable = PageRequest.of(page, BATCH_SIZE);
//                List<String> serials = fetcher.fetch(pageable);
//                assetSyncController.processBatchForUnmapped(serials, type);
//                logger.debug("Processed unmapped {} batch {}/{}", type, page + 1, totalPages);
//            }
//            return null;
//        });
//    }
//
//    /**
//     * Functional interface for fetching serial numbers
//     */
//    @FunctionalInterface
//    private interface SerialNumberFetcher {
//        List<String> fetch(Pageable pageable);
//    }
//}

