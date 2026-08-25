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
import tn.esprit.user.dto.LoginRequest;
import tn.esprit.user.dto.SignupRequest;
import tn.esprit.user.dto.UserResponse;
import tn.esprit.user.service.UserService;

/**
 * Public authentication endpoints. Declared permitAll() in SecurityConfig.
 *
 * Q: Difference between @RestController and @Controller?
 * A: @RestController = @Controller + @ResponseBody. The returned object is serialised
 *    straight to JSON by Jackson instead of being resolved as a view name (JSP/Thymeleaf).
 *
 * Q: What does @RequestMapping("/auth") on the class do?
 * A: It prefixes every method path, so @PostMapping("/login") answers POST /auth/login.
 *
 * Q: The gateway routes /users/** to this service - how is /auth reached?
 * A: Check the gateway routes: the /auth prefix must be forwarded too, otherwise the
 *    Angular app has to call the service directly on port 8024. Good question to verify
 *    in gateway/src/main/resources/application.properties before the defence.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * Q: Why 201 CREATED and not 200 OK?
     * A: REST convention - a POST that creates a new resource answers 201. Strictly the
     *    response should also carry a Location header pointing to the new resource
     *    (ResponseEntity.created(uri)).
     *
     * Q: What does @RequestBody do?
     * A: It tells Spring to deserialise the JSON body into a SignupRequest using Jackson.
     *    Jackson needs a no-arg constructor plus setters - which is exactly what Lombok
     *    @Setter provides on the DTO.
     *
     * NOTE (likely question): no @Valid here, so no server-side validation. See the note
     * in UserService.signup().
     */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
        AuthResponse created = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Q: Why is login a POST and not a GET?
     * A: A GET would put the password in the URL, where it lands in browser history,
     *    server access logs and the Referer header. Bodies of POST requests are not
     *    logged. A GET is also cacheable, which is unacceptable here.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    /**
     * "Who am I?" - used by the Angular app to restore the session on page reload.
     *
     * Q: Where does this Authentication parameter come from? Nothing passes it.
     * A: Spring injects it automatically from the SecurityContext, which JwtAuthFilter
     *    populated earlier in the chain. getName() returns the JWT "sub" claim.
     *
     * Q: Why not take the username as a parameter, like /auth/me?username=x?
     * A: Because a client could then read ANY profile by changing the value. Taking it
     *    from the verified token makes impersonation impossible.
     *
     * NOTE: /auth/** is permitAll(), so this method is reachable without a token - in
     * that case `authentication` is null and the call fails with a NullPointerException
     * (500) instead of a clean 401. Moving /auth/me out of the permitAll() block, or a
     * null check, would be the correct fix.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(userService.me(username));
    }
}
