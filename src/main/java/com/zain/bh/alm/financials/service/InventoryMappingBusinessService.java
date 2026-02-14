package com.zain.bh.alm.financials.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.UnmappedActiveInventory;
import com.zain.bh.alm.financials.entity.UnmappedITInventory;
import com.zain.bh.alm.financials.entity.UnmappedPassiveInventory;

@Service
public class InventoryMappingBusinessService {

    private final UnmappedInventoryService unmappedInventoryService;

    public InventoryMappingBusinessService(UnmappedInventoryService unmappedInventoryService) {
        this.unmappedInventoryService = unmappedInventoryService;
    }

    public Map<String, Object> mapInventory(String type, String identifier, String username) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        switch (type.toUpperCase()) {
            case "ACTIVE":
                Optional<UnmappedActiveInventory> activeResult =
                        unmappedInventoryService.mapActiveInventoryBySerialNumber(identifier, username);
                if (activeResult.isPresent()) {
                    response.put("status", "success");
                    response.put("message", "Active inventory mapped successfully");
                    response.put("data", activeResult.get());
                } else {
                    response.put("status", "NOT_FOUND");
                    response.put("message", "Active inventory with identifier " + identifier + " not found");
                }
                break;

            case "PASSIVE":
                Optional<UnmappedPassiveInventory> passiveResult =
                        unmappedInventoryService.mapPassiveInventoryByIdentifier(identifier, username);
                if (passiveResult.isPresent()) {
                    response.put("status", "success");
                    response.put("message", "Passive inventory mapped successfully");
                    response.put("data", passiveResult.get());
                } else {
                    response.put("status", "NOT_FOUND");
                    response.put("message", "Passive inventory with identifier " + identifier + " not found");
                }
                break;

            case "IT":
                Optional<UnmappedITInventory> itResult =
                        unmappedInventoryService.mapITInventoryByIdentifier(identifier, username);
                if (itResult.isPresent()) {
                    response.put("status", "success");
                    response.put("message", "IT inventory mapped successfully");
                    response.put("data", itResult.get());
                } else {
                    response.put("status", "NOT_FOUND");
                    response.put("message", "IT inventory with identifier " + identifier + " not found");
                }
                break;

            default:
                response.put("status", "BAD_REQUEST");
                response.put("message", "Invalid inventory type: " + type);
        }

        return response;
    }
}
