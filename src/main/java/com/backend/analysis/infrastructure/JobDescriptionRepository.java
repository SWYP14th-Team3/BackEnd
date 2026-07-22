package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobDescriptionRepository extends JpaRepository<JobDescription, Long> {

    @Modifying
    @Query("delete from JobDescription jobDescription where jobDescription.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
