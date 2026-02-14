package com.zain.bh.alm.financials.repository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.zain.bh.alm.financials.entity.AuditLog;

import java.util.Date;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityName(String entityName);
    List<AuditLog> findByAction(String action);
    List<AuditLog> findByAssetId(String assetId);
    List<AuditLog> findBySerialNumber(String serialNumber);
    List<AuditLog> findByPerformedBy(String performedBy);
    List<AuditLog> findByTimestampBetween(Date startDate, Date endDate);
    List<AuditLog> findByAssetIdOrSerialNumber(String siteId, String serialNumber, PageRequest changeDate);

    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoffDate")
    int deleteByTimestampBefore(@Param("cutoffDate") Date cutoffDate);

    long countByEntityName(String entityName);
    long countByAction(String action);

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

    @Query("SELECT a FROM AuditLog a ORDER BY a.timestamp DESC")
    List<AuditLog> findMostRecent(Pageable pageable);
}
