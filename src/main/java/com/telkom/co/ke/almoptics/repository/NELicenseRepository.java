package com.telkom.co.ke.almoptics.repository;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.telkom.co.ke.almoptics.entities.NELicense;

public interface NELicenseRepository extends JpaRepository<NELicense, Long> {
    NELicense findByLicenseIdAndLicenseDetailAndNodeId(String licenseId, String licenseDetail, String nodeId);
    void deleteByLicenseIdAndLicenseDetailAndNodeId(String licenseId, String licenseDetail, String nodeId);}