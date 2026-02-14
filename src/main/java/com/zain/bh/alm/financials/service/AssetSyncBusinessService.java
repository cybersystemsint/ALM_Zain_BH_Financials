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

@Service
public class AssetSyncBusinessService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AssetSyncBusinessService.class);
	private static final int BATCH_SIZE = 500;

	private final NodeRepository activeInventoryRepository;
	private final PassiveInventoryRepository passiveInventoryRepository;
	private final ITInventoryRepository itInventoryRepository;
	private final AssetProcessingService assetProcessingService;
	private final MissingAssetSyncService missingAssetSyncService;
	private final UnmappedRebuildService unmappedRebuildService;

	public AssetSyncBusinessService(NodeRepository activeInventoryRepository,
			PassiveInventoryRepository passiveInventoryRepository, ITInventoryRepository itInventoryRepository,
			AssetProcessingService assetProcessingService, MissingAssetSyncService missingAssetSyncService,
			UnmappedRebuildService unmappedRebuildService) {
		this.activeInventoryRepository = activeInventoryRepository;
		this.passiveInventoryRepository = passiveInventoryRepository;
		this.itInventoryRepository = itInventoryRepository;
		this.assetProcessingService = assetProcessingService;
		this.missingAssetSyncService = missingAssetSyncService;
		this.unmappedRebuildService = unmappedRebuildService;
	}

	@Async
	@Transactional
	public CompletableFuture<Void> syncActiveAssetsAsync() {
		LOGGER.info("Starting async sync for Active assets");
		long totalRecords = activeInventoryRepository.count();
		int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

		for (int page = 0; page < totalPages; page++) {
			List<Node> batch = activeInventoryRepository.findAll(PageRequest.of(page, BATCH_SIZE)).getContent();
			List<String> serials = batch.stream().map(Node::getSerialNumber).filter(s -> s != null)
					.collect(Collectors.toList());
			assetProcessingService.processBatchSync(serials, "ACTIVE");
		}
		LOGGER.info("Completed async sync for Active assets");
		return CompletableFuture.completedFuture(null);
	}

	@Async
	@Transactional
	public CompletableFuture<Void> syncPassiveAssetsAsync() {
		LOGGER.info("Starting async sync for Passive assets");
		long totalRecords = passiveInventoryRepository.count();
		int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

		for (int page = 0; page < totalPages; page++) {
			List<PassiveInventory> batch = passiveInventoryRepository.findAll(PageRequest.of(page, BATCH_SIZE))
					.getContent();
			List<String> serials = batch.stream().map(PassiveInventory::getSerial).filter(s -> s != null)
					.collect(Collectors.toList());
			assetProcessingService.processBatchSync(serials, "PASSIVE");
		}
		LOGGER.info("Completed async sync for Passive assets");
		return CompletableFuture.completedFuture(null);
	}

	@Async
	@Transactional
	public CompletableFuture<Void> syncItAssetsAsync() {
		LOGGER.info("Starting async sync for IT assets");
		long totalRecords = itInventoryRepository.count();
		int totalPages = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

		for (int page = 0; page < totalPages; page++) {
			List<ITInventory> batch = itInventoryRepository.findAll(PageRequest.of(page, BATCH_SIZE)).getContent();
			List<String> serials = batch.stream().map(ITInventory::getHostSerialNumber).filter(s -> s != null)
					.collect(Collectors.toList());
			assetProcessingService.processBatchSync(serials, "IT");
		}
		LOGGER.info("Completed async sync for IT assets");
		return CompletableFuture.completedFuture(null);
	}

	public CompletableFuture<Void> syncMissingAssetsAsync() {
		return missingAssetSyncService.syncMissingAssetsAsync();
	}

	public CompletableFuture<Void> rebuildUnmappedInventoriesAsync() {
		return unmappedRebuildService.rebuildUnmappedInventoriesAsync();
	}
}
