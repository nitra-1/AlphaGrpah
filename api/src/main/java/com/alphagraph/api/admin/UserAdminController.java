package com.alphagraph.api.admin;

import com.alphagraph.api.auth.PlatformUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-provisioned account creation (docs/006 - the user explicitly chose this over public
 * self-signup for a small, trusted user base). A USER account gets the same personal Portfolio/
 * Watchlist/Trade Journal as an ADMIN account - role only gates the ADMIN-only endpoints
 * (News Review, Add Instrument, Add Financial Data), never portfolio/watchlist access.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final PlatformUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminController(PlatformUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "List platform accounts")
    @GetMapping
    public List<UserSummaryDto> list() {
        return userRepository.listAll().stream().map(UserSummaryDto::from).toList();
    }

    @Operation(summary = "Provision a new account", description = "No self-signup exists - this is the only way a new account gets created.")
    @PostMapping
    public ResponseEntity<UserSummaryDto> create(@Valid @RequestBody CreateUserRequest request) {
        String hash = passwordEncoder.encode(request.password());
        UserSummaryDto created = userRepository.create(request.email(), hash, request.role())
            .map(UserSummaryDto::from)
            .orElseThrow(() -> new IllegalArgumentException(request.email() + " is already in use"));

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
