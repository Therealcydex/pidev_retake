package tn.esprit.user.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Body of POST /auth/signup.
 *
 * Q: Why is there no `role` field here?
 * A: SECURITY. If the client could send its own role, anyone could register as
 *    {"role":"ADMIN"}. The role is imposed by the server (UserService sets TRAINEE).
 *    This is the textbook defence against mass assignment.
 *
 * Q: Why no @NoArgsConstructor like the response DTOs?
 * A: This class declares no constructor at all, so Java provides the implicit default
 *    one - which is what Jackson needs. The response DTOs lose it because
 *    @AllArgsConstructor defines an explicit constructor.
 *
 * NOTE (likely question): the fields carry no validation annotation. Adding
 * spring-boot-starter-validation would allow:
 *      @NotBlank @Size(min = 3, max = 30) private String username;
 *      @NotBlank @Email                   private String email;
 *      @NotBlank @Size(min = 8)           private String password;
 * combined with @Valid on the controller parameter. Without them, an empty username or
 * a one-character password is accepted.
 */
@Getter
@Setter
public class SignupRequest {
    private String username;
    private String email;
    private String password;
}
