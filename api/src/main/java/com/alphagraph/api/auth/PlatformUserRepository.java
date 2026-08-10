package com.alphagraph.api.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PlatformUserRepository {

    public record PlatformUser(UUID id, String email, String passwordHash, String role) {
    }

    public record UserSummary(UUID id, String email, String role, boolean active) {
    }

    private final JdbcTemplate jdbcTemplate;

    public PlatformUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PlatformUser> findActiveByEmail(String email) {
        List<PlatformUser> results = jdbcTemplate.query(
            "SELECT id, email, password_hash, role FROM api.platform_users WHERE email = ? AND active = true",
            (rs, rowNum) -> new PlatformUser(
                (UUID) rs.getObject("id"), rs.getString("email"), rs.getString("password_hash"), rs.getString("role")
            ),
            email
        );
        return results.stream().findFirst();
    }

    public List<UserSummary> listAll() {
        return jdbcTemplate.query(
            "SELECT id, email, role, active FROM api.platform_users ORDER BY email",
            (rs, rowNum) -> new UserSummary(
                (UUID) rs.getObject("id"), rs.getString("email"), rs.getString("role"), rs.getBoolean("active")
            )
        );
    }

    /** Empty if the email is already taken - admin-provisioned accounts (docs decision) need a clean conflict signal, not a raw constraint-violation 500. */
    public Optional<UserSummary> create(String email, String passwordHash, String role) {
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                "INSERT INTO api.platform_users (id, email, password_hash, role, active) VALUES (?, ?, ?, ?, true)",
                id, email, passwordHash, role
            );
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
        return Optional.of(new UserSummary(id, email, role, true));
    }
}
