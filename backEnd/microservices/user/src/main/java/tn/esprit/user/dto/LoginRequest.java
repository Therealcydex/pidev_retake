package tn.esprit.user.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Body of POST /auth/login.
 *
 * Q: Why does the password travel in clear text in the JSON body?
 * A: It is not hashed client-side on purpose - the server needs the clear text to run
 *    BCrypt.matches() against the stored hash. Confidentiality is the job of the
 *    TRANSPORT: in production this endpoint must be served over HTTPS/TLS. Hashing in
 *    the browser would not help, since the hash would then become the password.
 */
@Getter
@Setter
public class LoginRequest {
    private String username;
    private String password;
}
