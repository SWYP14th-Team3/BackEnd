package com.backend.user.infrastructure;

import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}