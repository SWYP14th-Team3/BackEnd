package com.backend.user.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!isMysqlDatabase()) {
                return;
            }

            if (isUsersEmailRequired()) {
                jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN email VARCHAR(100) NULL");
            }

            addColumnIfMissing("terms_agreed_at", "DATETIME(6) NULL");
            addColumnIfMissing("privacy_agreed_at", "DATETIME(6) NULL");
            addColumnIfMissing("terms_version", "VARCHAR(20) NULL");
            addColumnIfMissing("privacy_version", "VARCHAR(20) NULL");
        } catch (DataAccessException exception) {
            log.warn("users 테이블 스키마 보정에 실패했습니다.", exception);
        }
    }

    private void addColumnIfMissing(String columnName, String columnDefinition) {
        if (!isColumnMissing(columnName)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN " + columnName + " " + columnDefinition);
    }

    private boolean isMysqlDatabase() {
        String productName = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName());

        return productName != null
                && (productName.equalsIgnoreCase("MySQL") || productName.equalsIgnoreCase("MariaDB"));
    }

    private boolean isUsersEmailRequired() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'users'
                  AND COLUMN_NAME = 'email'
                  AND IS_NULLABLE = 'NO'
                """,
                Integer.class
        );

        return count != null && count > 0;
    }

    private boolean isColumnMissing(String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'users'
                  AND COLUMN_NAME = ?
                """,
                Integer.class,
                columnName
        );

        return count == null || count == 0;
    }
}
