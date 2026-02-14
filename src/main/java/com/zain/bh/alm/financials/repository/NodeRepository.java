package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.Node;

import java.util.List;

@Repository
public interface NodeRepository extends JpaRepository<Node, Integer> {
    List<Node> findBySerialNumber(String serialNumber);

    @Query("SELECT a FROM Node a WHERE a.serialNumber = :serialNumber")
    List<Node> findBySerialNumberJPQL(@Param("serialNumber") String serialNumber);
}
