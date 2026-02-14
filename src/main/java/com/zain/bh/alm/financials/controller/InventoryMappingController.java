package com.zain.bh.alm.financials.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zain.bh.alm.financials.service.InventoryMappingBusinessService;
import com.zain.bh.alm.financials.service.InventoryQueryService;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/inventory-mapping")
public class InventoryMappingController {

    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryMappingController.class);

    private final InventoryMappingBusinessService mappingService;
    private final InventoryQueryService queryService;

    public InventoryMappingController(InventoryMappingBusinessService mappingService,
                                       InventoryQueryService queryService) {
        this.mappingService = mappingService;
        this.queryService = queryService;
    }

    @PostMapping("/map/{type}/{identifier}")
    public ResponseEntity<Map<String, Object>> mapInventory(
            @PathVariable String type,
            @PathVariable String identifier,
            @RequestBody Map<String, String> requestBody) {
        LOGGER.info("Mapping inventory of type: {}, identifier: {}", type, identifier);

        try {
            String username = requestBody.getOrDefault("username", "SYSTEM");
            Map<String, Object> response = mappingService.mapInventory(type, identifier, username);
            String status = (String) response.get("status");
            
            if ("NOT_FOUND".equals(status)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            } else if ("BAD_REQUEST".equals(status)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.error("Error mapping inventory: ", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error mapping inventory: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> getUnmappedInventory(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "insertDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        LOGGER.info("Getting unmapped inventory of type: {}, page: {}, size: {}, sortBy: {}, sortDir: {}",
                type, page, size, sortBy, sortDir);

        try {
            Map<String, Object> response = queryService.getUnmappedInventory(type, page, size, sortBy, sortDir);
            String status = (String) response.get("status");
            
            if ("BAD_REQUEST".equals(status)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.error("Error retrieving unmapped inventory: ", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error retrieving unmapped inventory: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/{type}/{id}")
    public ResponseEntity<Map<String, Object>> getUnmappedInventoryById(
            @PathVariable String type,
            @PathVariable Integer id) {
        LOGGER.info("Getting unmapped inventory details for type: {}, id: {}", type, id);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", "Not implemented yet");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(response);
    }


    @GetMapping("/search/{type}")
    public ResponseEntity<Map<String, Object>> searchUnmappedInventory(
            @PathVariable String type,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "insertDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        LOGGER.info("Searching unmapped inventory - type: {}, query: {}, page: {}, size: {}, sortBy: {}, sortDir: {}",
                type, query, page, size, sortBy, sortDir);

        try {
            Map<String, Object> response = queryService.searchUnmappedInventory(type, query, page, size, sortBy, sortDir);
            String status = (String) response.get("status");
            
            if ("BAD_REQUEST".equals(status)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.error("Error searching unmapped inventory: ", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error searching inventory: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
