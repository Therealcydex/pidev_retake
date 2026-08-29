package tn.esprit.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.user.dto.AuthResponse;
import tn.esprit.user.dto.LoginRequest;
import tn.esprit.user.dto.SignupRequest;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;
import tn.esprit.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the account rules. The repository, the password encoder and the
 * JWT service are mocked, so no MySQL and no Spring context are needed.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    private static SignupRequest signupRequest() {
        SignupRequest request = new SignupRequest();
        request.setUsername("wassim");
        request.setEmail("wassim@esprit.tn");
        request.setPassword("plain-password");
        return request;
    }

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    // ---------- signup ----------

    @Test
    void signupHashesThePasswordAndDefaultsToTrainee() {
        when(userRepository.existsByUsername("wassim")).thenReturn(false);
        when(userRepository.existsByEmail("wassim@esprit.tn")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User toSave = invocation.getArgument(0);
            toSave.setId(7L);
            return toSave;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("signed-jwt");

        AuthResponse response = userService.signup(signupRequest());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        // The raw password must never reach the database.
        assertEquals("hashed-password", saved.getValue().getPassword());
        assertNotEquals("plain-password", saved.getValue().getPassword());
        // A self-registered account must not be able to pick its own role.
        assertEquals(Role.TRAINEE, saved.getValue().getRole());

        assertEquals("signed-jwt", response.getToken());
        assertEquals(7L, response.getId());
        assertEquals(Role.TRAINEE, response.getRole());
    }

    @Test
    void signupRejectsADuplicateUsername() {
        when(userRepository.existsByUsername("wassim")).thenReturn(true);

        ResponseStatusException thrown =
            assertThrows(ResponseStatusException.class, () -> userService.signup(signupRequest()));

        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void signupRejectsADuplicateEmail() {
        when(userRepository.existsByUsername("wassim")).thenReturn(false);
        when(userRepository.existsByEmail("wassim@esprit.tn")).thenReturn(true);

        ResponseStatusException thrown =
            assertThrows(ResponseStatusException.class, () -> userService.signup(signupRequest()));

        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
    }

    // ---------- login ----------

    @Test
    void loginReturnsATokenForCorrectCredentials() {
        User stored = new User(3L, "wassim", "hashed-password", "wassim@esprit.tn", Role.TRAINER);
        when(userRepository.findByUsername("wassim")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("plain-password", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(stored)).thenReturn("signed-jwt");

        AuthResponse response = userService.login(loginRequest("wassim", "plain-password"));

        assertEquals("signed-jwt", response.getToken());
        assertEquals(Role.TRAINER, response.getRole());
    }

    @Test
    void loginWithAWrongPasswordIsUnauthorised() {
        User stored = new User(3L, "wassim", "hashed-password", "wassim@esprit.tn", Role.TRAINER);
        when(userRepository.findByUsername("wassim")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
            () -> userService.login(loginRequest("wassim", "wrong")));

        assertEquals(HttpStatus.UNAUTHORIZED, thrown.getStatusCode());
        verifyNoInteractions(jwtService);
    }

    @Test
    void loginWithAnUnknownUsernameIsUnauthorised() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
            () -> userService.login(loginRequest("ghost", "whatever")));

        // Same status as a wrong password: it must not reveal that the account exists.
        assertEquals(HttpStatus.UNAUTHORIZED, thrown.getStatusCode());
    }

    // ---------- last-admin guard ----------

    @Test
    void deletingTheLastAdminIsRefused() {
        User lastAdmin = new User(1L, "admin", "hashed", "admin@esprit.tn", Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(lastAdmin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        ResponseStatusException thrown =
            assertThrows(ResponseStatusException.class, () -> userService.delete(1L));

        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        verify(userRepository, never()).deleteById(1L);
    }

    @Test
    void anAdminCanBeDeletedWhenAnotherAdminRemains() {
        User admin = new User(1L, "admin", "hashed", "admin@esprit.tn", Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);

        userService.delete(1L);

        verify(userRepository).deleteById(1L);
    }

    // ---------- batch lookup used by the Formation service ----------

    @Test
    void getByIdsShortCircuitsOnAnEmptyList() {
        assertTrue(userService.getByIds(List.of()).isEmpty());
        assertTrue(userService.getByIds(null).isEmpty());

        // No pointless query when there is nothing to resolve.
        verifyNoInteractions(userRepository);
    }
}
