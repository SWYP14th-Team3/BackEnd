package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    List<AnalysisResult> findAllByUserAndDeletedAtIsNullOrderByCreatedAtDesc(User user);

    @EntityGraph(attributePaths = "jobDescription")
    Page<AnalysisResult> findAllByUserAndDeletedAtIsNullAndJobDescription_CompanyNameContainingIgnoreCaseOrderByCreatedAtDesc(
            User user,
            String companyName,
            Pageable pageable
    );

    Optional<AnalysisResult> findByIdAndDeletedAtIsNull(Long id);

    Optional<AnalysisResult> findByIdAndUserAndDeletedAtIsNull(Long id, User user);
}
