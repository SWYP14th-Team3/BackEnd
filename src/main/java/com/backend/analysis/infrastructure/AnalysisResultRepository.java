package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    List<AnalysisResult> findAllByUserAndDeletedAtIsNullOrderByCreatedAtDesc(User user);

    Optional<AnalysisResult> findByIdAndDeletedAtIsNull(Long id);

    Optional<AnalysisResult> findByIdAndUserAndDeletedAtIsNull(Long id, User user);
}