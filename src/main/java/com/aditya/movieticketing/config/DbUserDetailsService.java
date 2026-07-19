package com.aditya.movieticketing.config;

import com.aditya.movieticketing.domain.AppUser;
import com.aditya.movieticketing.repository.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads users from the database for HTTP Basic authentication. The {@code Role} enum maps to a
 * single {@code ROLE_*} authority so {@code @PreAuthorize("hasRole('CUSTOMER')")} works.
 */
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public DbUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .build();
    }
}
