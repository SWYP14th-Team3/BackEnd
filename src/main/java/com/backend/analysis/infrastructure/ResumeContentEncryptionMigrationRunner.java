package com.backend.analysis.infrastructure;

import com.backend.global.crypto.ResumeContentCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "resume.content.encryption.migrate-legacy", havingValue = "true")
public class ResumeContentEncryptionMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<LegacyResumeContent> legacyRows = jdbcTemplate.query(
                """
                SELECT id, resume_content
                FROM user_resume
                WHERE resume_content IS NOT NULL
                  AND resume_content NOT LIKE 'enc:aes-gcm:v1:%'
                """,
                (rs, rowNum) -> new LegacyResumeContent(
                        rs.getLong("id"),
                        rs.getString("resume_content")
                )
        );

        for (LegacyResumeContent row : legacyRows) {
            jdbcTemplate.update(
                    "UPDATE user_resume SET resume_content = ? WHERE id = ?",
                    ResumeContentCrypto.encrypt(row.resumeContent()),
                    row.id()
            );
        }

        if (!legacyRows.isEmpty()) {
            log.info("Encrypted legacy user_resume.resume_content rows. count={}", legacyRows.size());
        }
    }

    private record LegacyResumeContent(Long id, String resumeContent) {
    }
}
