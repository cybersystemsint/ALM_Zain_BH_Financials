package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.ActiveInventory;
import com.telkom.co.ke.almoptics.entities.ItInventory;
import com.telkom.co.ke.almoptics.entities.PassiveInventory;
import com.telkom.co.ke.almoptics.entities.UnmappedActiveInventory;
import com.telkom.co.ke.almoptics.entities.UnmappedITInventory;
import com.telkom.co.ke.almoptics.entities.UnmappedPassiveInventory;
import com.telkom.co.ke.almoptics.repository.ActiveInventoryRepository;
import com.telkom.co.ke.almoptics.repository.ItInventoryRepository;
import com.telkom.co.ke.almoptics.repository.PassiveInventoryRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedActiveInventoryRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedITInventoryRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedPassiveInventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.jpa.domain.Specification;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Service for mapping inventory data between source and unmapped tables
 * Handles special formatting and extraction of asset name components
 */
@EnableScheduling
@Service
public class UnmappedInventoryService {

    private static final Logger logger = LoggerFactory.getLogger(UnmappedInventoryService.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 2000;

    @Autowired
    private ActiveInventoryRepository activeInventoryRepository;

    @Autowired
    private PassiveInventoryRepository passiveInventoryRepository;

    @Autowired
    private ItInventoryRepository itInventoryRepository;

    @Autowired
    private UnmappedActiveInventoryRepository unmappedActiveRepository;

    @Autowired
    private UnmappedPassiveInventoryRepository unmappedPassiveRepository;

    @Autowired
    private UnmappedITInventoryRepository unmappedITRepository;

    /**
     * Custom retry logic for database operations
     */
    private <T> T retryOperation(Supplier<T> operation, String operationName) {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                return operation.get();
            } catch (Exception e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    logger.error("Failed {} after {} attempts: {}", operationName, MAX_RETRIES, e.getMessage());
                    throw e;
                }
                long backoff = BASE_BACKOFF_MS * (long) Math.pow(2, attempt);
                logger.warn("Attempt {}/{} failed for {}, retrying after {}ms: {}", attempt, MAX_RETRIES, operationName, backoff, e.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry backoff", ie);
                }
            }
        }
        return null; // Unreachable
    }

    /**
     * Validates and converts a manufacturing date to a Date object if possible,
     * otherwise returns null and logs the original string.
     *
     * @param manufacturingDate The date to validate (Date or String)
     * @param serialNumber The serial number for logging context
     * @return A valid Date object or null if invalid
     */
    private Date validateManufacturingDate(Object manufacturingDate, String serialNumber) {
        if (manufacturingDate == null) {
            return null;
        }

        if (manufacturingDate instanceof Date) {
            Date date = (Date) manufacturingDate;
            String dateStr = DATE_FORMAT.format(date);
            try {
                // Check if date is within MySQL valid range (1000-01-01 to 9999-12-31)
                Date parsedDate = DATE_FORMAT.parse(dateStr);
                int year = Integer.parseInt(dateStr.substring(0, 4));
                if (year >= 1000 && year <= 9999) {
                    return parsedDate;
                } else {
                    logger.warn("Invalid manufacturing date {} for serial number {}: out of range", dateStr, serialNumber);
                    return null;
                }
            } catch (Exception e) {
                logger.warn("Failed to parse manufacturing date {} for serial number {}: {}", dateStr, serialNumber, e.getMessage());
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
                    logger.warn("Invalid manufacturing date string {} for serial number {}: out of range", dateStr, serialNumber);
                    return null;
                }
            } catch (Exception e) {
                logger.warn("Failed to parse manufacturing date string {} for serial number {}: {}", dateStr, serialNumber, e.getMessage());
                return null;
            }
        } else {
            logger.warn("Unsupported manufacturing date type {} for serial number {}", manufacturingDate.getClass(), serialNumber);
            return null;
        }
    }

    /**
     * Maps active inventory to unmapped active inventory
     * Creates asset name from node name and element with special formatting
     *
     * @param activeInventory Source active inventory
     * @param username User who triggered the mapping
     * @return The created unmapped active inventory entity
     */
    @Transactional(timeout = 120)
    public UnmappedActiveInventory mapActiveInventory(ActiveInventory activeInventory, String username) {
        String serialNumber = activeInventory.getSerialNumber();
        logger.info("Mapping active inventory with serial number: {}", serialNumber);

        // Check for duplicates in main inventory
        boolean hasDuplicates = retryOperation(
                () -> activeInventoryRepository.findBySerialNumber(serialNumber).size() > 1,
                "checkActiveDuplicates"
        );
        if (hasDuplicates) {
            return null; // Silently skip duplicates
        }

        // Check if already in unmapped inventory
        boolean alreadyUnmapped = retryOperation(
                () -> unmappedActiveRepository.findBySerialNumber(serialNumber).isPresent(),
                "checkUnmappedActive"
        );
        if (alreadyUnmapped) {
            return null; // Silently skip already unmapped
        }

        UnmappedActiveInventory unmapped = new UnmappedActiveInventory();

        // Map standard fields
        unmapped.setSiteId(activeInventory.getSiteId());
        unmapped.setNodeName(activeInventory.getNodeName());
        unmapped.setNodeType(activeInventory.getNodeType());
        unmapped.setManufacturer(activeInventory.getManufacturer());
        unmapped.setModel(activeInventory.getModel());
        unmapped.setPartNumber(activeInventory.getPartNumber());
        unmapped.setSerialNumber(serialNumber);
        unmapped.setDescription(activeInventory.getDescription());
        unmapped.setInsertedBy(username);

        // Handle manufacturing date (Date or String)
        Date validDate = validateManufacturingDate(activeInventory.getManufacturingDate(), serialNumber);
        unmapped.setManufacturingDate(validDate);

        // Extract asset type from the node type or description if available
        unmapped.setAssetType(determineAssetType(activeInventory.getNodeType(), activeInventory.getDescription()));

        // Generate asset name from node name and element
        String element = activeInventory.getElement();
        String assetName = formatAssetName(activeInventory.getNodeName(), element);
        unmapped.setAssetName(assetName);

        return retryOperation(
                () -> unmappedActiveRepository.save(unmapped),
                "saveUnmappedActive"
        );
    }

    /**
     * Maps passive inventory to unmapped passive inventory
     * Creates asset name from node name and element with special formatting
     *
     * @param passiveInventory Source passive inventory
     * @param username User who triggered the mapping
     * @return The created unmapped passive inventory entity
     */
    @Transactional(timeout = 120)
    public UnmappedPassiveInventory mapPassiveInventory(PassiveInventory passiveInventory, String username) {
        String serialNumber = passiveInventory.getSerial();
        String objectId = passiveInventory.getObjectId().toString();
        logger.info("Mapping passive inventory with serial number: {} or object ID: {}", serialNumber, objectId);

        // Check for duplicates in main inventory
        boolean hasDuplicates = retryOperation(
                () -> passiveInventoryRepository.findByObjectIdOrSerialNumber(objectId, serialNumber).size() > 1,
                "checkPassiveDuplicates"
        );
        if (hasDuplicates) {
            return null; // Silently skip duplicates
        }

        // Check if already in unmapped inventory
        boolean alreadyUnmapped = retryOperation(
                () -> unmappedPassiveRepository.findBySerialOrObjectId(serialNumber, objectId).isPresent(),
                "checkUnmappedPassive"
        );
        if (alreadyUnmapped) {
            return null; // Silently skip already unmapped
        }

        UnmappedPassiveInventory unmapped = new UnmappedPassiveInventory();

        // Map standard fields
        unmapped.setObjectId(objectId);
        unmapped.setSiteId(passiveInventory.getSiteId());
        unmapped.setElementType("PASSIVE");
        unmapped.setModel(passiveInventory.getModel());
        unmapped.setSerial(serialNumber);
        unmapped.setEntryDate(new Date());
        unmapped.setEntryUser(username);

        // Map additional fields
        unmapped.setCategory(passiveInventory.getCategoryInNEP());
        unmapped.setItemBarCode(passiveInventory.getItemBarCode());
        unmapped.setUom(passiveInventory.getUom());
        unmapped.setItemClassification(passiveInventory.getItemClassification());
        unmapped.setItemClassification2(passiveInventory.getItemClassification2());
        unmapped.setNotes(passiveInventory.getNotes());
        unmapped.setPrPoNo(passiveInventory.getPrPoNo());

        return retryOperation(
                () -> unmappedPassiveRepository.save(unmapped),
                "saveUnmappedPassive"
        );
    }

    /**
     * Maps IT inventory to unmapped IT inventory
     * Creates asset name from node name and element with special formatting
     *
     * @param itInventory Source IT inventory
     * @param username User who triggered the mapping
     * @return The created unmapped IT inventory entity
     */
    @Transactional(timeout = 120)
    public UnmappedITInventory mapITInventory(ItInventory itInventory, String username) {
        String serialNumber = itInventory.getHostSerialNumber();
        logger.info("Mapping IT inventory with serial number: {}", serialNumber);

        // Check for duplicates in main inventory
        boolean hasDuplicates = retryOperation(
                () -> itInventoryRepository.findByObjectIdOrHostSerialNumber(itInventory.getObjectId(), serialNumber).size() > 1,
                "checkITDuplicates"
        );
        if (hasDuplicates) {
            return null; // Silently skip duplicates
        }

        // Check if already in unmapped inventory
        boolean alreadyUnmapped = retryOperation(
                () -> unmappedITRepository.findByHardwareSerialNumber(serialNumber).isPresent(),
                "checkUnmappedIT"
        );
        if (alreadyUnmapped) {
            return null; // Silently skip already unmapped
        }

        UnmappedITInventory unmapped = new UnmappedITInventory();

        // Map standard fields
        unmapped.setElementId(itInventory.getObjectId());
        unmapped.setSiteId(itInventory.getSiteId());
        unmapped.setHostName(itInventory.getParentName());
        unmapped.setElementType("IT");
        unmapped.setManufacturer(itInventory.getHardwareVendor());
        unmapped.setModel(itInventory.getModel());
        unmapped.setHardwareSerialNumber(serialNumber);
        unmapped.setWarranty("Unknown");
        unmapped.setAssetInsertDate(new Date());
        unmapped.setLastUpdateSuccess(itInventory.getLastUpdateSuccess());

        // Map additional fields
        unmapped.setOs(itInventory.getOs());
        unmapped.setHarwareVendor(itInventory.getHardwareVendor());
        unmapped.setHostType(itInventory.getHostType());
        unmapped.setDiskDriveSerialNumber(itInventory.getDiskDriveSerialNumber());
        unmapped.setIpAddress(itInventory.getIpAddress());

        // Generate asset name from parent name and site ID if available
        String assetName = formatAssetName(itInventory.getParentName(), itInventory.getSiteId());
        unmapped.setHostName(assetName);

        return retryOperation(
                () -> unmappedITRepository.save(unmapped),
                "saveUnmappedIT"
        );
    }

    /**
     * Formats the asset name according to specified pattern: NodeName/Element
     * For elements like "cabinet 3/ shelf 16/slot 41/sfpModule", extracts only the numbers.
     * When no numbers are present, uses blank.
     *
     * @param nodeName The node name component
     * @param element The element or location component
     * @return Formatted asset name (e.g., "1150/1_2_5_3_2")
     */
    private String formatAssetName(String nodeName, String element) {
        if (nodeName == null) {
            nodeName = "";
        }

        if (element == null || element.trim().isEmpty()) {
            return nodeName;
        }

        // Extract numbers from element sections
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

    /**
     * Determines asset type based on node type and description
     *
     * @param nodeType The node type (ACTIVE, PASSIVE, IT)
     * @param description Additional description that might contain asset type info
     * @return Determined asset type
     */
    private String determineAssetType(String nodeType, String description) {
        if (nodeType == null) {
            nodeType = "UNKNOWN";
        }

        // Default asset type based on node type
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
                    } else if (description.toLowerCase().contains("laptop") ||
                            description.toLowerCase().contains("desktop")) {
                        return "COMPUTER";
                    }
                }
                return "IT_EQUIPMENT";

            default:
                return "OTHER";
        }
    }

    /**
     * Retrieves paginated unmapped active inventory
     *
     * @param pageable Pagination information
     * @return Page of unmapped active inventory
     */
    public Page<UnmappedActiveInventory> getUnmappedActiveInventory(Pageable pageable) {
        return retryOperation(
                () -> unmappedActiveRepository.findAll(pageable),
                "findAllUnmappedActive"
        );
    }

    /**
     * Retrieves paginated unmapped passive inventory
     *
     * @param pageable Pagination information
     * @return Page of unmapped passive inventory
     */
    public Page<UnmappedPassiveInventory> getUnmappedPassiveInventory(Pageable pageable) {
        return retryOperation(
                () -> unmappedPassiveRepository.findAll(pageable),
                "findAllUnmappedPassive"
        );
    }

    /**
     * Retrieves paginated unmapped IT inventory
     *
     * @param pageable Pagination information
     * @return Page of unmapped IT inventory
     */
    public Page<UnmappedITInventory> getUnmappedITInventory(Pageable pageable) {
        return retryOperation(
                () -> unmappedITRepository.findAll(pageable),
                "findAllUnmappedIT"
        );
    }

    /**
     * Maps active inventory by serial number
     *
     * @param serialNumber Serial number to search for
     * @param username User performing the mapping
     * @return Optional containing the mapped unmapped inventory
     */
    @Transactional(timeout = 120)
    public Optional<UnmappedActiveInventory> mapActiveInventoryBySerialNumber(String serialNumber, String username) {
        logger.info("Looking up active inventory with serial number: {}", serialNumber);

        // Check for duplicates in main inventory
        List<ActiveInventory> activeInventoryList = retryOperation(
                () -> activeInventoryRepository.findBySerialNumber(serialNumber),
                "findActiveBySerialNumber"
        );
        if (activeInventoryList.size() > 1) {
            return Optional.empty(); // Silently skip duplicates
        }
        if (activeInventoryList.isEmpty()) {
            logger.info("No active inventory found for serial number: {}", serialNumber);
            return Optional.empty();
        }

        // Check if already in unmapped inventory
        boolean alreadyUnmapped = retryOperation(
                () -> unmappedActiveRepository.findBySerialNumber(serialNumber).isPresent(),
                "checkUnmappedActive"
        );
        if (alreadyUnmapped) {
            return Optional.empty(); // Silently skip already unmapped
        }

        return Optional.ofNullable(mapActiveInventory(activeInventoryList.get(0), username));
    }

    /**
     * Maps passive inventory by serial number or object ID
     *
     * @param identifier Serial number or object ID to search for
     * @param username User performing the mapping
     * @return Optional containing the mapped unmapped inventory
     */
    @Transactional(timeout = 120)
    public Optional<UnmappedPassiveInventory> mapPassiveInventoryByIdentifier(String identifier, String username) {
        logger.info("Looking up passive inventory with identifier: {}", identifier);

        // Check for duplicates in main inventory
        List<PassiveInventory> passiveList = retryOperation(
                () -> passiveInventoryRepository.findByObjectIdOrSerialNumber(identifier, identifier),
                "findPassiveByIdentifier"
        );
        if (passiveList.size() > 1) {
            return Optional.empty(); // Silently skip duplicates
        }
        if (passiveList.isEmpty()) {
            logger.info("No passive inventory found for identifier: {}", identifier);
            return Optional.empty();
        }

        // Check if already in unmapped inventory
        boolean alreadyUnmapped = retryOperation(
                () -> unmappedPassiveRepository.findBySerialOrObjectId(identifier, identifier).isPresent(),
                "checkUnmappedPassive"
        );
        if (alreadyUnmapped) {
            return Optional.empty(); // Silently skip already unmapped
        }

        return Optional.ofNullable(mapPassiveInventory(passiveList.get(0), username));
    }

    /**
     * Maps IT inventory by serial number or object ID
     *
     * @param identifier Serial number or object ID to search for
     * @param username User performing the mapping
     * @return Optional containing the mapped unmapped inventory
     */
    @Transactional(timeout = 120)
    public Optional<UnmappedITInventory> mapITInventoryByIdentifier(String identifier, String username) {
        logger.info("Looking up IT inventory with identifier: {}", identifier);

        // Check for duplicates in main inventory
        List<ItInventory> itList = retryOperation(
                () -> itInventoryRepository.findByObjectIdOrHostSerialNumber(identifier, identifier),
                "findITByIdentifier"
        );
        if (itList.size() > 1) {
            return Optional.empty(); // Silently skip duplicates
        }
        if (itList.isEmpty()) {
            logger.info("No IT inventory found for identifier: {}", identifier);
            return Optional.empty();
        }

        // Check if already in unmapped inventory
        boolean alreadyUnmapped = retryOperation(
                () -> unmappedITRepository.findByHardwareSerialNumber(identifier).isPresent(),
                "checkUnmappedIT"
        );
        if (alreadyUnmapped) {
            return Optional.empty(); // Silently skip already unmapped
        }

        return Optional.ofNullable(mapITInventory(itList.get(0), username));
    }

    /**
     * Scheduled task to check for unmapped inventory items.
     * This method runs every hour (3600000 milliseconds).
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void scheduleUnmappedInventoryCheck() {
        logger.info("Starting scheduled unmapped inventory check...");
        retryOperation(() -> {
            // Check for unmapped active inventory
            List<ActiveInventory> activeInventories = activeInventoryRepository.findAll();
            for (ActiveInventory activeInventory : activeInventories) {
                if (activeInventory == null) {
                    logger.warn("Encountered null ActiveInventory in scheduled check");
                    continue;
                }
                String serialNumber = activeInventory.getSerialNumber();
                if (activeInventoryRepository.findBySerialNumber(serialNumber).size() > 1) {
                    continue; // Silently skip duplicates
                }
                Optional<UnmappedActiveInventory> unmappedOpt = unmappedActiveRepository.findBySerialNumber(serialNumber);
                if (unmappedOpt.isEmpty()) {
                    logger.info("Found unmapped active inventory with serial number: {}", serialNumber);
                    mapActiveInventory(activeInventory, "ScheduledJob");
                }
            }

            // Check for unmapped passive inventory
            List<PassiveInventory> passiveInventories = passiveInventoryRepository.findAll();
            if (passiveInventories.contains(null)) {
                logger.warn("Passive inventory list contains null entries");
            }
            for (PassiveInventory passiveInventory : passiveInventories) {
                if (passiveInventory == null) {
                    logger.warn("Encountered null PassiveInventory in scheduled check");
                    continue;
                }
                String serialNumber = passiveInventory.getSerial();
                String objectId = passiveInventory.getObjectId().toString();
                if (passiveInventoryRepository.findByObjectIdOrSerialNumber(objectId, serialNumber).size() > 1) {
                    continue; // Silently skip duplicates
                }
                Optional<UnmappedPassiveInventory> unmappedOpt = unmappedPassiveRepository.findBySerialOrObjectId(serialNumber, objectId);
                if (unmappedOpt.isEmpty()) {
                    logger.info("Found unmapped passive inventory with serial number: {} or object ID: {}", serialNumber, objectId);
                    mapPassiveInventory(passiveInventory, "ScheduledJob");
                }
            }

            // Check for unmapped IT inventory
            List<ItInventory> itInventories = itInventoryRepository.findAll();
            for (ItInventory itInventory : itInventories) {
                if (itInventory == null) {
                    logger.warn("Encountered null ItInventory in scheduled check");
                    continue;
                }
                String serialNumber = itInventory.getHostSerialNumber();
                if (itInventoryRepository.findByObjectIdOrHostSerialNumber(itInventory.getObjectId(), serialNumber).size() > 1) {
                    continue; // Silently skip duplicates
                }
                Optional<UnmappedITInventory> unmappedOpt = unmappedITRepository.findByHardwareSerialNumber(serialNumber);
                if (unmappedOpt.isEmpty()) {
                    logger.info("Found unmapped IT inventory with serial number: {}", serialNumber);
                    mapITInventory(itInventory, "ScheduledJob");
                }
            }

            logger.info("Completed scheduled unmapped inventory check.");
            return null;
        }, "scheduleUnmappedInventoryCheck");
    }

    /**
     * Processes all unmapped active inventory records by mapping them to the active inventory table if not already mapped.
     */
    public void processUnmappedActiveInventory() {
        logger.info("Processing unmapped active inventory");
        List<UnmappedActiveInventory> unmappedActiveList = retryOperation(
                () -> unmappedActiveRepository.findAll(),
                "findAllUnmappedActive"
        );
        for (UnmappedActiveInventory unmappedActive : unmappedActiveList) {
            String serialNumber = unmappedActive.getSerialNumber();
            if (serialNumber != null) {
                List<ActiveInventory> existingActive = retryOperation(
                        () -> activeInventoryRepository.findBySerialNumber(serialNumber),
                        "findActiveBySerialNumber"
                );
                if (existingActive.size() > 1) {
                    continue; // Silently skip duplicates
                }
                if (existingActive.isEmpty()) {
                    logger.info("Mapping unmapped active inventory with serial number: {}", serialNumber);
                    mapActiveInventoryBySerialNumber(serialNumber, "SystemProcess");
                }
            } else {
                logger.warn("Skipping unmapped active inventory with null serial number");
            }
        }
    }

    /**
     * Processes all unmapped passive inventory records by mapping them to the passive inventory table if not already mapped.
     */
    public void processUnmappedPassiveInventory() {
        logger.info("Processing unmapped passive inventory");
        List<UnmappedPassiveInventory> unmappedPassiveList = retryOperation(
                () -> unmappedPassiveRepository.findAll(),
                "findAllUnmappedPassive"
        );
        for (UnmappedPassiveInventory unmappedPassive : unmappedPassiveList) {
            String serialNumber = unmappedPassive.getSerial();
            String objectId = unmappedPassive.getObjectId();
            if (serialNumber != null || objectId != null) {
                List<PassiveInventory> existingPassive = retryOperation(
                        () -> passiveInventoryRepository.findByObjectIdOrSerialNumber(objectId, serialNumber),
                        "findPassiveByIdentifier"
                );
                if (existingPassive.size() > 1) {
                    continue; // Silently skip duplicates
                }
                if (existingPassive.isEmpty()) {
                    logger.info("Mapping unmapped passive inventory with serial number: {} or object ID: {}", serialNumber, objectId);
                    mapPassiveInventoryByIdentifier(serialNumber != null ? serialNumber : objectId, "SystemProcess");
                }
            } else {
                logger.warn("Skipping unmapped passive inventory with null serial number and object ID");
            }
        }
    }

    /**
     * Processes all unmapped IT inventory records by mapping them to the IT inventory table if not already mapped.
     */
    public void processUnmappedItInventory() {
        logger.info("Processing unmapped IT inventory");
        int pageNumber = 0;
        int pageSize = 100; // Process in batches of 100
        boolean hasNext = true;

        while (hasNext) {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            Page<UnmappedITInventory> page = retryOperation(
                    () -> unmappedITRepository.findAll(pageable),
                    "findAllUnmappedIT"
            );
            for (UnmappedITInventory unmappedIt : page.getContent()) {
                String serialNumber = unmappedIt.getHardwareSerialNumber();
                if (serialNumber != null) {
                    List<ItInventory> existingIt = retryOperation(
                            () -> itInventoryRepository.findByObjectIdOrHostSerialNumber(unmappedIt.getElementId(), serialNumber),
                            "findITByIdentifier"
                    );
                    if (existingIt.size() > 1) {
                        continue; // Silently skip duplicates
                    }
                    if (existingIt.isEmpty()) {
                        logger.info("Mapping unmapped IT inventory with serial number: {}", serialNumber);
                        mapITInventoryByIdentifier(serialNumber, "SystemProcess");
                    }
                }
            }
            hasNext = page.hasNext();
            pageNumber++;
        }
    }

    /**
     * Search unmapped active inventory by SerialNumber or AssetName
     */
    public Page<UnmappedActiveInventory> searchActiveInventory(String query, String field1, String field2, Pageable pageable) {
        logger.info("Searching active inventory for query: {} in fields: {}, {}", query, field1, field2);

        Specification<UnmappedActiveInventory> spec = (root, querySpec, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search SerialNumber
            predicates.add(cb.like(cb.lower(root.get("serialNumber")), "%" + query.toLowerCase() + "%"));

            // Search AssetName
            predicates.add(cb.like(cb.lower(root.get("assetName")), "%" + query.toLowerCase() + "%"));

            return cb.or(predicates.toArray(new Predicate[0]));
        };

        return retryOperation(
                () -> unmappedActiveRepository.findAll(spec, pageable),
                "searchUnmappedActive"
        );
    }

    /**
     * Search unmapped passive inventory by ElementID, Item_BarCode, or Serial
     */
    public Page<UnmappedPassiveInventory> searchPassiveInventory(String query, String field1, String field2, String field3, Pageable pageable) {
        logger.info("Searching passive inventory for query: {} in fields: {}, {}, {}", query, field1, field2, field3);

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

        return retryOperation(
                () -> unmappedPassiveRepository.findAll(spec, pageable),
                "searchUnmappedPassive"
        );
    }

    /**
     * Search unmapped IT inventory by Element_ID or Host_Serial_Number
     */
    public Page<UnmappedITInventory> searchITInventory(String query, String field1, String field2, Pageable pageable) {
        logger.info("Searching IT inventory for query: {} in fields: {}, {}", query, field1, field2);

        Specification<UnmappedITInventory> spec = (root, querySpec, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search Element_ID
            predicates.add(cb.like(cb.lower(root.get("elementId")), "%" + query.toLowerCase() + "%"));

            // Search Host_Serial_Number (mapped to hardwareSerialNumber in entity)
            predicates.add(cb.like(cb.lower(root.get("hardwareSerialNumber")), "%" + query.toLowerCase() + "%"));

            return cb.or(predicates.toArray(new Predicate[0]));
        };

        return retryOperation(
                () -> unmappedITRepository.findAll(spec, pageable),
                "searchUnmappedIT"
        );
    }
}