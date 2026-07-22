package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.UserResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserResumeRepository extends JpaRepository<UserResume, Long> {

    @Modifying
    @Query("delete from UserResume userResume where userResume.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
