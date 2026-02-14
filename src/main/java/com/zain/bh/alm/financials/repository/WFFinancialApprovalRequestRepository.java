package com.zain.bh.alm.financials.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.WFFinancialApprovalRequest;

import java.util.List;
import java.util.Optional;

@Repository
public interface WFFinancialApprovalRequestRepository extends JpaRepository<WFFinancialApprovalRequest, Integer> {
    List<WFFinancialApprovalRequest> findByUpdatedStatus(String status);
    Optional<WFFinancialApprovalRequest> findByAssetIdAndUpdatedStatus(String assetId, String status);
    List<WFFinancialApprovalRequest> findByAssetIdOrderByInsertDateDesc(String assetId);
    List<WFFinancialApprovalRequest> findByProcessIdOrderByInsertDateDesc(Integer processId);
    List<WFFinancialApprovalRequest> findByAssetId(String assetId);
    List<WFFinancialApprovalRequest> findByAssetIdInOrderByInsertDateDesc(List<String> assetIds);
    List<WFFinancialApprovalRequest> findByProcessIdAndUpdatedStatus(String processId, String status);
    Optional<WFFinancialApprovalRequest> findTopByAssetIdOrderByInsertDateDesc(String assetId);
    boolean existsByAssetIdAndUpdatedStatus(String assetId, String status);
    boolean existsByProcessId(Integer processId);

    Page<WFFinancialApprovalRequest> findByUpdatedStatus(String status, Pageable pageable);

    @Query("SELECT MAX(w.processId) FROM WFFinancialApprovalRequest w")
    Integer findMaxProcessId();
}
