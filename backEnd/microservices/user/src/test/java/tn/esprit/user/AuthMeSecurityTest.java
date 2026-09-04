package tn.esprit.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.user.config.JwtAuthFilter;
import tn.esprit.user.config.SecurityConfig;
import tn.esprit.user.controller.AuthController;
import tn.esprit.user.dto.UserResponse;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;
import tn.esprit.user.service.JwtService;
import tn.esprit.user.service.UserService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /auth/me is the identity lookup every other microservice depends on: Formation
 * resolves its caller through it before applying any ownership rule. It therefore has to
 * refuse an unauthenticated caller itself.
 *
 * It used to sit under a blanket `/auth/**` permitAll, so Spring Security waved the
 * request through and it failed inside the controller instead — a 500, which Formation
 * reported to the browser as 502 "User service unreachable". These tests pin the contract
 * that makes the 401 real.
 *
 * The web layer and the real security chain are loaded; only UserService is faked.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@TestPropertySource(properties = "jwt.secret=" + AuthMeSecurityTest.BASE64_SECRET)
class AuthMeSecurityTest {

    /** HS256 needs at least 256 bits, and JwtService decodes the property as Base64. */
    static final String BASE64_SECRET =
        "c2tpbGx1cC10ZXN0LXNpZ25pbmctc2VjcmV0LWtleS0yNTYtYml0cy1sb25n";

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;

    @MockBean private UserService userService;

    private static User trainer() {
        return new User(42L, "wassim", "hashed", "wassim@esprit.tn", Role.TRAINER);
    }

    @Test
    @DisplayName("no Authorization header is a 401, and never reaches the controller")
    void noTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());

        verify(userService, never()).me(any());
    }

    @Test
    @DisplayName("a malformed token is a 401")
    void malformedTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/auth/me").header("Authorization", "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized());

        verify(userService, never()).me(any());
    }

    @Test
    @DisplayName("a token signed with the wrong key is a 401")
    void foreignlySignedTokenIsUnauthorized() throws Exception {
        String otherSecret = Base64.getEncoder().encodeToString(
            "a-different-signing-secret-key-256-bits-long!!".getBytes(StandardCharsets.UTF_8));
        String forged = new JwtService(otherSecret).generateToken(trainer());

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + forged))
            .andExpect(status().isUnauthorized());

        verify(userService, never()).me(any());
    }

    @Test
    @DisplayName("a valid token returns the caller's own id, username and role")
    void validTokenReturnsTheCaller() throws Exception {
        when(userService.me("wassim"))
            .thenReturn(new UserResponse(42L, "wassim", "wassim@esprit.tn", Role.TRAINER));

        mvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + jwtService.generateToken(trainer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.username").value("wassim"))
            .andExpect(jsonPath("$.role").value("TRAINER"));
    }

    @Test
    @DisplayName("login stays public — the four entry points must not need a token")
    void loginStaysPublic() throws Exception {
        // 401 would mean the matcher change locked out the endpoints that hand out tokens.
        mvc.perform(get("/auth/login")).andExpect(status().isMethodNotAllowed());
    }
}
