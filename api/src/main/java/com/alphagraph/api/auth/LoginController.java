package com.alphagraph.api.auth;

import com.alphagraph.api.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private final PlatformUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long expiryMinutes;

    public LoginController(
        PlatformUserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
        @Value("${alphagraph.security.jwt-expiry-minutes:60}") long expiryMinutes
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.expiryMinutes = expiryMinutes;
    }

    @Operation(summary = "Issue a JWT for a platform user", description = "No refresh flow in Phase 0 - re-login on expiry.")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        PlatformUserRepository.PlatformUser user = userRepository.findActiveByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.issueToken(user.email(), user.role());
        return new LoginResponse(token, "Bearer", expiryMinutes * 60);
    }
}
