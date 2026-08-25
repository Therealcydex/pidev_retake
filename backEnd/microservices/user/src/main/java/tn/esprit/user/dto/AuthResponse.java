package tn.esprit.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import tn.esprit.user.entity.Role;

/**
 * Returned by POST /auth/signup and POST /auth/login.
 * The Angular app stores the token in localStorage and replays it in the
 * Authorization header of every later request.
 *
 * Q: Why return the user's details next to the token, since they are already inside it?
 * A: To spare the front-end from decoding the JWT just to display a username. It is
 *    convenience, not security: the authoritative copy is the signed token.
 *
 * NOTE: no password field, by construction. See package-info.java.
 */
@Getter
@Setter
@NoArgsConstructor  // required by Jackson for deserialisation - see package-info.java
@AllArgsConstructor // used by UserService to build the response in one call
public class AuthResponse {
    private String token;
    private Long id;
    private String username;
    private String email;
    private Role role;
}
