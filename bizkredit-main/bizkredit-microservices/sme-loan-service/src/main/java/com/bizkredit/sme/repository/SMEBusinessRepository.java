package com.bizkredit.sme.repository;

import com.bizkredit.sme.entity.SMEBusiness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SMEBusinessRepository extends JpaRepository<SMEBusiness, Long> {

    Optional<SMEBusiness> findByRegistrationNumber(String registrationNumber);

    List<SMEBusiness> findByStatus(String status);

    List<SMEBusiness> findByIndustry(String industry);

    // The businesses registered by a given applicant - for the
    // applicant's own "my businesses" listing.
    List<SMEBusiness> findByApplicantUserId(Long applicantUserId);

    boolean existsByRegistrationNumber(String registrationNumber);
}
