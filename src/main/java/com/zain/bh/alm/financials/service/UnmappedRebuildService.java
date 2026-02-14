package com.zain.bh.alm.financials.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zain.bh.alm.financials.entity.ITInventory;
import com.zain.bh.alm.financials.entity.Node;
import com.zain.bh.alm.financials.entity.PassiveInventory;
import com.zain.bh.alm.financials.repository.ITInventoryRepository;
import com.zain.bh.alm.financials.repository.NodeRepository;
import com.zain.bh.alm.financials.repository.PassiveInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedActiveInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedITInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedPassiveInventoryRepository;

@Service
public class UnmappedRebuildService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnmappedRebuildService.class);
    private static final int BATCH_SIZE = 500;

    private final NodeRepository activeInventoryRepository;
    private final PassiveInventoryRepository passiveInventoryRepository;
    private final ITInventoryRepository itInventoryRepository;
    private final UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;
    private final UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;
    private final UnmappedITInventoryRepository unmappedITInventoryRepository;
    private final AssetProcessingService assetProcessingService;

    public UnmappedRebuildService(NodeRepository activeInventoryRepository,
            PassiveInventoryRepository passiveInventoryRepository, ITInventoryRepository itInventoryRepository,
            UnmappedActiveInventoryRepository unmappedActiveInventoryRepository,
            UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository,
            UnmappedITInventoryRepository unmappedITInventoryRepository,
            AssetProcessingService assetProcessingService) {
        this.activeInventoryRepository = activeInventoryRepository;
        this.passiveInventoryRepository = passiveInventoryRepository;
        this.itInventoryRepository = itInventoryRepository;
        this.unmappedActiveInventoryRepository = unmappedActiveInventoryRepository;
        this.unmappedPassiveInventoryRepository = unmappedPassiveInventoryRepository;
        this.unmappedITInventoryRepository = unmappedITInventoryRepository;
        this.assetProcessingService = assetProcessingService;
    }

    @Async
    @Transactional
    public CompletableFuture<Void> rebuildUnmappedInventoriesAsync() {
        LOGGER.info("Rebuilding unmapped inventories");
        unmappedActiveInventoryRepository.deleteAll();
        unmappedPassiveInventoryRepository.deleteAll();
        unmappedITInventoryRepository.deleteAll();
        rebuildActive();
        rebuildPassive();
        rebuildIT();
        LOGGER.info("Completed rebuilding unmapped inventories");
        return CompletableFuture.completedFuture(null);
    }

    private void rebuildActive() {
        long totalActive = activeInventoryRepository.count();
        int totalActivePages = (int) Math.ceil((double) totalActive / BATCH_SIZE);
        for (int page = 0; page < totalActivePages; page++) {
            List<Node> batch = activeInventoryRepository.findAll(PageRequest.of(page, BATCH_SIZE)).getContent();
            List<String> serials = batch.stream().map(Node::getSerialNumber).filter(s -> s != null).collect(Collectors.toList());
            assetProcessingService.processBatchForUnmapped(serials, "ACTIVE");
        }
    }

    private void rebuildPassive() {
        long totalPassive = passiveInventoryRepository.count();
        int totalPassivePages = (int) Math.ceil((double) totalPassive / BATCH_SIZE);
        for (int page = 0; page < totalPassivePages; page++) {
            List<PassiveInventory> batch = passiveInventoryRepository.findAll(PageRequest.of(page, BATCH_SIZE)).getContent();
            List<String> serials = batch.stream().map(PassiveInventory::getSerial).filter(s -> s != null).collect(Collectors.toList());
            assetProcessingService.processBatchForUnmapped(serials, "PASSIVE");
        }
    }

    private void rebuildIT() {
        long totalIt = itInventoryRepository.count();
        int totalItPages = (int) Math.ceil((double) totalIt / BATCH_SIZE);
        for (int page = 0; page < totalItPages; page++) {
            List<ITInventory> batch = itInventoryRepository.findAll(PageRequest.of(page, BATCH_SIZE)).getContent();
            List<String> serials = batch.stream().map(ITInventory::getHostSerialNumber).filter(s -> s != null).collect(Collectors.toList());
            assetProcessingService.processBatchForUnmapped(serials, "IT");
        }
    }
}
