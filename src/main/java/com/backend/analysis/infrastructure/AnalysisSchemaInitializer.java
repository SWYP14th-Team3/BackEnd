package com.backend.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisSchemaInitializer implements ApplicationRunner {

    private static final List<LegacyColumn> LEGACY_ANALYSIS_RESULT_COLUMNS = List.of(
            new LegacyColumn("job_input_type", "ENUM('TEXT', 'URL') NULL"),
            new LegacyColumn("job_posting_raw", "TEXT NULL"),
            new LegacyColumn("resume_original_text", "TEXT NULL"),
            new LegacyColumn("resume_current_text", "TEXT NULL")
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!isMysqlDatabase()) {
                return;
            }

            for (LegacyColumn column : LEGACY_ANALYSIS_RESULT_COLUMNS) {
                if (isColumnRequired("analysis_result", column.name())) {
                    jdbcTemplate.execute("ALTER TABLE analysis_result MODIFY COLUMN "
                            + column.name()
                            + " "
                            + column.definition());
                }
            }
        } catch (DataAccessException exception) {
            log.warn("analysis_result 레거시 컬럼 nullable 보정에 실패했습니다.", exception);
        }
    }

    private boolean isMysqlDatabase() {
        String productName = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName());

        return productName != null
                && (productName.equalsIgnoreCase("MySQL") || productName.equalsIgnoreCase("MariaDB"));
    }

    private boolean isColumnRequired(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                  AND IS_NULLABLE = 'NO'
                """,
                Integer.class,
                tableName,
                columnName
        );

        return count != null && count > 0;
    }

    private record LegacyColumn(String name, String definition) {
    }
}
