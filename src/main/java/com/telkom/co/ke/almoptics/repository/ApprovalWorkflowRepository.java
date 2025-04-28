package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.models.ApprovalWorkflow; // Assuming this is the correct entity class
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, Integer> {
    List<ApprovalWorkflow> findByUpdatedStatus(String status);
    Optional<ApprovalWorkflow> findByAssetIdAndUpdatedStatus(String assetId, String status);
    List<ApprovalWorkflow> findByAssetIdOrderByInsertDateDesc(String assetId);
    List<ApprovalWorkflow> findByProcessIdOrderByInsertDateDesc(Integer processId);
    List<ApprovalWorkflow> findByAssetId(String assetId);
    List<ApprovalWorkflow> findByAssetIdInOrderByInsertDateDesc(List<String> assetIds);
    List<ApprovalWorkflow> findByProcessIdAndUpdatedStatus(String processId, String status);
    Optional<ApprovalWorkflow> findTopByAssetIdOrderByInsertDateDesc(String assetId);
    boolean existsByAssetIdAndUpdatedStatus(String assetId, String status);

    // Added paginated method to fix the error
    Page<ApprovalWorkflow> findByUpdatedStatus(String status, Pageable pageable);

    @Query("SELECT MAX(w.processId) FROM ApprovalWorkflow w")
    Integer findMaxProcessId();

    boolean existsByProcessId(Integer processId);
}