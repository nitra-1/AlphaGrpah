package com.alphagraph.api.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PlatformUserRepository {

    public record PlatformUser(String email, String passwordHash, String role) {
    }

    private final JdbcTemplate jdbcTemplate;

    public PlatformUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PlatformUser> findActiveByEmail(String email) {
        List<PlatformUser> results = jdbcTemplate.query(
            "SELECT email, password_hash, role FROM api.platform_users WHERE email = ? AND active = true",
            (rs, rowNum) -> new PlatformUser(rs.getString("email"), rs.getString("password_hash"), rs.getString("role")),
            email
        );
        return results.stream().findFirst();
    }
}
