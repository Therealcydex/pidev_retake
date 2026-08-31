package tn.esprit.user.service;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

import tn.esprit.user.dto.AuthResponse;
import tn.esprit.user.dto.LoginRequest;
import tn.esprit.user.dto.SignupRequest;
import tn.esprit.user.dto.UpdateUserRequest;
import tn.esprit.user.dto.UserResponse;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;
import tn.esprit.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {
    /** Duree de vie du code de reinitialisation. */
    private static final int VALIDITE_JETON_MINUTES = 15;

    private static final int LONGUEUR_MOT_DE_PASSE_MIN = 6;

    private static final SecureRandom ALEA = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;

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

        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setRole(Role.TRAINEE);

        User saved = userRepository.save(user);

        String token = jwtService.generateToken(saved);

        return new AuthResponse(
            token,
            saved.getId(),
            saved.getUsername(),
            saved.getEmail(),
            saved.getRole()
        );
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

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
     * Etape 1 — l'utilisateur a oublie son mot de passe.
     *
     * On tire un jeton aleatoire, on l'enregistre avec une date limite, et on l'envoie
     * a l'adresse mail du compte. Le mot de passe actuel n'est pas touche : tant que le
     * jeton n'a pas servi, l'ancien reste valable.
     *
     * La reponse est la meme que l'adresse existe ou non. Repondre « compte inconnu »
     * transformerait ce point d'entree en moyen de decouvrir qui est inscrit.
     */
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String jeton = genererJeton();
            user.setResetToken(jeton);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(VALIDITE_JETON_MINUTES));
            userRepository.save(user);

            mailService.envoyerJetonReinitialisation(email, jeton, VALIDITE_JETON_MINUTES);
        });
    }

    /**
     * Etape 2 — l'utilisateur revient avec le jeton recu par mail.
     *
     * Le jeton est efface des qu'il a servi : il ne vaut que pour un seul changement.
     */
    public void resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < LONGUEUR_MOT_DE_PASSE_MIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Le mot de passe doit faire au moins " + LONGUEUR_MOT_DE_PASSE_MIN + " caracteres");
        }

        User user = userRepository.findByResetToken(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Code de reinitialisation invalide"));

        if (user.getResetTokenExpiry() == null
                || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Code de reinitialisation expire");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    /**
     * Efface les codes de reinitialisation perimes. Appele par TokenCleanupJob.
     *
     * @Transactional est indispensable : une requete de modification JPQL echoue sans
     * transaction ouverte. Elle est ici, sur le service, et non sur le repository — la
     * limite d'une transaction est une decision metier.
     *
     * @return le nombre de comptes nettoyes
     */
    @Transactional
    public int purgerJetonsExpires(LocalDateTime maintenant) {
        return userRepository.purgerJetonsExpires(maintenant);
    }

    /**
     * Six chiffres, tires par SecureRandom — pas par Random, dont la suite est
     * previsible si l'on en connait quelques valeurs.
     */
    private String genererJeton() {
        return String.format("%06d", ALEA.nextInt(1_000_000));
    }

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

    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
            .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRole()))
            .toList();
    }

    /**
     * Resolves several users in one call. Formation uses it to turn a list of enrolled
     * user ids into names, instead of one Feign round-trip per enrolment.
     */
    public List<UserResponse> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(ids).stream()
            .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRole()))
            .toList();
    }

    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.getRole() != null && request.getRole() != Role.ADMIN) {
            requireNotLastAdmin(user);
        }

        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getUsername(), saved.getEmail(), saved.getRole());
    }

    public void delete(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        requireNotLastAdmin(user);

        userRepository.deleteById(id);
    }

    private void requireNotLastAdmin(User user) {
        if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove the last admin");
        }
    }
}
