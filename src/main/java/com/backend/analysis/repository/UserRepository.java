package com.backend.analysis.repository;

import com.backend.analysis.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

// user 테이블 CRUD
public interface UserRepository extends JpaRepository<User, Long> {
}
