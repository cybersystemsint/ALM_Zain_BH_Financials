package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.NELicense;

@Repository
public interface NELicenseRepository extends JpaRepository<NELicense, Long> {
    NELicense findByLicenseIdAndLicenseDetailAndNodeId(String licenseId, String licenseDetail, String nodeId);
    void deleteByLicenseIdAndLicenseDetailAndNodeId(String licenseId, String licenseDetail, String nodeId);
}
