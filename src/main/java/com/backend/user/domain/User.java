package com.backend.user.domain;

import com.backend.global.common.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_provider_provider_id", columnNames = {"provider", "provider_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(length = 50)
    private String name;

    @Column(name = "terms_agreed_at")
    private LocalDateTime termsAgreedAt;

    @Column(name = "privacy_agreed_at")
    private LocalDateTime privacyAgreedAt;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "privacy_version", length = 20)
    private String privacyVersion;

    @Builder
    private User(String email, Provider provider, String providerId, String name) {
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
        this.name = name;
    }

    public static User createSocialUser(String email, Provider provider, String providerId, String name) {
        return User.builder()
                .email(email)
                .provider(provider)
                .providerId(providerId)
                .name(name)
                .build();
    }

    public boolean isTermsRequired(String currentTermsVersion, String currentPrivacyVersion) {
        return termsAgreedAt == null
                || privacyAgreedAt == null
                || !Objects.equals(termsVersion, currentTermsVersion)
                || !Objects.equals(privacyVersion, currentPrivacyVersion);
    }

    public void agreeTerms(LocalDateTime agreedAt, String termsVersion, String privacyVersion) {
        this.termsAgreedAt = agreedAt;
        this.privacyAgreedAt = agreedAt;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
    }
}
