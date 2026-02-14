package com.zain.bh.alm.financials.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.repository.FinancialReportRepository;

@Service
public class MissingAssetSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MissingAssetSyncService.class);
    private static final int BATCH_SIZE = 500;

    private final FinancialReportRepository financialReportRepository;
    private final AssetProcessingService assetProcessingService;

    public MissingAssetSyncService(FinancialReportRepository financialReportRepository,
            AssetProcessingService assetProcessingService) {
        this.financialReportRepository = financialReportRepository;
        this.assetProcessingService = assetProcessingService;
    }

    @Async
    @Transactional
    public CompletableFuture<Void> syncMissingAssetsAsync() {
        LOGGER.info("Starting async sync for missing assets");
        long totalRecords = financialReportRepository.count();
        int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

        for (int page = 0; page < totalPages; page++) {
            List<FinancialReport> batch = financialReportRepository.findAll(PageRequest.of(page, BATCH_SIZE)).getContent();
            batch.forEach(assetProcessingService::processFRAsset);
        }
        LOGGER.info("Completed async sync for missing assets");
        return CompletableFuture.completedFuture(null);
    }
}
