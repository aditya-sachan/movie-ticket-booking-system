package com.aditya.movieticketing.config;

import com.aditya.movieticketing.domain.AppUser;
import com.aditya.movieticketing.domain.Role;
import com.aditya.movieticketing.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the demo users with real bcrypt hashes via the {@link PasswordEncoder}. The V3 migration
 * inserts the rows with placeholder hashes; this runner replaces those with encoded passwords on
 * first boot and is idempotent thereafter (it skips rows that already carry a bcrypt hash).
 *
 * <p>Demo credentials: {@code admin/admin123} (ADMIN), {@code alice/alice123} and
 * {@code bob/bob123} (CUSTOMER).
 */
@Component
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSeeder(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        upsert("admin", "admin123", Role.ADMIN);
        upsert("alice", "alice123", Role.CUSTOMER);
        upsert("bob", "bob123", Role.CUSTOMER);
    }

    private void upsert(String username, String rawPassword, Role role) {
        AppUser existing = appUserRepository.findByUsername(username).orElse(null);
        if (existing == null) {
            appUserRepository.save(new AppUser(username, passwordEncoder.encode(rawPassword), role, true));
            log.info("Seeded user '{}' ({})", username, role);
        } else if (!existing.getPasswordHash().startsWith("$2")) {
            existing.setPasswordHash(passwordEncoder.encode(rawPassword));
            log.info("Set bcrypt password for seeded user '{}'", username);
        }
    }
}
