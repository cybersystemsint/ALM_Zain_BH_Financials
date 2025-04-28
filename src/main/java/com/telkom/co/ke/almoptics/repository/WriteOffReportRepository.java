package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.WriteOffReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.Optional;

public interface WriteOffReportRepository extends JpaRepository<WriteOffReport, Long> {

    Optional<WriteOffReport> findBySerialNumber(String serialNumber);
    Optional<WriteOffReport> findByAssetId(String assetId);

    @Query("SELECT w FROM WriteOffReport w WHERE " +
            "(:serialNumber IS NULL OR w.serialNumber LIKE %:serialNumber%) " +
            "AND (:rfid IS NULL OR w.rfid LIKE %:rfid%) " +
            "AND (:tag IS NULL OR w.tag LIKE %:tag%) " +
            "AND (:assetType IS NULL OR w.assetType LIKE %:assetType%) " +
            "AND (:assetId IS NULL OR w.assetId LIKE %:assetId%) " +
            "AND (:neType IS NULL OR w.neType LIKE %:neType%) " +
            "AND (:statusFlag IS NULL OR w.statusFlag = :statusFlag) " +
            "AND (:startDate IS NULL OR w.writeOffDate >= :startDate) " +
            "AND (:endDate IS NULL OR w.writeOffDate <= :endDate)")
    Page<WriteOffReport> findWithFilters(
            @Param("serialNumber") String serialNumber,
            @Param("rfid") String rfid,
            @Param("tag") String tag,
            @Param("assetType") String assetType,
            @Param("assetId") String assetId,
            @Param("neType") String neType,
            @Param("statusFlag") String statusFlag,
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate,
            Pageable pageable);
    @Query("SELECT w FROM WriteOffReport w WHERE " +
            "LOWER(w.serialNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(w.rfid) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(w.tag) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(w.assetType) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(w.assetId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(w.neType) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(w.statusFlag) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(w.insertedBy) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<WriteOffReport> search(@Param("query") String query, Pageable pageable);
}