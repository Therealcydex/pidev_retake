package tn.esprit.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the JWT logic. No Spring context and no database, so this runs
 * on a CI machine in milliseconds.
 */
class JwtServiceTest {

    private static String base64Secret(String raw) {
        // HS256 needs a key of at least 256 bits; the constructor expects Base64.
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(base64Secret("skillup-test-signing-secret-key-256-bits-long"));
    }

    private User trainer() {
        return new User(42L, "wassim", "hashed", "wassim@esprit.tn", Role.TRAINER);
    }

    @Test
    void generatedTokenCarriesUsernameAndRole() {
        String token = jwtService.generateToken(trainer());

        assertEquals("wassim", jwtService.extractUsername(token));
        assertEquals(Role.TRAINER, jwtService.extractRole(token));
    }

    @Test
    void freshTokenIsValid() {
        assertTrue(jwtService.isTokenValid(jwtService.generateToken(trainer())));
    }

    @Test
    void malformedTokenIsRejectedInsteadOfThrowing() {
        assertFalse(jwtService.isTokenValid("not-a-jwt"));
        assertFalse(jwtService.isTokenValid(""));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        // Simulates a forged token: right shape, wrong signature.
        JwtService attacker = new JwtService(base64Secret("a-completely-different-secret-key-256-bits"));
        String forged = attacker.generateToken(trainer());

        assertFalse(jwtService.isTokenValid(forged));
    }

    @Test
    void differentUsersGetDifferentTokens() {
        String trainerToken = jwtService.generateToken(trainer());
        String adminToken = jwtService.generateToken(
            new User(1L, "admin", "hashed", "admin@esprit.tn", Role.ADMIN));

        assertNotEquals(trainerToken, adminToken);
        assertEquals(Role.ADMIN, jwtService.extractRole(adminToken));
    }
}
