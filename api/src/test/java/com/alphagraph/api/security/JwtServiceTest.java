package com.alphagraph.api.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-at-least-32-bytes-long-xxxx";

    @Test
    void issuedTokenValidatesBackToTheSamePrincipal() {
        JwtService service = new JwtService(SECRET, 60);

        String token = service.issueToken("admin@alphagraph.local", "ADMIN");
        Optional<JwtService.AuthenticatedPrincipal> principal = service.validate(token);

        assertThat(principal).isPresent();
        assertThat(principal.get().email()).isEqualTo("admin@alphagraph.local");
        assertThat(principal.get().role()).isEqualTo("ADMIN");
    }

    @Test
    void tamperedTokenFailsValidationWithoutThrowing() {
        JwtService service = new JwtService(SECRET, 60);
        String token = service.issueToken("admin@alphagraph.local", "ADMIN");

        // Flip a character safely inside the payload segment (a few characters past the first
        // '.'), not the last character of the whole token - a base64 segment's final character
        // can have "don't care" trailing bits, so tampering only that one occasionally decodes
        // to the same bytes as the original and the test flakes (seen in practice).
        int position = token.indexOf('.') + 5;
        char replacement = token.charAt(position) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, position) + replacement + token.substring(position + 1);

        assertThat(tampered).isNotEqualTo(token);
        assertThat(service.validate(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        JwtService issuer = new JwtService(SECRET, 60);
        JwtService verifier = new JwtService("a-completely-different-secret-32-bytes-min", 60);

        String token = issuer.issueToken("admin@alphagraph.local", "ADMIN");

        assertThat(verifier.validate(token)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        // 0-minute expiry plus a short sleep is enough to push "now" past the expiration instant.
        JwtService service = new JwtService(SECRET, 0);
        String token = service.issueToken("admin@alphagraph.local", "ADMIN");

        Thread.sleep(1000);

        assertThat(service.validate(token)).isEmpty();
    }

    @Test
    void garbageInputNeverThrows() {
        JwtService service = new JwtService(SECRET, 60);

        assertThat(service.validate("not-a-jwt")).isEmpty();
        assertThat(service.validate("")).isEmpty();
    }
}
