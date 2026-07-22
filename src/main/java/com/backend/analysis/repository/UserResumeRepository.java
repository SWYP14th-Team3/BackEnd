package com.backend.analysis.repository;

import com.backend.analysis.domain.UserResume;
import org.springframework.data.jpa.repository.JpaRepository;

// user_resume 테이블 CRUD
public interface UserResumeRepository extends JpaRepository<UserResume, Long> {
}
