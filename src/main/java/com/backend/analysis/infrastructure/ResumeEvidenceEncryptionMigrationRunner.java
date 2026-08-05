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
@ConditionalOnProperty(name = "resume.evidence.encryption.migrate-legacy", havingValue = "true")
public class ResumeEvidenceEncryptionMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<LegacyResumeEvidence> legacyRows = jdbcTemplate.query(
                """
                SELECT id, resume_evidence
                FROM requirement_evaluation
                WHERE resume_evidence IS NOT NULL
                  AND resume_evidence NOT LIKE 'enc:aes-gcm:v1:%'
                """,
                (rs, rowNum) -> new LegacyResumeEvidence(
                        rs.getLong("id"),
                        rs.getString("resume_evidence")
                )
        );

        for (LegacyResumeEvidence row : legacyRows) {
            jdbcTemplate.update(
                    "UPDATE requirement_evaluation SET resume_evidence = ? WHERE id = ?",
                    ResumeContentCrypto.encrypt(row.resumeEvidence()),
                    row.id()
            );
        }

        if (!legacyRows.isEmpty()) {
            log.info("Encrypted legacy requirement_evaluation.resume_evidence rows. count={}", legacyRows.size());
        }
    }

    private record LegacyResumeEvidence(Long id, String resumeEvidence) {
    }
}
