package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.entities.UnmappedActiveInventory;
import com.telkom.co.ke.almoptics.entities.UnmappedITInventory;
import com.telkom.co.ke.almoptics.entities.UnmappedPassiveInventory;
import com.telkom.co.ke.almoptics.services.UnmappedInventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for mapping inventory data and providing access to unmapped inventories
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/inventory-mapping")
public class InventoryMappingController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryMappingController.class);

    @Autowired
    private UnmappedInventoryService unmappedInventoryService;

    /**
     * Map inventory from source to unmapped inventory by serial number or identifier
     */
    @PostMapping("/map/{type}/{identifier}")
    public ResponseEntity<Map<String, Object>> mapInventory(
            @PathVariable String type,
            @PathVariable String identifier,
            @RequestBody Map<String, String> requestBody) {
        logger.info("Mapping inventory of type: {}, identifier: {}", type, identifier);

        String username = requestBody.getOrDefault("username", "SYSTEM");
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            switch (type.toUpperCase()) {
                case "ACTIVE":
                    Optional<UnmappedActiveInventory> activeResult =
                            unmappedInventoryService.mapActiveInventoryBySerialNumber(identifier, username);
                    if (activeResult.isPresent()) {
                        response.put("status", "success");
                        response.put("message", "Active inventory mapped successfully");
                        response.put("data", activeResult.get());
                        return ResponseEntity.ok(response);
                    } else {
                        response.put("status", "error");
                        response.put("message", "Active inventory with identifier " + identifier + " not found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                    }

                case "PASSIVE":
                    Optional<UnmappedPassiveInventory> passiveResult =
                            unmappedInventoryService.mapPassiveInventoryByIdentifier(identifier, username);
                    if (passiveResult.isPresent()) {
                        response.put("status", "success");
                        response.put("message", "Passive inventory mapped successfully");
                        response.put("data", passiveResult.get());
                        return ResponseEntity.ok(response);
                    } else {
                        response.put("status", "error");
                        response.put("message", "Passive inventory with identifier " + identifier + " not found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                    }

                case "IT":
                    Optional<UnmappedITInventory> itResult =
                            unmappedInventoryService.mapITInventoryByIdentifier(identifier, username);
                    if (itResult.isPresent()) {
                        response.put("status", "success");
                        response.put("message", "IT inventory mapped successfully");
                        response.put("data", itResult.get());
                        return ResponseEntity.ok(response);
                    } else {
                        response.put("status", "error");
                        response.put("message", "IT inventory with identifier " + identifier + " not found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                    }

                default:
                    response.put("status", "error");
                    response.put("message", "Invalid inventory type: " + type);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            logger.error("Error mapping inventory: ", e);
            response.put("status", "error");
            response.put("message", "Error mapping inventory: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get unmapped inventory data for a specific type with pagination
     */
    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> getUnmappedInventory(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size, // Default to 100 per page
            @RequestParam(defaultValue = "insertDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        logger.info("Getting unmapped inventory of type: {}, page: {}, size: {}, sortBy: {}, sortDir: {}",
                type, page, size, sortBy, sortDir);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        // Adjust sort field based on inventory type
        String effectiveSortBy = sortBy;
        if ("IT".equalsIgnoreCase(type) && "insertDate".equalsIgnoreCase(sortBy)) {
            effectiveSortBy = "assetInsertDate"; // IT uses assetInsertDate
        } else if ("PASSIVE".equalsIgnoreCase(type) && "insertDate".equalsIgnoreCase(sortBy)) {
            effectiveSortBy = "entryDate"; // Passive uses entryDate
        }

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(effectiveSortBy).descending() : Sort.by(effectiveSortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        try {
            switch (type.toUpperCase()) {
                case "ACTIVE":
                    Page<UnmappedActiveInventory> activePage = unmappedInventoryService.getUnmappedActiveInventory(pageable);
                    response.put("status", "success");
                    response.put("data", activePage.getContent());
                    response.put("currentPage", activePage.getNumber());
                    response.put("totalItems", activePage.getTotalElements());
                    response.put("totalPages", activePage.getTotalPages());
                    return ResponseEntity.ok(response);

                case "PASSIVE":
                    Page<UnmappedPassiveInventory> passivePage = unmappedInventoryService.getUnmappedPassiveInventory(pageable);
                    response.put("status", "success");
                    response.put("data", passivePage.getContent());
                    response.put("currentPage", passivePage.getNumber());
                    response.put("totalItems", passivePage.getTotalElements());
                    response.put("totalPages", passivePage.getTotalPages());
                    return ResponseEntity.ok(response);

                case "IT":
                    Page<UnmappedITInventory> itPage = unmappedInventoryService.getUnmappedITInventory(pageable);
                    response.put("status", "success");
                    response.put("data", itPage.getContent());
                    response.put("currentPage", itPage.getNumber());
                    response.put("totalItems", itPage.getTotalElements());
                    response.put("totalPages", itPage.getTotalPages());
                    return ResponseEntity.ok(response);

                default:
                    response.put("status", "error");
                    response.put("message", "Invalid inventory type: " + type);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            logger.error("Error retrieving unmapped inventory: ", e);
            response.put("status", "error");
            response.put("message", "Error retrieving unmapped inventory: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get details of a specific unmapped inventory item (Placeholder)
     */
    @GetMapping("/{type}/{id}")
    public ResponseEntity<Map<String, Object>> getUnmappedInventoryById(
            @PathVariable String type,
            @PathVariable Integer id) {
        logger.info("Getting unmapped inventory details for type: {}, id: {}", type, id);
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", "error");
        response.put("message", "Not implemented yet");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(response);
    }


    /**
     * Search unmapped inventory by type and query string
     */
    @GetMapping("/search/{type}")
    public ResponseEntity<Map<String, Object>> searchUnmappedInventory(
            @PathVariable String type,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "insertDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        logger.info("Searching unmapped inventory - type: {}, query: {}, page: {}, size: {}, sortBy: {}, sortDir: {}",
                type, query, page, size, sortBy, sortDir);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        // Adjust sort field based on inventory type
        String effectiveSortBy = sortBy;
        if ("IT".equalsIgnoreCase(type) && "insertDate".equalsIgnoreCase(sortBy)) {
            effectiveSortBy = "assetInsertDate";
        } else if ("PASSIVE".equalsIgnoreCase(type) && "insertDate".equalsIgnoreCase(sortBy)) {
            effectiveSortBy = "entryDate";
        }

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(effectiveSortBy).descending() : Sort.by(effectiveSortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        try {
            switch (type.toUpperCase()) {
                case "ACTIVE":
                    Page<UnmappedActiveInventory> activePage = unmappedInventoryService.searchActiveInventory(
                            query, "serialNumber", "assetName", pageable);
                    response.put("status", "success");
                    response.put("data", activePage.getContent());
                    response.put("currentPage", activePage.getNumber());
                    response.put("totalItems", activePage.getTotalElements());
                    response.put("totalPages", activePage.getTotalPages());
                    return ResponseEntity.ok(response);

                case "PASSIVE":
                    Page<UnmappedPassiveInventory> passivePage = unmappedInventoryService.searchPassiveInventory(
                            query, "elementID", "itemBarCode", "serial", pageable);
                    response.put("status", "success");
                    response.put("data", passivePage.getContent());
                    response.put("currentPage", passivePage.getNumber());
                    response.put("totalItems", passivePage.getTotalElements());
                    response.put("totalPages", passivePage.getTotalPages());
                    return ResponseEntity.ok(response);

                case "IT":
                    Page<UnmappedITInventory> itPage = unmappedInventoryService.searchITInventory(
                            query, "elementId", "hostSerialNumber", pageable);
                    response.put("status", "success");
                    response.put("data", itPage.getContent());
                    response.put("currentPage", itPage.getNumber());
                    response.put("totalItems", itPage.getTotalElements());
                    response.put("totalPages", itPage.getTotalPages());
                    return ResponseEntity.ok(response);

                default:
                    response.put("status", "error");
                    response.put("message", "Invalid inventory type: " + type);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            logger.error("Error searching unmapped inventory: ", e);
            response.put("status", "error");
            response.put("message", "Error searching inventory: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}