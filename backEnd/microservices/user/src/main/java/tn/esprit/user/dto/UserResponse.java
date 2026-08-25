package tn.esprit.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import tn.esprit.user.entity.Role;

/**
 * The public view of a user: AuthResponse without the token.
 * Returned by GET /auth/me and by every endpoint of UserController.
 *
 * This is the class that guarantees the password hash never reaches a client.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private Role role;
}
