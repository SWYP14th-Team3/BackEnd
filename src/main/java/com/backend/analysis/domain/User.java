package com.backend.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    // 사용자 row의 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private User(String email, String provider, String providerId, String name) {
        // 로그인/회원 식별 정보를 저장
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
        this.name = name;
    }

    @PrePersist
    void prePersist() {
        // 가입 시간 저장
        this.createdAt = LocalDateTime.now();
    }
}
