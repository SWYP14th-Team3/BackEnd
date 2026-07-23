package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.JobPostingImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobPostingImageRepository extends JpaRepository<JobPostingImage, Long> {

    @Modifying
    @Query("""
            delete from JobPostingImage image
            where image.jobDescription.id in (
                select jobDescription.id
                from JobDescription jobDescription
                where jobDescription.user.id = :userId
            )
            """)
    void deleteAllByUserId(@Param("userId") Long userId);
}
