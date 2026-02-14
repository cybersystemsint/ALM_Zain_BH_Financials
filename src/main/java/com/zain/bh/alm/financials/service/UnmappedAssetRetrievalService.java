package com.zain.bh.alm.financials.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.UnmappedActiveInventory;
import com.zain.bh.alm.financials.entity.UnmappedITInventory;
import com.zain.bh.alm.financials.entity.UnmappedPassiveInventory;
import com.zain.bh.alm.financials.repository.UnmappedActiveInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedITInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedPassiveInventoryRepository;

@Service
public class UnmappedAssetRetrievalService {

    private final UnmappedActiveInventoryRepository unmappedActiveInventoryRepository;
    private final UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository;
    private final UnmappedITInventoryRepository unmappedITInventoryRepository;

    public UnmappedAssetRetrievalService(UnmappedActiveInventoryRepository unmappedActiveInventoryRepository,
            UnmappedPassiveInventoryRepository unmappedPassiveInventoryRepository,
            UnmappedITInventoryRepository unmappedITInventoryRepository) {
        this.unmappedActiveInventoryRepository = unmappedActiveInventoryRepository;
        this.unmappedPassiveInventoryRepository = unmappedPassiveInventoryRepository;
        this.unmappedITInventoryRepository = unmappedITInventoryRepository;
    }

    public Map<String, Object> getUnmappedAssets(String type, Pageable pageable) {
        Map<String, Object> response = new HashMap<>();

        switch (type.toUpperCase()) {
        case "ACTIVE":
            Page<UnmappedActiveInventory> activeAssets = unmappedActiveInventoryRepository.findAll(pageable);
            response.put("assets", activeAssets.getContent());
            response.put("type", "ACTIVE");
            response.put("totalItems", activeAssets.getTotalElements());
            response.put("totalPages", activeAssets.getTotalPages());
            response.put("currentPage", activeAssets.getNumber());
            break;

        case "PASSIVE":
            Page<UnmappedPassiveInventory> passiveAssets = unmappedPassiveInventoryRepository.findAll(pageable);
            response.put("assets", passiveAssets.getContent());
            response.put("type", "PASSIVE");
            response.put("totalItems", passiveAssets.getTotalElements());
            response.put("totalPages", passiveAssets.getTotalPages());
            response.put("currentPage", passiveAssets.getNumber());
            break;

        case "IT":
            Page<UnmappedITInventory> itAssets = unmappedITInventoryRepository.findAll(pageable);
            response.put("assets", itAssets.getContent());
            response.put("type", "IT");
            response.put("totalItems", itAssets.getTotalElements());
            response.put("totalPages", itAssets.getTotalPages());
            response.put("currentPage", itAssets.getNumber());
            break;

        case "ALL":
        default:
            return getAllUnmappedAssets(pageable);
        }

        return response;
    }

    private Map<String, Object> getAllUnmappedAssets(Pageable pageable) {
        Map<String, Object> response = new HashMap<>();
        List<Object> combinedAssets = new ArrayList<>();

        Page<UnmappedActiveInventory> active = unmappedActiveInventoryRepository.findAll(pageable);
        active.getContent().forEach(asset -> {
            Map<String, Object> assetMap = new HashMap<>();
            assetMap.put("id", asset.getId());
            assetMap.put("serialNumber", asset.getSerialNumber());
            assetMap.put("assetType", "ACTIVE");
            assetMap.put("discoveredDate", asset.getInsertDate());
            combinedAssets.add(assetMap);
        });

        Page<UnmappedPassiveInventory> passive = unmappedPassiveInventoryRepository.findAll(pageable);
        passive.getContent().forEach(asset -> {
            Map<String, Object> assetMap = new HashMap<>();
            assetMap.put("id", asset.getObjectId());
            assetMap.put("objectId", asset.getObjectId());
            assetMap.put("serialNumber", asset.getSerial());
            assetMap.put("discoveredDate", asset.getEntryDate());
            assetMap.put("assetType", "PASSIVE");
            combinedAssets.add(assetMap);
        });

        Page<UnmappedITInventory> it = unmappedITInventoryRepository.findAll(pageable);
        it.getContent().forEach(asset -> {
            Map<String, Object> assetMap = new HashMap<>();
            assetMap.put("id", asset.getElementId());
            assetMap.put("objectId", asset.getElementId());
            assetMap.put("serialNumber", asset.getHostSerialNumber());
            assetMap.put("discoveredDate", asset.getAssetInsertDate());
            assetMap.put("assetType", "IT");
            combinedAssets.add(assetMap);
        });

        combinedAssets.sort((a, b) -> {
            Date dateA = (Date) ((Map<String, Object>) a).get("discoveredDate");
            Date dateB = (Date) ((Map<String, Object>) b).get("discoveredDate");
            return dateB.compareTo(dateA);
        });

        response.put("assets", combinedAssets);
        response.put("type", "ALL");

        long totalActive = unmappedActiveInventoryRepository.count();
        long totalPassive = unmappedPassiveInventoryRepository.count();
        long totalIt = unmappedITInventoryRepository.count();
        long totalItems = totalActive + totalPassive + totalIt;

        response.put("totalItems", totalItems);
        response.put("totalActive", totalActive);
        response.put("totalPassive", totalPassive);
        response.put("totalIt", totalIt);
        response.put("currentPage", pageable.getPageNumber());
        response.put("totalPages", (int) Math.ceil(totalItems / (double) pageable.getPageSize()));

        return response;
    }
}
