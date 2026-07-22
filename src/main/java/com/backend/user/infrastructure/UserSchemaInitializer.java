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
            if (!isMysqlDatabase() || !isUsersEmailRequired()) {
                return;
            }

            jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN email VARCHAR(100) NULL");
        } catch (DataAccessException exception) {
            log.warn("users.email 컬럼 nullable 보정에 실패했습니다.", exception);
        }
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
}
