package tn.esprit.user.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.user.dto.AuthResponse;
import tn.esprit.user.dto.ForgotPasswordRequest;
import tn.esprit.user.dto.LoginRequest;
import tn.esprit.user.dto.ResetPasswordRequest;
import tn.esprit.user.dto.SignupRequest;
import tn.esprit.user.dto.UserResponse;
import tn.esprit.user.service.UserService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
        AuthResponse created = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    /**
     * Envoie un code de reinitialisation a l'adresse indiquee.
     *
     * Repond 200 dans tous les cas, y compris si l'adresse est inconnue : distinguer les
     * deux permettrait de deviner qui possede un compte.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request.getEmail());
        return ResponseEntity.ok().build();
    }

    /** Echange le code recu par mail contre un nouveau mot de passe. */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(userService.me(username));
    }
}
