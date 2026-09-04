package tn.esprit.user.config;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http

            .csrf(csrf -> csrf.disable())

            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth

                // Les quatre points d'entree publics : on ne peut pas exiger un jeton
                // pour les obtenir. /auth/me n'est deliberement pas dans la liste --
                // c'est la resolution d'identite sur laquelle s'appuient les autres
                // services, donc elle doit repondre 401 sans jeton valide au lieu de
                // laisser passer la requete jusqu'au controleur.
                .requestMatchers("/auth/signup", "/auth/login",
                                 "/auth/forgot-password", "/auth/reset-password").permitAll()

                // Quand un controleur leve une exception, Spring reachemine la requete
                // vers /error. Spring Security filtre aussi ce reacheminement : sans
                // cette ligne, /error est refuse et le client recoit un 403 vide a la
                // place du vrai code (401 identifiants invalides, 400 code expire...).
                .requestMatchers("/error").permitAll()

                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/actuator/**").permitAll()

                .anyRequest().authenticated()
            )

            // Sans point d'entree explicite, Spring refuse un appel anonyme avec 403.
            // Or 403 signifie « authentifie mais pas autorise » : l'absence de jeton est
            // un 401. Les autres services s'appuient sur cette distinction.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
