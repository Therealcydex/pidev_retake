package tn.esprit.formation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * permitAll here means "Spring Security decides nothing", not "anyone may do anything".
 *
 * This service holds no JWT secret and runs no authentication filter, so there is never a
 * principal for @PreAuthorize to read. Authorisation is explicit instead: every mutating
 * endpoint calls FormationAccessService, which resolves the caller through the USER
 * service and applies the ownership rule. Leaving the framework out of it keeps the token
 * secret in exactly one service.
 */
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // No cookies and no session: the token is sent explicitly on each call, so
            // there is no cross-site request forgery vector for CSRF tokens to close.
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
