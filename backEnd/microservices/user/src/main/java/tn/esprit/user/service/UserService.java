package tn.esprit.user.service;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import tn.esprit.user.dto.AuthResponse;
import tn.esprit.user.dto.LoginRequest;
import tn.esprit.user.dto.SignupRequest;
import tn.esprit.user.dto.UpdateUserRequest;
import tn.esprit.user.dto.UserResponse;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;
import tn.esprit.user.repository.UserRepository;

/**
 * Business layer: all the rules live here, the controller only maps HTTP.
 *
 * Q: Why a service layer at all - why not call the repository from the controller?
 * A: Separation of concerns (the 3-tier architecture of the course):
 *      Controller = HTTP (routes, status codes)
 *      Service    = business rules (duplicate check, hashing, token issuing)
 *      Repository = persistence
 *    It keeps the rules testable without HTTP and reusable from several controllers.
 *
 * Q: Where are the constructors? How are these three fields injected?
 * A: Lombok @RequiredArgsConstructor generates a constructor taking every `final`
 *    field, and Spring injects the beans through it. This is CONSTRUCTOR INJECTION,
 *    preferred over @Autowired on fields because the dependencies are immutable,
 *    explicit, and the class can be unit-tested with `new UserService(mock, mock, mock)`.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // the BCryptPasswordEncoder bean from SecurityConfig
    private final JwtService jwtService;

    /**
     * Registration.
     *
     * Q: Why 409 CONFLICT and not 400 BAD REQUEST for a duplicate?
     * A: The request is well-formed, so it is not a 400. 409 is the status meaning
     *    "the request conflicts with the current state of the resource".
     *
     * Q: What is ResponseStatusException?
     * A: A Spring exception carrying an HTTP status. Thrown from the service, it is
     *    caught by the default Spring handler and turned into that status. It avoids
     *    returning ResponseEntity everywhere. A cleaner alternative for a bigger project
     *    is a custom exception + @RestControllerAdvice (global exception handler).
     *
     * NOTE (likely question): the two exists() checks are not atomic with the save().
     * Under concurrency two identical signups can slip through - see the note in User.java.
     *
     * NOTE (likely question): there is NO input validation. An empty username or an
     * invalid email is accepted. The fix is @Valid on the controller parameter plus
     * @NotBlank / @Email on SignupRequest (spring-boot-starter-validation).
     */
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());

        // Q: Why encode() and not store the password directly?
        // A: BCrypt is a ONE-WAY hash: the clear text is never recoverable, so a database
        //    leak does not expose the passwords. BCrypt also generates a random SALT per
        //    password (two users with the same password get different hashes, which
        //    defeats rainbow tables) and is deliberately SLOW (work factor 10 by default),
        //    which makes brute force expensive. The salt is stored inside the hash string.
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Every new account is a TRAINEE. Promoting to ADMIN/TRAINER goes through
        // PUT /users/{id}, which is itself ADMIN-only. See the note in Role.java.
        user.setRole(Role.TRAINEE);

        User saved = userRepository.save(user);

        // Issue the token immediately so the user is logged in right after registering
        // (no second call to /auth/login needed from the Angular front-end).
        String token = jwtService.generateToken(saved);

        return new AuthResponse(
            token,
            saved.getId(),
            saved.getUsername(),
            saved.getEmail(),
            saved.getRole()
        );
    }

    /**
     * Authentication.
     *
     * Q: Why does "user not found" return exactly the same message as "wrong password"?
     * A: Deliberate. Distinct messages would allow USER ENUMERATION: an attacker could
     *    discover which usernames exist by comparing the responses. Both branches answer
     *    401 "Invalid credentials".
     *
     * Q: Why 401 UNAUTHORIZED and not 403 FORBIDDEN?
     * A: 401 = "I do not know who you are / your credentials are wrong".
     *    403 = "I know who you are, but you are not allowed to do this" (that is what
     *    @PreAuthorize produces in UserController).
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        // Q: Why matches() and not equals()?
        // A: BCrypt is one-way, so we cannot "decrypt" the stored hash to compare. matches()
        //    extracts the salt from the stored hash, re-hashes the submitted clear text with
        //    that same salt, and compares the results in constant time.
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(user);

        return new AuthResponse(
            token,
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole()
        );
    }

    /**
     * Serves GET /auth/me - "who am I?".
     * The username is not sent by the client: it is read from the JWT by JwtAuthFilter,
     * placed in the SecurityContext, and handed over by the controller. A client
     * therefore cannot ask for somebody else profile through this endpoint.
     */
    public UserResponse me(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole()
        );
    }

    /**
     * Q: Why map every User to a UserResponse instead of returning the entities?
     * A: Because User carries the password hash and Jackson would serialise it into the
     *    JSON response. The DTO exposes only what the client is allowed to see. It also
     *    decouples the API contract from the database schema and avoids infinite
     *    recursion once entities have bidirectional relations.
     *
     * NOTE: findAll() loads the whole table. With many users you would return a
     * Page<UserResponse> using findAll(Pageable) instead.
     */
    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
            .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRole()))
            .toList();
    }

    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    /**
     * Q: Why is the password absent from UpdateUserRequest?
     * A: Changing a password is a separate, more sensitive operation (it normally requires
     *    the current password). Leaving it out of this DTO prevents an admin edit from
     *    accidentally overwriting the hash.
     *
     * NOTE (likely question - this is a real weakness):
     *   1. No null check. If the JSON omits "email", request.getEmail() is null and this
     *      code ERASES the stored email. A PUT is a full replacement so it is defensible,
     *      but a PATCH-style "update only non-null fields" is usually what is wanted.
     *   2. No uniqueness check. Renaming a user to an existing username is accepted here,
     *      unlike in signup().
     */
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());

        // save() on an entity that already has an id performs an UPDATE, not an INSERT
        // (Hibernate decides via the primary key: null id -> persist, non-null -> merge).
        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getUsername(), saved.getEmail(), saved.getRole());
    }

    /**
     * Q: Why check existsById first - deleteById already ignores a missing row?
     * A: Precisely so that it does NOT fail silently. Without the check the API would
     *    answer 204 No Content for a user that never existed, which hides client bugs.
     *    Here a wrong id honestly returns 404.
     */
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        userRepository.deleteById(id);
    }
}
