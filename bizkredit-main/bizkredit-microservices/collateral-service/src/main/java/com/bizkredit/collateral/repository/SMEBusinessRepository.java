package com.bizkredit.collateral.repository;

import com.bizkredit.collateral.entity.SMEBusiness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Read access to the sme_business table (owned/written by sme-loan-service).
// collateral-service uses this when creating a FacilityAccount, which links
// directly to the business (not just via the application).
@Repository
public interface SMEBusinessRepository extends JpaRepository<SMEBusiness, Long> {
}
