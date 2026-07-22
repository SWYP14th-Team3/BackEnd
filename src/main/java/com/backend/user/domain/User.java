package com.backend.user.domain;

import com.backend.global.common.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
