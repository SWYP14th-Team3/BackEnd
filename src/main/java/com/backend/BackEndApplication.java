package com.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(
        basePackages = "com.backend",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.backend\\.analysis_20260710\\..*"
        )
)
@EntityScan(basePackages = "com.backend.analysis.domain")
@EnableJpaRepositories(basePackages = "com.backend.analysis.repository")
public class BackEndApplication {

    public static void main(String[] args) {
        // Spring Boot 애플리케이션 시작
        SpringApplication.run(BackEndApplication.class, args);
    }

}
