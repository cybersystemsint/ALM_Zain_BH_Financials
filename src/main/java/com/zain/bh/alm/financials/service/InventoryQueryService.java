package com.zain.bh.alm.financials.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.UnmappedActiveInventory;
import com.zain.bh.alm.financials.entity.UnmappedITInventory;
import com.zain.bh.alm.financials.entity.UnmappedPassiveInventory;

@Service
public class InventoryQueryService {

    private final UnmappedInventoryService unmappedInventoryService;

    public InventoryQueryService(UnmappedInventoryService unmappedInventoryService) {
        this.unmappedInventoryService = unmappedInventoryService;
    }

    public Map<String, Object> getUnmappedInventory(String type, int page, int size, String sortBy, String sortDir) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        String effectiveSortBy = adjustSortField(type, sortBy);
        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(effectiveSortBy).descending() : Sort.by(effectiveSortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        switch (type.toUpperCase()) {
            case "ACTIVE":
                Page<UnmappedActiveInventory> activePage = unmappedInventoryService.getUnmappedActiveInventory(pageable);
                buildPageResponse(response, activePage);
                break;

            case "PASSIVE":
                Page<UnmappedPassiveInventory> passivePage = unmappedInventoryService.getUnmappedPassiveInventory(pageable);
                buildPageResponse(response, passivePage);
                break;

            case "IT":
                Page<UnmappedITInventory> itPage = unmappedInventoryService.getUnmappedITInventory(pageable);
                buildPageResponse(response, itPage);
                break;

            default:
                response.put("status", "BAD_REQUEST");
                response.put("message", "Invalid inventory type: " + type);
                return response;
        }

        response.put("status", "success");
        return response;
    }

    public Map<String, Object> searchUnmappedInventory(String type, String query, int page, int size, String sortBy, String sortDir) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        String effectiveSortBy = adjustSortField(type, sortBy);
        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(effectiveSortBy).descending() : Sort.by(effectiveSortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        switch (type.toUpperCase()) {
            case "ACTIVE":
                Page<UnmappedActiveInventory> activePage = unmappedInventoryService.searchActiveInventory(
                        query, "serialNumber", "assetName", pageable);
                buildPageResponse(response, activePage);
                break;

            case "PASSIVE":
                Page<UnmappedPassiveInventory> passivePage = unmappedInventoryService.searchPassiveInventory(
                        query, "elementID", "itemBarCode", "serial", pageable);
                buildPageResponse(response, passivePage);
                break;

            case "IT":
                Page<UnmappedITInventory> itPage = unmappedInventoryService.searchITInventory(
                        query, "elementId", "hostSerialNumber", pageable);
                buildPageResponse(response, itPage);
                break;

            default:
                response.put("status", "BAD_REQUEST");
                response.put("message", "Invalid inventory type: " + type);
                return response;
        }

        response.put("status", "success");
        return response;
    }

    private String adjustSortField(String type, String sortBy) {
        if ("IT".equalsIgnoreCase(type) && "insertDate".equalsIgnoreCase(sortBy)) {
            return "assetInsertDate";
        } else if ("PASSIVE".equalsIgnoreCase(type) && "insertDate".equalsIgnoreCase(sortBy)) {
            return "entryDate";
        }
        return sortBy;
    }

    private void buildPageResponse(Map<String, Object> response, Page<?> page) {
        response.put("data", page.getContent());
        response.put("currentPage", page.getNumber());
        response.put("totalItems", page.getTotalElements());
        response.put("totalPages", page.getTotalPages());
    }
}
