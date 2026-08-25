package tn.esprit.user.config;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * Q: What happens if I delete this class?
 * A: Spring Boot auto-configuration takes over: EVERY endpoint becomes protected by a
 *    generated login form and a random password printed in the console. This class
 *    replaces that with stateless JWT security.
 *
 * Q: What does @EnableMethodSecurity do?
 * A: It activates the method-level annotations - @PreAuthorize / @PostAuthorize.
 *    Without it, the @PreAuthorize("hasRole('ADMIN')") on UserController would be
 *    SILENTLY IGNORED and any authenticated user could manage users. This is a classic
 *    exam trap: the annotation is there, but it does nothing unless enabled here.
 *
 * Q: Two levels of security - is that redundant?
 * A: No, they answer different questions:
 *      - the filter chain below = URL-based ("is this path public or authenticated?")
 *      - @PreAuthorize          = role-based ("does this user have the right role?")
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    /**
     * Q: Why expose the encoder as a @Bean instead of doing `new BCryptPasswordEncoder()`
     *    inside UserService?
     * A: So it is a single shared, injectable, swappable instance. To migrate to Argon2
     *    later you change this one line and the whole application follows. It is also what
     *    lets UserService depend on the PasswordEncoder INTERFACE rather than the
     *    implementation (dependency inversion).
     *
     * Q: Can I choose the strength?
     * A: Yes: new BCryptPasswordEncoder(12). Default is 10 = 2^10 rounds. Higher is safer
     *    but slower - it is a deliberate CPU cost to make brute force expensive.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Defines the security filter chain applied to every HTTP request.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Q: Why disable CSRF? Is that not a security hole?
            // A: CSRF attacks rely on the browser AUTOMATICALLY attaching credentials
            //    (a session cookie) to a forged cross-site request. Here the credential
            //    is a JWT that the Angular app must attach MANUALLY in the Authorization
            //    header, so a forged request from another site carries no token.
            //    CSRF protection would only add a useless token exchange for a REST API.
            //    It WOULD be required if the JWT were stored in a cookie.
            .csrf(csrf -> csrf.disable())

            // Q: What does STATELESS change?
            // A: Spring stops creating an HttpSession and stops storing the
            //    SecurityContext in it. Every request must therefore prove its identity
            //    again through the JWT. That is what makes the service horizontally
            //    scalable: any instance can serve any request, no sticky sessions.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // Public: you obviously cannot present a token before you have logged in.
                // This also covers /auth/me, which is only usable WITH a token - the
                // controller reads Authentication, which would be null without one.
                .requestMatchers("/auth/**").permitAll()

                // Public tooling: Swagger UI, OpenAPI spec, and the actuator endpoints
                // scraped by Prometheus. In production /actuator/** should be restricted.
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/actuator/**").permitAll()

                // Everything else needs a valid token. Order matters: the FIRST matching
                // rule wins, so anyRequest() must stay last.
                .anyRequest().authenticated()
            )

            // Q: Why addFilterBefore, and why before UsernamePasswordAuthenticationFilter
            //    specifically?
            // A: Spring Security is a CHAIN of filters. Our filter must run BEFORE the
            //    standard form-login filter so that, by the time authorization is checked,
            //    the SecurityContext is already populated from the JWT. Registering it
            //    after would be too late - the request would be rejected as anonymous.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
