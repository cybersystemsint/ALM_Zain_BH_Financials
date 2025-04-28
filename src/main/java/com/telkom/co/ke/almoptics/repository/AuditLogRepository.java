/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license/default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.models.AuditLog;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 *
 * @author Gilian
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityName(String entityName);

    List<AuditLog> findByAction(String action);

    List<AuditLog> findByAssetId(String assetId);

    List<AuditLog> findBySerialNumber(String serialNumber);

    List<AuditLog> findByPerformedBy(String performedBy);

    List<AuditLog> findByTimestampBetween(Date startDate, Date endDate);

    /**
     * Delete audit logs older than a specific date.
     *
     * @param cutoffDate The date before which logs should be deleted.
     * @return The number of records deleted.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoffDate")
    int deleteByTimestampBefore(@Param("cutoffDate") Date cutoffDate);

    /**
     * Count audit logs for a specific entity.
     *
     * @param entityName The name of the entity to count logs for.
     * @return The count of logs for the specified entity.
     */
    long countByEntityName(String entityName);

    /**
     * Count audit logs for a specific action.
     *
     * @param action The action to count logs for.
     * @return The count of logs for the specified action.
     */
    long countByAction(String action);


    /**
     * Find audit logs for a specific date range with additional filters.
     *
     * @param startDate    The start date of the range.
     * @param endDate      The end date of the range.
     * @param entityName   Optional filter for entity name (can be null).
     * @param action       Optional filter for action (can be null).
     * @param performedBy  Optional filter for the user who performed the action (can be null).
     * @param pageable     Pagination and sorting parameters.
     * @return List of matching audit logs.
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
            "a.timestamp BETWEEN :startDate AND :endDate " +
            "AND (:entityName IS NULL OR a.entityName = :entityName) " +
            "AND (:action IS NULL OR a.action = :action) " +
            "AND (:performedBy IS NULL OR a.performedBy = :performedBy)")
    List<AuditLog> findByDateRangeAndFilters(
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate,
            @Param("entityName") String entityName,
            @Param("action") String action,
            @Param("performedBy") String performedBy,
            Pageable pageable);

    /**
     * Find the most recent audit logs with pagination.
     *
     * @param pageable Pagination and sorting parameters.
     * @return List of the most recent audit logs.
     */
    @Query("SELECT a FROM AuditLog a ORDER BY a.timestamp DESC")
    List<AuditLog> findMostRecent(Pageable pageable);

    List<AuditLog> findByAssetIdOrSerialNumber(String siteId, String serialNumber, PageRequest changeDate);
}