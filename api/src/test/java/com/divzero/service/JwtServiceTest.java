package com.divzero.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.divzero.config.AppProperties;
import com.divzero.entity.Role;
import com.divzero.entity.UserEntity;
import com.divzero.service.impl.JwtServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Plain JUnit: token signing and parsing need no Spring context. */
class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-0123456789";
    private static final String OTHER_SECRET = "a-completely-different-secret-0123456789ab";

    private static AppProperties properties(String secret, long expirationMs) {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret(secret);
        properties.getJwt().setExpirationMs(expirationMs);
        return properties;
    }

    private static JwtService serviceWith(String secret, long expirationMs) {
        return new JwtServiceImpl(properties(secret, expirationMs));
    }

    private static UserEntity user() {
        return UserEntity.builder()
            .id("user-123")
            .name("Ada")
            .username("ada")
            .password("hashed")
            .role(Role.USER)
            .build();
    }

    @Test
    @DisplayName("a generated token verifies and its subject is the user id")
    void generatedTokenVerifiesWithTheUserIdAsSubject() {
        JwtService service = serviceWith(SECRET, 60_000L);

        String token = service.generateToken(user());

        assertThat(service.verify(token))
            .isPresent()
            .get()
            .satisfies(decoded -> {
                assertThat(decoded.getSubject()).isEqualTo("user-123");
                assertThat(decoded.getClaim("id").asString()).isEqualTo("user-123");
                assertThat(decoded.getClaim("username").asString()).isEqualTo("ada");
            });
    }

    @Test
    @DisplayName("a token signed with a different secret fails verification")
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreignToken = serviceWith(OTHER_SECRET, 60_000L).generateToken(user());

        assertThat(serviceWith(SECRET, 60_000L).verify(foreignToken)).isEmpty();
    }

    @Test
    @DisplayName("an expired token fails verification")
    void expiredTokenIsRejected() {
        // A 1 ms lifetime: the token is already past its expiry by the time it is checked.
        JwtService service = serviceWith(SECRET, 1L);
        String token = service.generateToken(user());

        // java-jwt compares against whole seconds, so back-date via a negative-lifetime service
        // rather than sleeping (no Thread.sleep in these tests).
        JwtService backDated = serviceWith(SECRET, -60_000L);
        String alreadyExpired = backDated.generateToken(user());

        assertThat(service.verify(alreadyExpired)).isEmpty();
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("a tampered token fails verification")
    void tamperedTokenIsRejected() {
        JwtService service = serviceWith(SECRET, 60_000L);
        String token = service.generateToken(user());

        String tampered = token.substring(0, token.length() - 2) + "xy";

        assertThat(service.verify(tampered)).isEmpty();
    }

    @Test
    @DisplayName("extractToken handles Bearer, bearer, a bare token, null and blank")
    void extractTokenHandlesEveryHeaderShape() {
        JwtService service = serviceWith(SECRET, 60_000L);

        assertThat(service.extractToken("Bearer x")).isEqualTo("x");
        assertThat(service.extractToken("bearer x")).isEqualTo("x");
        assertThat(service.extractToken("BEARER x")).isEqualTo("x");
        assertThat(service.extractToken("x")).isEqualTo("x");
        assertThat(service.extractToken("  Bearer   x  ")).isEqualTo("x");
        assertThat(service.extractToken(null)).isNull();
        assertThat(service.extractToken("")).isNull();
        assertThat(service.extractToken("   ")).isNull();
    }

    @Test
    @DisplayName("verify rejects null and blank tokens without throwing")
    void verifyRejectsNullAndBlank() {
        JwtService service = serviceWith(SECRET, 60_000L);

        assertThat(service.verify(null)).isEmpty();
        assertThat(service.verify("")).isEmpty();
        assertThat(service.verify("   ")).isEmpty();
        assertThat(service.verify("not-a-jwt")).isEqualTo(Optional.empty());
    }
}
