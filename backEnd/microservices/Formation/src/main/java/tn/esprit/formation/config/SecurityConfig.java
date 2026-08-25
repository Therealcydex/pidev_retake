package tn.esprit.formation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security of the Formation microservice - deliberately wide open.
 *
 * Q: Why is this class here at all if everything is permitAll()?
 * A: Because spring-boot-starter-security is on the classpath (see pom.xml). Without this
 *    bean, Spring Boot auto-configuration would protect EVERY endpoint with a generated
 *    login form and a random password printed in the console, and the Angular app would
 *    receive 401 on every call. This configuration switches that default off.
 *
 * Q: So why is the User microservice secured and this one is not?
 * A: The intended design is "the gateway is the guard": the client authenticates against
 *    the User service, and the Gateway (port 9090) is supposed to validate the JWT before
 *    forwarding. The business services then trust what reaches them.
 *
 * NOTE (the most likely question on this file, and a REAL weakness):
 *    that reasoning only holds if the service is UNREACHABLE from outside. Here port 8084
 *    is open, so anyone can call
 *        curl -X DELETE http://localhost:8084/formations/1
 *    with no token at all and delete the data. The gateway is a front door on a house
 *    with no walls.
 *
 *    Two honest answers to give:
 *      1. Short term - copy the JwtAuthFilter of the user service into this one and
 *         protect the write operations:
 *             .requestMatchers(HttpMethod.GET, "/formations/**").permitAll()
 *             .requestMatchers("/formations/**").hasRole("ADMIN")
 *      2. Proper - make each microservice an OAuth2 RESOURCE SERVER validating the JWT
 *         with the shared secret. The dependencies are ALREADY in the pom
 *         (spring-boot-starter-oauth2-resource-server); they are simply not configured.
 *
 * Q: Why disable CSRF?
 * A: Same reason as in the user service: a stateless REST API consumed by a JavaScript
 *    client that sends its credentials in a header, not in an automatically-attached
 *    cookie. CSRF protection would only break the calls without adding safety.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // Everything is public: no authentication, no roles. See the notes above.
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
