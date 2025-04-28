package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.models.AuditLog;
import com.telkom.co.ke.almoptics.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 *
 * @author Gilian
 */
@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Initializes the AuditLog table if it doesn't exist.
     * This method runs after dependency injection is complete.
     */
    @PostConstruct
    public void initAuditLogTable() {
        String createTableSQL =
                "CREATE TABLE IF NOT EXISTS `tb_AuditLog` (" +
                        "    `id` BIGINT NOT NULL AUTO_INCREMENT," +
                        "    `assetId` VARCHAR(255)," +
                        "    `serialNumber` VARCHAR(255)," +
                        "    `previousStatus` VARCHAR(255)," +
                        "    `newStatus` VARCHAR(255)," +
                        "    `changeDate` DATETIME," +
                        "    `nodeType` VARCHAR(255)," +
                        "    `notes` VARCHAR(1000)," +
                        "    `entityName` VARCHAR(255)," +
                        "    `action` VARCHAR(255)," +
                        "    `performedBy` VARCHAR(255)," +
                        "    `details` VARCHAR(255)," +
                        "    `timestamp` TIMESTAMP," +
                        "    PRIMARY KEY (`id`)" +
                        ")";

        try {
            // Execute create table statement
            jdbcTemplate.execute(createTableSQL);

            // Create indexes safely by checking if they exist first
            createIndexIfNotExists("idx_entityName", "tb_AuditLog", "entityName");
            createIndexIfNotExists("idx_action", "tb_AuditLog", "action");
            createIndexIfNotExists("idx_timestamp", "tb_AuditLog", "timestamp");

            System.out.println("AuditLog table initialization completed successfully");
        } catch (Exception e) {
            System.err.println("Error initializing AuditLog table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates an index if it doesn't exist already.
     * MySQL doesn't support "CREATE INDEX IF NOT EXISTS" directly, so we need to check first.
     *
     * @param indexName The name of the index to create
     * @param tableName The name of the table to create the index on
     * @param columnName The column to index
     */
    private void createIndexIfNotExists(String indexName, String tableName, String columnName) {
        try {
            // Check if index exists
            String checkIndex =
                    "SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = ? " +
                            "AND INDEX_NAME = ?";

            Integer indexExists = jdbcTemplate.queryForObject(
                    checkIndex,
                    Integer.class,
                    tableName,
                    indexName
            );

            // Create index if it doesn't exist
            if (indexExists != null && indexExists == 0) {
                String createIndex =
                        "CREATE INDEX " + indexName + " ON " + tableName + "(`" + columnName + "`)";
                jdbcTemplate.execute(createIndex);
                System.out.println("Created index: " + indexName);
            } else {
                System.out.println("Index already exists: " + indexName);
            }
        } catch (Exception e) {
            System.err.println("Error creating index " + indexName + ": " + e.getMessage());
        }
    }

    /**
     * Logs an action performed in the system.
     *
     * @param entityName   The name of the entity affected (e.g., "Asset", "Financial Report").
     * @param action       The action performed (e.g., "INSERT", "UPDATE", "DELETE").
     * @param performedBy  The user or system that performed the action.
     * @param details      Additional details or metadata related to the action.
     */
    public void logAction(String entityName, String action, String performedBy, String details) {
        AuditLog log = new AuditLog();
        log.setEntityName(entityName);
        log.setAction(action);
        log.setPerformedBy(performedBy);
        log.setDetails(details);
        log.setTimestamp(Instant.now()); // Set the timestamp explicitly
        auditLogRepository.save(log);
    }

    /**
     * Logs a status change for an object.
     *
     * @param objectId       The ID of the object being changed.
     * @param serialNumber   The serial number of the object (if applicable).
     * @param previousStatus The previous status of the object.
     * @param newStatus      The new status of the object.
     * @param nodeType       The type of node/object.
     * @param notes          Additional notes about the change.
     * @param performedBy    The user who performed the change.
     */
    public void logStatusChange(String objectId, String serialNumber, String previousStatus,
                                String newStatus, String nodeType, String notes, String performedBy) {
        AuditLog log = new AuditLog();

        log.setSerialNumber(serialNumber);
        log.setPreviousStatus(previousStatus);
        log.setNewStatus(newStatus);
        log.setNodeType(nodeType);
        log.setNotes(notes);
        log.setPerformedBy(performedBy);

        log.setAction("STATUS_CHANGE");
        log.setEntityName(nodeType);
        log.setDetails("Status changed from " + previousStatus + " to " + newStatus);

        auditLogRepository.save(log);
    }

    /**
     * Retrieves all audit logs from the database.
     *
     * @return List of all audit logs.
     */
    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAll();
    }

    /**
     * Retrieves audit logs by entity name.
     *
     * @param entityName The name of the entity to filter logs.
     * @return List of audit logs related to the specified entity.
     */
    public List<AuditLog> getLogsByEntityName(String entityName) {
        return auditLogRepository.findByEntityName(entityName);
    }

    /**
     * Retrieves audit logs by action type.
     *
     * @param action The action performed (INSERT, UPDATE, DELETE).
     * @return List of logs filtered by action type.
     */
    public List<AuditLog> getLogsByAction(String action) {
        return auditLogRepository.findByAction(action);
    }

    /**
     * Retrieves audit logs for a specific object.
     *
     * @param assetId The ID of the object to filter logs.
     * @return List of audit logs related to the specified object.
     */
    public List<AuditLog> getLogsByObjectId(String assetId) {
        return auditLogRepository.findByAssetId(assetId);
    }

    /**
     * Retrieves audit logs for a specific serial number.
     *
     * @param serialNumber The serial number to filter logs.
     * @return List of audit logs related to the specified serial number.
     */
    public List<AuditLog> getLogsBySerialNumber(String serialNumber) {
        return auditLogRepository.findBySerialNumber(serialNumber);
    }

    /**
     * Retrieves audit logs created by a specific user.
     *
     * @param performedBy The user who performed the actions.
     * @return List of audit logs created by the specified user.
     */
    public List<AuditLog> getLogsByPerformedBy(String performedBy) {
        return auditLogRepository.findByPerformedBy(performedBy);
    }

    /**
     * Retrieves audit logs within a date range.
     *
     * @param startDate The start date of the range.
     * @param endDate The end date of the range.
     * @return List of audit logs within the specified date range.
     */
    public List<AuditLog> getLogsByDateRange(Date startDate, Date endDate) {
        return auditLogRepository.findByTimestampBetween(startDate, endDate);
    }

    /**
     * Deletes all logs older than a certain number of days to prevent database bloat.
     *
     * @param days The number of days after which logs should be deleted.
     * @return The number of records deleted.
     */
    public int deleteOldLogs(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        Date cutoffDate = cal.getTime();

        return auditLogRepository.deleteByTimestampBefore(cutoffDate);
    }

    /**
     * Counts the total number of audit logs in the system.
     *
     * @return The total count of audit logs.
     */
    public long countTotalLogs() {
        return auditLogRepository.count();
    }

    /**
     * Counts the number of audit logs for a specific entity.
     *
     * @param entityName The name of the entity to count logs for.
     * @return The count of logs for the specified entity.
     */
    public long countLogsByEntity(String entityName) {
        return auditLogRepository.countByEntityName(entityName);
    }

    /**
     * Counts the number of audit logs for a specific action.
     *
     * @param action The action to count logs for.
     * @return The count of logs for the specified action.
     */
    public long countLogsByAction(String action) {
        return auditLogRepository.countByAction(action);
    }
}