package com.bizkredit.sme.repository;

import com.bizkredit.sme.entity.ApplicationDocument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

    @EntityGraph(attributePaths = {"application", "application.business"})
    Optional<ApplicationDocument> findById(Long id);

    // Plain lookup by id with NO forced association fetch - safe for
    // business-level KYC documents whose `application` is null (the
    // EntityGraph on findById force-joins application.business, which
    // breaks for those). Used by document download/view, which only
    // needs the document's own columns (filePath, originalFileName).
    java.util.Optional<ApplicationDocument> findByDocId(Long docId);

    @EntityGraph(attributePaths = {"application", "application.business"})
    List<ApplicationDocument> findByApplication_ApplicationId(Long applicationId);

    // Used by the KYC approval gate - checks for required document
    // types across ANY of a business's applications, since KYC is
    // verified once per business, not per individual application.
    @EntityGraph(attributePaths = {"application", "application.business"})
    List<ApplicationDocument> findByApplication_Business_BusinessId(Long businessId);

    // Documents attached DIRECTLY to a business (the new KYC-document
    // path - no application involved).
    @EntityGraph(attributePaths = {"business"})
    List<ApplicationDocument> findByBusiness_BusinessId(Long businessId);

    // All documents relevant to a business: those attached directly to
    // it (KYC docs) AND those attached via any of its applications
    // (financial docs). This is what the KYC gate and the Admin review
    // page use, so it doesn't matter which way a document was attached.
    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT d FROM ApplicationDocument d " +
        "LEFT JOIN FETCH d.business b " +
        "LEFT JOIN FETCH d.application a " +
        "LEFT JOIN a.business ab " +
        "WHERE b.businessId = :businessId OR ab.businessId = :businessId")
    List<ApplicationDocument> findAllForBusiness(
        @org.springframework.data.repository.query.Param("businessId") Long businessId);
}
