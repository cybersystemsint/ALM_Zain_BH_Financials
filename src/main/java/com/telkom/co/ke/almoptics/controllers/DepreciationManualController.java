package com.telkom.co.ke.almoptics.controllers;

import com.telkom.co.ke.almoptics.services.DepreciationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/depreciation")
public class DepreciationManualController {

    private static final Logger logger = LoggerFactory.getLogger(DepreciationManualController.class);

    @Autowired
    private DepreciationService depreciationService;

    /**
     * Endpoint to manually trigger the depreciation calculation for all active financial reports.
     * @return ResponseEntity with a success or error message
     */
    @GetMapping("/calculate")
    public ResponseEntity<String> manualDepreciationCalculation() {
        logger.info("Manual depreciation calculation triggered at {}", java.time.LocalDateTime.now());

        try {
            depreciationService.calculateMonthlyDepreciation();
            logger.info("Manual depreciation calculation completed successfully");
            return ResponseEntity.ok("Depreciation calculation initiated successfully. Check logs for details.");
        } catch (Exception e) {
            logger.error("Error during manual depreciation calculation: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error initiating depreciation calculation: " + e.getMessage());
        }
    }
}