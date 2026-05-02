package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.models.AuditLog;
import com.telkom.co.ke.almoptics.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Audit log service.
 *
 * <p><b>Storage strategy</b> – we now route every audit event into a
 * dedicated rolling file ({@code alm-audit.log} via the {@code "audit"}
 * SLF4J logger configured in {@code logback-spring.xml}) <em>instead of</em>
 * writing it to the {@code tb_AuditLog} MySQL table.</p>
 *
 * <p>Why? The DB-backed audit trail had three problems we kept hitting in
 * production:</p>
 * <ul>
 *   <li><b>Storage bloat</b> – every sync run wrote 6+ rows; over months
 *       the table dominates the schema's footprint and lengthens backups.</li>
 *   <li><b>Truncation errors</b> – the {@code details VARCHAR(255)} column
 *       silently rolled back transactions when the orchestrator's per-stage
 *       summary got long ("Data too long for column 'details'").</li>
 *   <li><b>Read patterns are file-shaped</b> – the only consumers tail the
 *       last N events / grep by user-id, never run analytical SQL on it.</li>
 * </ul>
 *
 * <p>Compatibility: the public {@code logAction()} / {@code logStatusChange()}
 * methods keep their old signatures, and the historical query methods
 * ({@code getLogsByEntityName}, {@code countTotalLogs}, …) still hit the DB
 * if it's still populated — so old call-sites don't break and historical
 * data stays readable. Set {@code audit.persist.db=true} to additionally
 * keep writing to {@code tb_AuditLog} (off by default).</p>
 *
 * @author Gilian
 */
@Service
public class AuditLogService {

    /** Application/operational logger – always on. */
    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    /**
     * Dedicated audit logger. Wired in {@code logback-spring.xml} to write
     * to {@code alm-audit.log} with a size+time rolling policy
     * (50 MB chunks, 60 days, 5 GB total cap, gz-compressed).
     * {@code additivity=false} means audit events do NOT pollute the main
     * application log.
     */
    private static final Logger auditFileLogger = LoggerFactory.getLogger("audit");

    @Autowired(required = false)
    private AuditLogRepository auditLogRepository;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /**
     * Master switch. {@code false} (default) means audit events go ONLY to
     * the rolling file; nothing is written to MySQL — exactly what we want
     * in long-running production deployments. Set to {@code true} when you
     * need both file + DB during a transition / data-migration window.
     */
    @Value("${audit.persist.db:false}")
    private boolean persistToDb;

    /**
     * Initialises the legacy {@code tb_AuditLog} table only if DB
     * persistence has been opted-in. Skipping the CREATE TABLE keeps
     * fresh deployments clean of the now-deprecated table.
     */
    @PostConstruct
    public void initAuditLogTable() {
        if (!persistToDb) {
            logger.info("AuditLogService: DB persistence disabled (audit.persist.db=false). " +
                    "Events will be written to the 'audit' rolling file only.");
            return;
        }
        if (jdbcTemplate == null) {
            logger.warn("AuditLogService: persistToDb=true but JdbcTemplate is not available — DB writes will be skipped.");
            return;
        }
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
                        // widened from VARCHAR(255) – the orchestrator
                        // summary regularly exceeded the old length and
                        // caused silent rollbacks.
                        "    `details` VARCHAR(2000)," +
                        "    `timestamp` TIMESTAMP," +
                        "    PRIMARY KEY (`id`)" +
                        ")";
        try {
            jdbcTemplate.execute(createTableSQL);
            createIndexIfNotExists("idx_entityName", "tb_AuditLog", "entityName");
            createIndexIfNotExists("idx_action",     "tb_AuditLog", "action");
            createIndexIfNotExists("idx_timestamp",  "tb_AuditLog", "timestamp");
            logger.info("AuditLog table initialisation complete (DB persistence ON).");
        } catch (Exception e) {
            logger.error("Error initialising tb_AuditLog: {}", e.getMessage(), e);
        }
    }

    private void createIndexIfNotExists(String indexName, String tableName, String columnName) {
        try {
            String checkIndex =
                    "SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "  AND TABLE_NAME   = ? " +
                            "  AND INDEX_NAME   = ?";
            Integer indexExists = jdbcTemplate.queryForObject(checkIndex, Integer.class, tableName, indexName);
            if (indexExists != null && indexExists == 0) {
                jdbcTemplate.execute("CREATE INDEX " + indexName + " ON " + tableName + "(`" + columnName + "`)");
                logger.info("Created audit index {}", indexName);
            }
        } catch (Exception e) {
            logger.warn("Could not ensure audit index {}: {}", indexName, e.getMessage());
        }
    }

    // ==================================================================
    // PUBLIC LOGGING API – unchanged signatures, new storage backend
    // ==================================================================

    /**
     * Logs a generic action. Always written to the audit file. Optionally
     * mirrored to MySQL when {@code audit.persist.db=true}.
     */
    public void logAction(String entityName, String action, String performedBy, String details) {
        // pipe-delimited so the file is grep-friendly AND splittable for
        // any downstream parser (Splunk / Loki / awk).
        auditFileLogger.info("ACTION | entity={} | action={} | by={} | details={}",
                safe(entityName), safe(action), safe(performedBy), safe(details));

        if (!persistToDb) return;
        try {
            AuditLog log = new AuditLog();
            log.setEntityName(entityName);
            log.setAction(action);
            log.setPerformedBy(performedBy);
            log.setDetails(truncate(details, 2000));
            log.setTimestamp(Instant.now());
            auditLogRepository.save(log);
        } catch (Exception e) {
            // Never let audit-log persistence break the calling business
            // operation — file already has the event.
            logger.warn("DB audit-log write failed (file already captured the event): {}", e.getMessage());
        }
    }

    /**
     * Logs a status change. Always written to the audit file.
     */
    public void logStatusChange(String objectId, String serialNumber, String previousStatus,
                                String newStatus, String nodeType, String notes, String performedBy) {
        auditFileLogger.info("STATUS_CHANGE | entity={} | objectId={} | serial={} | {} -> {} | by={} | notes={}",
                safe(nodeType), safe(objectId), safe(serialNumber),
                safe(previousStatus), safe(newStatus), safe(performedBy), safe(notes));

        if (!persistToDb) return;
        try {
            AuditLog log = new AuditLog();
            log.setSerialNumber(serialNumber);
            log.setPreviousStatus(previousStatus);
            log.setNewStatus(newStatus);
            log.setNodeType(nodeType);
            log.setNotes(truncate(notes, 1000));
            log.setPerformedBy(performedBy);
            log.setAction("STATUS_CHANGE");
            log.setEntityName(nodeType);
            log.setDetails(truncate("Status changed from " + previousStatus + " to " + newStatus, 2000));
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("DB audit-log write failed (file already captured the event): {}", e.getMessage());
        }
    }

    // ==================================================================
    // QUERY API – kept for backwards compatibility.
    //
    // These continue to read from tb_AuditLog so historical data stays
    // accessible. Once the legacy table is dropped, these methods just
    // return empty lists / zero counts (the repository handles the
    // missing-table case).
    // ==================================================================

    public List<AuditLog> getAllLogs() {
        return auditLogRepository != null ? auditLogRepository.findAll() : Collections.emptyList();
    }
    public List<AuditLog> getLogsByEntityName(String entityName) {
        return auditLogRepository != null ? auditLogRepository.findByEntityName(entityName) : Collections.emptyList();
    }
    public List<AuditLog> getLogsByAction(String action) {
        return auditLogRepository != null ? auditLogRepository.findByAction(action) : Collections.emptyList();
    }
    public List<AuditLog> getLogsByObjectId(String assetId) {
        return auditLogRepository != null ? auditLogRepository.findByAssetId(assetId) : Collections.emptyList();
    }
    public List<AuditLog> getLogsBySerialNumber(String serialNumber) {
        return auditLogRepository != null ? auditLogRepository.findBySerialNumber(serialNumber) : Collections.emptyList();
    }
    public List<AuditLog> getLogsByPerformedBy(String performedBy) {
        return auditLogRepository != null ? auditLogRepository.findByPerformedBy(performedBy) : Collections.emptyList();
    }
    public List<AuditLog> getLogsByDateRange(Date startDate, Date endDate) {
        return auditLogRepository != null ? auditLogRepository.findByTimestampBetween(startDate, endDate)
                                          : Collections.emptyList();
    }

    /** Hard-deletes legacy DB rows older than {@code days}. Use as a one-off
     *  cleanup after migrating to file-based audit. */
    public int deleteOldLogs(int days) {
        if (auditLogRepository == null) return 0;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        return auditLogRepository.deleteByTimestampBefore(cal.getTime());
    }

    public long countTotalLogs() {
        return auditLogRepository != null ? auditLogRepository.count() : 0L;
    }
    public long countLogsByEntity(String entityName) {
        return auditLogRepository != null ? auditLogRepository.countByEntityName(entityName) : 0L;
    }
    public long countLogsByAction(String action) {
        return auditLogRepository != null ? auditLogRepository.countByAction(action) : 0L;
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private static String safe(String s) { return s == null ? "" : s.replace('|', '/'); }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
