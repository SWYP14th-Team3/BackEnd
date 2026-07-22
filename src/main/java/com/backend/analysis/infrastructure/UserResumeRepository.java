package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.UserResume;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserResumeRepository extends JpaRepository<UserResume, Long> {
}
