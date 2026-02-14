package com.zain.bh.alm.financials.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.ITInventory;
import com.zain.bh.alm.financials.entity.Node;
import com.zain.bh.alm.financials.entity.PassiveInventory;
import com.zain.bh.alm.financials.entity.UnmappedActiveInventory;
import com.zain.bh.alm.financials.entity.UnmappedITInventory;
import com.zain.bh.alm.financials.entity.UnmappedPassiveInventory;
import com.zain.bh.alm.financials.repository.ITInventoryRepository;
import com.zain.bh.alm.financials.repository.NodeRepository;
import com.zain.bh.alm.financials.repository.PassiveInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedActiveInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedITInventoryRepository;
import com.zain.bh.alm.financials.repository.UnmappedPassiveInventoryRepository;

@EnableScheduling
@Service
public class UnmappedInventoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnmappedInventoryService.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final NodeRepository activeInventoryRepository;
    private final PassiveInventoryRepository passiveInventoryRepository;
    private final ITInventoryRepository itInventoryRepository;
    private final UnmappedActiveInventoryRepository unmappedActiveRepository;
    private final UnmappedPassiveInventoryRepository unmappedPassiveRepository;
    private final UnmappedITInventoryRepository unmappedITRepository;

    public UnmappedInventoryService(NodeRepository activeInventoryRepository,
            PassiveInventoryRepository passiveInventoryRepository, ITInventoryRepository itInventoryRepository,
            UnmappedActiveInventoryRepository unmappedActiveRepository,
            UnmappedPassiveInventoryRepository unmappedPassiveRepository,
            UnmappedITInventoryRepository unmappedITRepository) {
        this.activeInventoryRepository = activeInventoryRepository;
        this.passiveInventoryRepository = passiveInventoryRepository;
        this.itInventoryRepository = itInventoryRepository;
        this.unmappedActiveRepository = unmappedActiveRepository;
        this.unmappedPassiveRepository = unmappedPassiveRepository;
        this.unmappedITRepository = unmappedITRepository;
    }

    private Date validateManufacturingDate(Object manufacturingDate, String serialNumber) {
        if (manufacturingDate == null) {
            return null;
        }

        if (manufacturingDate instanceof Date) {
            Date date = (Date) manufacturingDate;
            String dateStr = DATE_FORMAT.format(date);
            try {
                Date parsedDate = DATE_FORMAT.parse(dateStr);
                int year = Integer.parseInt(dateStr.substring(0, 4));
                if (year >= 1000 && year <= 9999) {
                    return parsedDate;
                } else {
                    LOGGER.warn("Invalid manufacturing date {} for serial number {}: out of range", dateStr, serialNumber);
                    return null;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse manufacturing date {} for serial number {}: {}", dateStr, serialNumber, e.getMessage());
                return null;
            }
        } else if (manufacturingDate instanceof String) {
            String dateStr = (String) manufacturingDate;
            try {
                Date parsedDate = DATE_FORMAT.parse(dateStr);
                int year = Integer.parseInt(dateStr.substring(0, 4));
                if (year >= 1000 && year <= 9999) {
                    return parsedDate;
                } else {
                    LOGGER.warn("Invalid manufacturing date string {} for serial number {}: out of range", dateStr, serialNumber);
                    return null;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse manufacturing date string {} for serial number {}: {}", dateStr, serialNumber, e.getMessage());
                return null;
            }
        } else {
            LOGGER.warn("Unsupported manufacturing date type {} for serial number {}", manufacturingDate.getClass(), serialNumber);
            return null;
        }
    }

    public UnmappedActiveInventory mapActiveInventory(Node activeInventory, String username) {
        LOGGER.info("Mapping active inventory to unmapped with serial number: {}", activeInventory.getSerialNumber());
        UnmappedActiveInventory unmapped = new UnmappedActiveInventory();
        unmapped.setSiteId(activeInventory.getSiteId());
        unmapped.setNodeName(activeInventory.getNodeName());
        unmapped.setNodeType(activeInventory.getNodeType());
        unmapped.setManufacturer(activeInventory.getManufacturer());
        unmapped.setModel(activeInventory.getModel());
        unmapped.setPartNumber(activeInventory.getPartNumber());
        unmapped.setSerialNumber(activeInventory.getSerialNumber());
        unmapped.setDescription(activeInventory.getDescription());
        unmapped.setInsertedBy(username);
        Date validDate = validateManufacturingDate(activeInventory.getManufacturingDate(), activeInventory.getSerialNumber());
        unmapped.setManufacturingDate(validDate);
        unmapped.setAssetType(determineAssetType(activeInventory.getNodeType(), activeInventory.getDescription()));
        String element = activeInventory.getElement();
        String assetName = formatAssetName(activeInventory.getNodeName(), element);
        unmapped.setAssetName(assetName);
        return unmappedActiveRepository.save(unmapped);
    }

    public UnmappedPassiveInventory mapPassiveInventory(PassiveInventory passiveInventory, String username) {
        LOGGER.info("Mapping passive inventory to unmapped with serial number: {}", passiveInventory.getSerial());
        UnmappedPassiveInventory unmapped = new UnmappedPassiveInventory();
        unmapped.setObjectId(passiveInventory.getObjectId().toString());
        unmapped.setSiteId(passiveInventory.getSiteId());
        unmapped.setElementType("PASSIVE");
        unmapped.setModel(passiveInventory.getModel());
        unmapped.setSerial(passiveInventory.getSerial());
        unmapped.setEntryDate(new Date());
        unmapped.setEntryUser(username);
        unmapped.setCategory(passiveInventory.getCategoryInNEP());
        unmapped.setItemBarCode(passiveInventory.getItemBarCode());
        unmapped.setUom(passiveInventory.getUom());
        unmapped.setItemClassification(passiveInventory.getItemClassification());
        unmapped.setItemClassification2(passiveInventory.getItemClassification2());
        unmapped.setNotes(passiveInventory.getNotes());
        unmapped.setPrPoNo(passiveInventory.getPrPoNo());
        return unmappedPassiveRepository.save(unmapped);
    }

    public UnmappedITInventory mapITInventory(ITInventory itInventory, String username) {
        LOGGER.info("Mapping IT inventory to unmapped with serial number: {}", itInventory.getHostSerialNumber());
        UnmappedITInventory unmapped = new UnmappedITInventory();
        unmapped.setElementId(itInventory.getObjectId());
        unmapped.setSiteId(itInventory.getSiteId());
        unmapped.setHostName(itInventory.getParentName());
        unmapped.setElementType("IT");
        unmapped.setManufacturer(itInventory.getHardwareVendor());
        unmapped.setModel(itInventory.getModel());
        unmapped.setHardwareSerialNumber(itInventory.getHostSerialNumber());
        unmapped.setWarranty("Unknown");
        unmapped.setAssetInsertDate(new Date());
        unmapped.setLastUpdateSuccess(itInventory.getLastUpdateSuccess());
        unmapped.setOs(itInventory.getOs());
        unmapped.setHarwareVendor(itInventory.getHardwareVendor());
        unmapped.setHostType(itInventory.getHostType());
        unmapped.setDiskDriveSerialNumber(itInventory.getDiskDriveSerialNumber());
        unmapped.setIpAddress(itInventory.getIpAddress());
        String assetName = formatAssetName(itInventory.getParentName(), itInventory.getSiteId());
        unmapped.setHostName(assetName);
        return unmappedITRepository.save(unmapped);
    }

    private String formatAssetName(String nodeName, String element) {
        if (nodeName == null) {
            nodeName = "";
        }
        if (element == null || element.trim().isEmpty()) {
            return nodeName;
        }
        StringBuilder elementNumbers = new StringBuilder();
        String[] sections = element.split("/");
        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].trim();
            Pattern pattern = Pattern.compile("\\d+");
            Matcher matcher = pattern.matcher(section);
            if (matcher.find()) {
                if (elementNumbers.length() > 0) {
                    elementNumbers.append("_");
                }
                elementNumbers.append(matcher.group());
            }
        }
        if (elementNumbers.length() == 0) {
            return nodeName;
        }
        return nodeName + "/" + elementNumbers.toString();
    }

    private String determineAssetType(String nodeType, String description) {
        if (nodeType == null) {
            nodeType = "UNKNOWN";
        }
        switch (nodeType.toUpperCase()) {
            case "ACTIVE":
                if (description != null) {
                    if (description.toLowerCase().contains("router")) {
                        return "ROUTER";
                    } else if (description.toLowerCase().contains("switch")) {
                        return "SWITCH";
                    } else if (description.toLowerCase().contains("olt")) {
                        return "OLT";
                    }
                }
                return "NETWORK_EQUIPMENT";
            case "PASSIVE":
                if (description != null) {
                    if (description.toLowerCase().contains("cable")) {
                        return "CABLE";
                    } else if (description.toLowerCase().contains("splitter")) {
                        return "SPLITTER";
                    } else if (description.toLowerCase().contains("cabinet")) {
                        return "CABINET";
                    }
                }
                return "PASSIVE_EQUIPMENT";
            case "IT":
                if (description != null) {
                    if (description.toLowerCase().contains("server")) {
                        return "SERVER";
                    } else if (description.toLowerCase().contains("storage")) {
                        return "STORAGE";
                    } else if (description.toLowerCase().contains("laptop") || description.toLowerCase().contains("desktop")) {
                        return "COMPUTER";
                    }
                }
                return "IT_EQUIPMENT";
            default:
                return "OTHER";
        }
    }

    public Page<UnmappedActiveInventory> getUnmappedActiveInventory(Pageable pageable) {
        return unmappedActiveRepository.findAll(pageable);
    }

    public Page<UnmappedPassiveInventory> getUnmappedPassiveInventory(Pageable pageable) {
        return unmappedPassiveRepository.findAll(pageable);
    }

    public Page<UnmappedITInventory> getUnmappedITInventory(Pageable pageable) {
        return unmappedITRepository.findAll(pageable);
    }

    public Optional<UnmappedActiveInventory> mapActiveInventoryBySerialNumber(String serialNumber, String username) {
        LOGGER.info("Looking up active inventory with serial number: {}", serialNumber);

        List<Node> activeInventoryOpt = activeInventoryRepository.findBySerialNumber(serialNumber);
        if (activeInventoryOpt.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(mapActiveInventory(activeInventoryOpt.get(0), username));
    }

    public Optional<UnmappedPassiveInventory> mapPassiveInventoryByIdentifier(String identifier, String username) {
        LOGGER.info("Looking up passive inventory with identifier: {}", identifier);

        List<PassiveInventory> passiveList = passiveInventoryRepository.findByObjectIdOrSerialNumber(identifier, identifier);
        if (passiveList.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(mapPassiveInventory(passiveList.get(0), username));
    }

    public Optional<UnmappedITInventory> mapITInventoryByIdentifier(String identifier, String username) {
        LOGGER.info("Looking up IT inventory with identifier: {}", identifier);

        List<ITInventory> itList = itInventoryRepository.findByObjectIdOrHostSerialNumber(identifier, identifier);
        if (itList.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(mapITInventory(itList.get(0), username));
    }

    @Scheduled(fixedRate = 3600000) // Run every hour
    public void scheduleUnmappedInventoryCheck() {
        LOGGER.info("Starting scheduled unmapped inventory check...");

        // Check for unmapped active inventory
        List<Node> activeInventories = activeInventoryRepository.findAll();
        for (Node activeInventory : activeInventories) {
            if (activeInventory == null) {
                LOGGER.warn("Encountered null ActiveInventory in scheduled check");
                continue;
            }
            Optional<UnmappedActiveInventory> unmappedOpt = unmappedActiveRepository.findBySerialNumber(activeInventory.getSerialNumber());
            if (unmappedOpt.isEmpty()) {
                LOGGER.info("Found unmapped active inventory with serial number: {}", activeInventory.getSerialNumber());
                mapActiveInventory(activeInventory, "ScheduledJob");
            }
        }

        // Check for unmapped passive inventory
        List<PassiveInventory> passiveInventories = passiveInventoryRepository.findAll();
        if (passiveInventories.contains(null)) {
            LOGGER.warn("Passive inventory list contains null entries");
        }
        for (PassiveInventory passiveInventory : passiveInventories) {
            if (passiveInventory == null) {
                LOGGER.warn("Encountered null PassiveInventory in scheduled check");
                continue;
            }
            Optional<UnmappedPassiveInventory> unmappedOpt = unmappedPassiveRepository.findBySerialOrObjectId(passiveInventory.getSerial(), passiveInventory.getObjectId().toString());
            if (unmappedOpt.isEmpty()) {
                LOGGER.info("Found unmapped passive inventory with serial number: {} or object ID: {}", passiveInventory.getSerial(), passiveInventory.getObjectId());
                mapPassiveInventory(passiveInventory, "ScheduledJob");
            }
        }

        // Check for unmapped IT inventory
        List<ITInventory> itInventories = itInventoryRepository.findAll();
        for (ITInventory itInventory : itInventories) {
            if (itInventory == null) {
                LOGGER.warn("Encountered null ItInventory in scheduled check");
                continue;
            }
            Optional<UnmappedITInventory> unmappedOpt = unmappedITRepository.findByHardwareSerialNumber(itInventory.getHostSerialNumber());
            if (unmappedOpt.isEmpty()) {
                LOGGER.info("Found unmapped IT inventory with serial number: {}", itInventory.getHostSerialNumber());
                mapITInventory(itInventory, "ScheduledJob");
            }
        }

        LOGGER.info("Completed scheduled unmapped inventory check.");
    }

    public void processUnmappedActiveInventory() {
        LOGGER.info("Processing unmapped active inventory");

        List<UnmappedActiveInventory> unmappedActiveList = unmappedActiveRepository.findAll();
        for (UnmappedActiveInventory unmappedActive : unmappedActiveList) {
            String serialNumber = unmappedActive.getSerialNumber();
            if (serialNumber != null) {
                List<Node> existingActive = activeInventoryRepository.findBySerialNumber(serialNumber);
                if (existingActive.isEmpty()) {
                    LOGGER.info("Mapping unmapped active inventory with serial number: {}", serialNumber);
                    mapActiveInventoryBySerialNumber(serialNumber, "SystemProcess");
                } else {
                    LOGGER.debug("Active inventory with serial number {} already mapped, skipping", serialNumber);
                }
            } else {
                LOGGER.warn("Skipping unmapped active inventory with null serial number");
            }
        }
    }

    public void processUnmappedPassiveInventory() {
        LOGGER.info("Processing unmapped passive inventory");

        List<UnmappedPassiveInventory> unmappedPassiveList = unmappedPassiveRepository.findAll();
        for (UnmappedPassiveInventory unmappedPassive : unmappedPassiveList) {
            String serialNumber = unmappedPassive.getSerial();
            String objectId = unmappedPassive.getObjectId();
            if (serialNumber != null || objectId != null) {
                List<PassiveInventory> existingPassive = passiveInventoryRepository.findByObjectIdOrSerialNumber(objectId, serialNumber);
                if (existingPassive.isEmpty()) {
                    LOGGER.info("Mapping unmapped passive inventory with serial number: {} or object ID: {}", serialNumber, objectId);
                    mapPassiveInventoryByIdentifier(serialNumber != null ? serialNumber : objectId, "SystemProcess");
                } else {
                    LOGGER.debug("Passive inventory with serial number {} or object ID {} already mapped, skipping", serialNumber, objectId);
                }
            } else {
                LOGGER.warn("Skipping unmapped passive inventory with null serial number and object ID");
            }
        }
    }

    public void processUnmappedItInventory() {
        LOGGER.info("Processing unmapped IT inventory");
        Pageable pageable = PageRequest.of(0, 100); // Process in batches of 100
        Page<UnmappedITInventory> page;

        do {
            page = unmappedITRepository.findAll(pageable);
            for (UnmappedITInventory unmappedIt : page.getContent()) {
                String serialNumber = unmappedIt.getHardwareSerialNumber();
                if (serialNumber != null) {
                    List<ITInventory> existingIt = itInventoryRepository.findByObjectIdOrHostSerialNumber(unmappedIt.getElementId(), serialNumber);
                    if (existingIt.isEmpty()) {
                        LOGGER.info("Mapping unmapped IT inventory with serial number: {}", serialNumber);
                        mapITInventoryByIdentifier(serialNumber, "SystemProcess");
                    }
                }
            }
            pageable = page.nextPageable();
        } while (page.hasNext());
    }

    public Page<UnmappedActiveInventory> searchActiveInventory(String query, String field1, String field2, Pageable pageable) {
        LOGGER.info("Searching active inventory for query: {} in fields: {}, {}", query, field1, field2);

        Specification<UnmappedActiveInventory> spec = (root, querySpec, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search SerialNumber
            predicates.add(cb.like(cb.lower(root.get("serialNumber")), "%" + query.toLowerCase() + "%"));

            // Search AssetName
            predicates.add(cb.like(cb.lower(root.get("assetName")), "%" + query.toLowerCase() + "%"));

            return cb.or(predicates.toArray(new Predicate[0]));
        };

        return unmappedActiveRepository.findAll(spec, pageable);
    }

    public Page<UnmappedPassiveInventory> searchPassiveInventory(String query, String field1, String field2, String field3, Pageable pageable) {
        LOGGER.info("Searching passive inventory for query: {} in fields: {}, {}, {}", query, field1, field2, field3);

        Specification<UnmappedPassiveInventory> spec = (root, querySpec, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search ElementID
            predicates.add(cb.like(cb.lower(root.get("objectId")), "%" + query.toLowerCase() + "%"));

            // Search Item_BarCode
            predicates.add(cb.like(cb.lower(root.get("itemBarCode")), "%" + query.toLowerCase() + "%"));

            // Search Serial
            predicates.add(cb.like(cb.lower(root.get("serial")), "%" + query.toLowerCase() + "%"));

            return cb.or(predicates.toArray(new Predicate[0]));
        };

        return unmappedPassiveRepository.findAll(spec, pageable);
    }

    public Page<UnmappedITInventory> searchITInventory(String query, String field1, String field2, Pageable pageable) {
        LOGGER.info("Searching IT inventory for query: {} in fields: {}, {}", query, field1, field2);

        Specification<UnmappedITInventory> spec = (root, querySpec, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search Element_ID
            predicates.add(cb.like(cb.lower(root.get("elementId")), "%" + query.toLowerCase() + "%"));

            // Search Host_Serial_Number (mapped to hardwareSerialNumber in entity)
            predicates.add(cb.like(cb.lower(root.get("hardwareSerialNumber")), "%" + query.toLowerCase() + "%"));

            return cb.or(predicates.toArray(new Predicate[0]));
        };

        return unmappedITRepository.findAll(spec, pageable);
    }

}
