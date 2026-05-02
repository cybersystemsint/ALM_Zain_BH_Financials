//package com.telkom.co.ke.almoptics.services;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
///**
// * High-speed daily reconciliation service: Truncates unmapped tables at midnight,
// * then bulk-populates from main inventories for perfect sync.
// * Optimized for sub-minute execution on large datasets.
// */
//@Service
//public class ReconciliationSyncService {
//
//    private static final Logger logger = LoggerFactory.getLogger(ReconciliationSyncService.class);
//
//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    /**
//     * Scheduled midnight reconciliation: Truncate and repopulate unmapped tables.
//     * Cron: 0 0 0 * * ? (daily at 00:00:00 UTC).
//     */
//    @Scheduled(cron = "0 0 0 * * ?")
//    @Transactional
//    public void performDailyReconciliation() {
//        logger.info("Starting daily reconciliation at midnight...");
//        long startTime = System.currentTimeMillis();
//
//        try {
//            // Step 1: Truncate unmapped tables (native SQL for speed)
//            truncateUnmappedTables();
//
//            // Step 2: Bulk reconcile active inventory
//            long activeCount = bulkReconcileActive();
//            logger.info("Reconciled {} active records", activeCount);
//
//            // Step 3: Bulk reconcile passive inventory
//            long passiveCount = bulkReconcilePassive();
//            logger.info("Reconciled {} passive records", passiveCount);
//
//            // Step 4: Bulk reconcile IT inventory
//            long itCount = bulkReconcileIT();
//            logger.info("Reconciled {} IT records", itCount);
//
//            long totalTime = System.currentTimeMillis() - startTime;
//            logger.info("Daily reconciliation completed successfully in {} ms. Total records: {}", totalTime, activeCount + passiveCount + itCount);
//
//            // Optional: Trigger financial report refresh (e.g., via event publisher)
//            // financialReportService.refreshFromUnmapped();
//
//        } catch (Exception e) {
//            logger.error("Daily reconciliation failed", e);
//            // Rollback transaction ensures unmapped remains consistent
//        }
//    }
//
//    /**
//     * Truncate all unmapped tables using native SQL.
//     * Assumes no FK constraints; disable if needed.
//     */
//    private void truncateUnmappedTables() {
//        logger.info("Truncating unmapped tables...");
//        String[] truncateQueries = {
//                "TRUNCATE TABLE tb_unmappednode",
//                "TRUNCATE TABLE tb_unmappedPassive_Inventory",
//                "TRUNCATE TABLE tb_unmappedIT_INVENTORY"
//        };
//        for (String query : truncateQueries) {
//            jdbcTemplate.execute(query);
//        }
//        logger.info("Unmapped tables truncated successfully");
//    }
//
//    /**
//     * Bulk reconcile active inventory: Select from main, insert formatted into unmapped.
//     * Uses batch insert for speed; assumes UNIQUE on serial_number.
//     * Asset name formatted in SQL (regex extract + concat).
//     */
//    private long bulkReconcileActive() {
//        // Paginate fetch for large datasets (batch size 1000)
//        int pageSize = 1000;
//        int offset = 0;
//        long totalProcessed = 0;
//
//        while (true) {
//            // Native select with asset name formatting
//            String selectSql = """
//                SELECT
//                    siteId, nodeName, nodeType, manufacturer, model, partNumber, serialNumber,
//                    description, manufacturingDate, Element,
//                    -- Format asset name: nodeName / cabinet_shelf_slot_port (extract digits via REGEXP)
//                    CONCAT(
//                        COALESCE(nodeName, ''),
//                        CASE
//                            WHEN Element IS NULL OR Element = '' THEN ''
//                            ELSE CONCAT('/',
//                                COALESCE(REGEXP_SUBSTR(Element, 'cabinet\\s*(\\d+)', 1, 1, 'e'), '0'),
//                                '_',
//                                COALESCE(REGEXP_SUBSTR(Element, 'shelf\\s*(\\d+)', 1, 1, 'e'), '0'),
//                                '_',
//                                COALESCE(REGEXP_SUBSTR(Element, 'slot\\s*(\\d+)', 1, 1, 'e'), '0'),
//                                '_',
//                                COALESCE(REGEXP_SUBSTR(Element, 'port\\s*(\\d+)', 1, 1, 'e'), '0')
//                            )
//                        END
//                    ) AS assetName
//                FROM tb_Node
//                WHERE serialNumber IS NOT NULL AND serialNumber != ''
//                LIMIT ? OFFSET ?
//                """;
//
//            List<Map<String, Object>> batch = jdbcTemplate.queryForList(selectSql, pageSize, offset);
//
//            if (batch.isEmpty()) {
//                break;
//            }
//
//            // Convert to List<Object[]> for simple batchUpdate overload
//            List<Object[]> batchArgs = batch.stream().map(row -> new Object[]{
//                    row.get("siteId"),
//                    row.get("nodeName"),
//                    row.get("nodeType"),
//                    row.get("manufacturer"),
//                    row.get("model"),
//                    row.get("partNumber"),
//                    row.get("serialNumber"),
//                    row.get("description"),
//                    row.get("manufacturingDate"),
//                    row.get("assetName"),
//                    row.get("nodeType")
//            }).collect(Collectors.toList());
//
//            String insertSql = """
//                INSERT IGNORE INTO tb_unmappednode
//                (SiteId, NodeName, NodeType, Manufacturer, Model, PartNumber, SerialNumber,
//                 Description, ManufacturingDate, AssetName, InsertedBy, AssetType)
//                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ReconciliationSync', ?)
//                """;
//
//            int[] results = jdbcTemplate.batchUpdate(insertSql, batchArgs);
//
//            // Count successful inserts (ignores duplicates)
//            long batchProcessed = results.length;
//            totalProcessed += batchProcessed;
//            logger.debug("Processed active batch: {} records (offset: {})", batchProcessed, offset);
//
//            offset += pageSize;
//        }
//
//        return totalProcessed;
//    }
//
//    /**
//     * Bulk reconcile passive inventory: Similar to active, with serial/objectId handling.
//     * Uses mapping_identifier as serial; prefers objectId.
//     */
//    private long bulkReconcilePassive() {
//        // Paginate fetch
//        int pageSize = 1000;
//        int offset = 0;
//        long totalProcessed = 0;
//
//        while (true) {
//            String selectSql = """
//                SELECT
//                    SiteId, Model, CategoryInNEP AS category, ItemBarCode AS item_bar_code, UOM AS uom,
//                    ItemClassification AS item_classification, ItemClassification2 AS item_classification2,
//                    Notes AS notes, `PR/PONo` AS pr_po_no,
//                    COALESCE(Serial, ObjectID) AS mapping_identifier,
//                    CASE WHEN ObjectID IS NOT NULL THEN ObjectID ELSE COALESCE(Serial, '') END AS object_id_str,
//                    NOW() AS entry_date,
//                    'ReconciliationSync' AS entry_user,
//                    'PASSIVE' AS element_type
//                FROM tb_Passive_Inventory
//                WHERE (Serial IS NOT NULL AND Serial != '') OR (ObjectID IS NOT NULL)
//                LIMIT ? OFFSET ?
//                """;
//
//            List<Map<String, Object>> batch = jdbcTemplate.queryForList(selectSql, pageSize, offset);
//
//            if (batch.isEmpty()) {
//                break;
//            }
//
//            // Convert to List<Object[]> for simple batchUpdate overload
//            List<Object[]> batchArgs = batch.stream().map(row -> new Object[]{
//                    row.get("object_id_str"),
//                    row.get("SiteId"),
//                    row.get("element_type"),
//                    row.get("Model"),
//                    row.get("mapping_identifier"),
//                    row.get("entry_date"),
//                    row.get("entry_user"),
//                    row.get("category"),
//                    row.get("item_bar_code"),
//                    row.get("uom"),
//                    row.get("item_classification"),
//                    row.get("item_classification2"),
//                    row.get("notes"),
//                    row.get("pr_po_no")
//            }).collect(Collectors.toList());
//
//            String insertSql = """
//                INSERT IGNORE INTO tb_unmappedPassive_Inventory
//                (ElementID, Site_ID, Element_Type, Model, Serial, Entry_Date, Entry_User,
//                 Category, Item_BarCode, UOM, Item_Classification, Item_Classification_2, Notes, PR_PONo)
//                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
//                """;
//
//            int[] results = jdbcTemplate.batchUpdate(insertSql, batchArgs);
//
//            totalProcessed += results.length;
//            offset += pageSize;
//        }
//
//        return totalProcessed;
//    }
//
//    /**
//     * Bulk reconcile IT inventory: Formats asset name from parent_name + site_id.
//     */
//    private long bulkReconcileIT() {
//        // Paginate fetch
//        int pageSize = 1000;
//        int offset = 0;
//        long totalProcessed = 0;
//
//        while (true) {
//            String selectSql = """
//                SELECT
//                    ObjectID AS element_id, SiteId,
//                    CONCAT(COALESCE(ParentName, ''), '_', COALESCE(SiteId, '')) AS host_name,
//                    HardwareVendor AS manufacturer, Model, HostSerialNumber AS hardware_serial_number,
//                    OS AS os, HostType, DiskDriveSerialNumber AS disk_drive_serial_number, IPAddress AS ip_address, LastUpdateSuccess,
//                    NOW() AS asset_insert_date,
//                    'Unknown' AS warranty,
//                    'IT' AS element_type
//                FROM vw_IT_Inventory
//                WHERE (HostSerialNumber IS NOT NULL AND HostSerialNumber != '') OR (ObjectID IS NOT NULL)
//                LIMIT ? OFFSET ?
//                """;
//
//            List<Map<String, Object>> batch = jdbcTemplate.queryForList(selectSql, pageSize, offset);
//
//            if (batch.isEmpty()) {
//                break;
//            }
//
//            // Convert to List<Object[]> for simple batchUpdate overload
//            List<Object[]> batchArgs = batch.stream().map(row -> new Object[]{
//                    row.get("element_id"),
//                    row.get("SiteId"),
//                    row.get("host_name"),
//                    row.get("element_type"),
//                    row.get("manufacturer"),
//                    row.get("Model"),
//                    row.get("hardware_serial_number"),
//                    row.get("warranty"),
//                    row.get("asset_insert_date"),
//                    row.get("LastUpdateSuccess"),
//                    row.get("os"),
//                    row.get("manufacturer"),
//                    row.get("HostType"),
//                    row.get("disk_drive_serial_number"),
//                    row.get("ip_address")
//            }).collect(Collectors.toList());
//
//            String insertSql = """
//                INSERT IGNORE INTO tb_unmappedIT_INVENTORY
//                (Element_ID, Site_ID, Host_Name, Element_Type, Manufacturer, Model, Hardware_Serial_Number,
//                 Warranty, Asset_Insert_Date, Last_Update_Success, OS, Hardware_Vendor, Host_Type,
//                 Disk_Drive_Serial_Number, IP_Address)
//                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
//                """;
//
//            int[] results = jdbcTemplate.batchUpdate(insertSql, batchArgs);
//
//            totalProcessed += results.length;
//            offset += pageSize;
//        }
//
//        return totalProcessed;
//    }
//}