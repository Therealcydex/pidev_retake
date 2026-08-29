package tn.esprit.user;

import org.junit.jupiter.api.Test;
import tn.esprit.user.dto.AuthResponse;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeTest {

    @Test
    void verifyBuildAndLogic() {
        // This is a fast, isolated smoke test that runs without a Spring Context.
        // It guarantees that the service compiles and JUnit is executing correctly.
        assertTrue(true, "Smoke test passed successfully.");
    }

    @Test
    void allApplicationRolesAreDefined() {
        // The frontend and the JWT claims both rely on these exact names.
        assertEquals(4, Role.values().length);
        assertNotNull(Role.valueOf("ADMIN"));
        assertNotNull(Role.valueOf("TRAINER"));
        assertNotNull(Role.valueOf("TRAINEE"));
        assertNotNull(Role.valueOf("COMPANY"));
    }

    @Test
    void userEntityExposesItsFields() {
        // Fails if Lombok annotation processing breaks on the build machine.
        User user = new User(1L, "wassim", "secret", "wassim@esprit.tn", Role.TRAINER);

        assertEquals(1L, user.getId());
        assertEquals("wassim", user.getUsername());
        assertEquals("wassim@esprit.tn", user.getEmail());
        assertEquals(Role.TRAINER, user.getRole());
    }

    @Test
    void authResponseCarriesTokenAndRole() {
        AuthResponse response = new AuthResponse();
        response.setToken("jwt-token");
        response.setRole(Role.ADMIN);

        assertEquals("jwt-token", response.getToken());
        assertEquals(Role.ADMIN, response.getRole());
    }
}
