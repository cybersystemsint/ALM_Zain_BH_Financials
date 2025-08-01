package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.UnmappedActiveInventory;
import com.telkom.co.ke.almoptics.entities.UnmappedITInventory;
import com.telkom.co.ke.almoptics.entities.UnmappedNELicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UnmappedLicenseRepository
        extends JpaRepository<UnmappedNELicense, Long>, JpaSpecificationExecutor<UnmappedNELicense> {

    Optional<UnmappedActiveInventory> findByElementID(String elementID);
}
