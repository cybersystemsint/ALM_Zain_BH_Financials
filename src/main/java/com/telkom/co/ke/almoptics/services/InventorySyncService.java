package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.ActiveInventory;
import com.telkom.co.ke.almoptics.entities.ItInventory;
import com.telkom.co.ke.almoptics.entities.NELicense;
import com.telkom.co.ke.almoptics.entities.PassiveInventory;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.repository.ActiveInventoryRepository;
import com.telkom.co.ke.almoptics.repository.ItInventoryRepository;
import com.telkom.co.ke.almoptics.repository.NELicenseRepository;
import com.telkom.co.ke.almoptics.repository.PassiveInventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InventorySyncService {

    private static final Logger logger = LoggerFactory.getLogger(InventorySyncService.class);

    @Autowired
    private ActiveInventoryRepository activeInventoryRepository;

    @Autowired
    private PassiveInventoryRepository passiveInventoryRepository;

    @Autowired
    private ItInventoryRepository itInventoryRepository;

    @Autowired
    private NELicenseRepository neLicenseRepository;

    /**
     * Populates fields from the appropriate inventory based on the identifier.
     * @param report The financial report to populate fields for.
     * @return The updated financial report with populated fields.
     */
    public tb_FinancialReport populateFieldsFromInventory(tb_FinancialReport report) {
        String identifier = report.getAssetSerialNumber() != null && !report.getAssetSerialNumber().isEmpty() ?
                report.getAssetSerialNumber() : report.getAssetName();

        if (identifier == null || identifier.trim().isEmpty()) {
            logger.warn("No valid identifier provided for inventory sync");
            return report;
        }

        // Try Active Inventory
        List<ActiveInventory> activeInventoryList = activeInventoryRepository.findBySerialNumber(identifier);
        if (!activeInventoryList.isEmpty()) {
            logger.info("Found asset(s) in Active Inventory: {}", identifier);
            ActiveInventory active = activeInventoryList.get(0); // Use the first record
            if (activeInventoryList.size() > 1) {
                logger.warn("Multiple Active Inventory records found for identifier: {}. Using the first record.", identifier);
            }
            report.setAssetCategory("Active Hardware");
            if (report.getSiteId() == null) report.setSiteId(active.getSiteId());
            if (report.getPartNumber() == null) report.setPartNumber(active.getPartNumber());
            if (report.getItemBarCode() == null) report.setItemBarCode(null); // ActiveInventory does not have ItemBarCode
            if (report.getModel() == null) report.setModel(active.getModel());
            if (report.getAssetSerialNumber() == null) report.setAssetSerialNumber(active.getSerialNumber());
            return report;
        }

        // Try Passive Inventory
        Optional<PassiveInventory> passiveInventoryOpt = passiveInventoryRepository.findBySerial(identifier)
                .or(() -> passiveInventoryRepository.findByObjectId(identifier));
        if (passiveInventoryOpt.isPresent()) {
            logger.info("Found asset in Passive Inventory: {}", identifier);
            PassiveInventory passive = passiveInventoryOpt.get();
            report.setAssetCategory("Passive Hardware");
            if (report.getSiteId() == null) report.setSiteId(passive.getSiteId());
            if (report.getPartNumber() == null) report.setPartNumber(passive.getPart());
            if (report.getItemBarCode() == null) report.setItemBarCode(passive.getItemBarCode());
            if (report.getModel() == null) report.setModel(passive.getModel());
            if (report.getAssetSerialNumber() == null) report.setAssetSerialNumber(passive.getSerial());
            return report;
        }

        // Try IT Inventory
        Optional<ItInventory> itInventoryOpt = itInventoryRepository.findByHostSerialNumber(identifier)
                .or(() -> itInventoryRepository.findByObjectId(identifier));
        if (itInventoryOpt.isPresent()) {
            logger.info("Found asset in IT Inventory: {}", identifier);
            ItInventory it = itInventoryOpt.get();
            report.setAssetCategory("IT Hardware");
            if (report.getSiteId() == null) report.setSiteId(it.getSiteId());
            if (report.getPartNumber() == null) report.setPartNumber(null); // ITInventory does not have PartNumber
            if (report.getItemBarCode() == null) report.setItemBarCode(null); // ITInventory does not have ItemBarCode
            if (report.getModel() == null) report.setModel(it.getModel());
            if (report.getAssetSerialNumber() == null) report.setAssetSerialNumber(it.getHostSerialNumber());
            return report;
        }

        // Try NELicense
        Optional<NELicense> neLicenseOpt = neLicenseRepository.findByLicenseId(identifier);
        if (neLicenseOpt.isPresent()) {
            logger.info("Found asset in NELicense: {}", identifier);
            NELicense license = neLicenseOpt.get();
            report.setAssetCategory("NElicense Hardware");
            if (report.getSiteId() == null) report.setSiteId(license.getSiteId());
            if (report.getPartNumber() == null) report.setPartNumber(null); // NELicense does not have PartNumber
            if (report.getItemBarCode() == null) report.setItemBarCode(null); // NELicense does not have ItemBarCode
            if (report.getModel() == null) report.setModel(null); // NELicense does not have Model
            if (report.getAssetSerialNumber() == null) report.setAssetSerialNumber(license.getLicenseId());
            return report;
        }

        logger.warn("Asset with identifier {} not found in any inventory", identifier);
        return report;
    }
}