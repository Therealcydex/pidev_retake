package tn.esprit.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tn.esprit.user.entity.Role;
import tn.esprit.user.service.JwtService;

import java.io.IOException;
import java.util.List;

/**
 * The piece that turns a JWT into a Spring Security identity, on every request.
 *
 * Full request flow (worth being able to recite):
 *   Angular sends  Authorization: Bearer eyJhbGciOi...
 *     -> Gateway (9090) routes /users/** to this service (8024)
 *     -> THIS FILTER reads the header, verifies the signature, extracts username + role
 *     -> it stores an Authentication object in the SecurityContext
 *     -> the filter chain continues; @PreAuthorize and authorizeHttpRequests now have
 *        an authenticated principal to check
 *     -> the controller runs
 *
 * Q: Why extend OncePerRequestFilter rather than implement Filter?
 * A: It guarantees the filter executes EXACTLY ONCE per request. A plain Filter can be
 *    invoked several times for the same request (forwards, error dispatch, async
 *    dispatch), which would redo the JWT parsing needlessly.
 *
 * Q: How does this filter reach the Spring Security chain?
 * A: @Component makes it a bean, and SecurityConfig injects it and registers it with
 *    .addFilterBefore(...).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No token at all -> stay anonymous and let the chain decide.
        // Q: Why not answer 401 immediately here?
        // A: Because some routes are public (/auth/login, /auth/signup, Swagger). This
        //    filter only AUTHENTICATES; deciding what requires a login is the job of
        //    authorizeHttpRequests in SecurityConfig. Mixing the two here would break
        //    every public endpoint.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // "Bearer " is 7 characters - skip the scheme and keep the token itself.
        // "Bearer" is the standard HTTP auth scheme for tokens (RFC 6750): it means
        // "whoever bears this token gets access", hence the importance of HTTPS.
        String token = authHeader.substring(7);

        // Signature invalid, token expired or malformed -> ignore it, remain anonymous.
        // The request then gets a 401/403 from the authorization rules if the route is
        // protected. We never explain WHY the token was refused (no information leak).
        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = jwtService.extractUsername(token);
        Role role = jwtService.extractRole(token);

        // Q: Why is the second argument (credentials) null?
        // A: It is where the password would go during a form login. Here the token has
        //    already proved the identity, so there is nothing left to verify - and we
        //    certainly do not want a password sitting in memory.
        //
        // Q: Why the "ROLE_" prefix?
        // A: Spring Security convention. hasRole('ADMIN') internally looks for the
        //    authority "ROLE_ADMIN". Forgetting this prefix is the single most common
        //    cause of an unexplained 403: the token is valid, the role looks right, but
        //    hasRole() never matches. The alternative is hasAuthority('ADMIN') with no
        //    prefix - pick one convention and stay consistent.
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            username,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );

        // Q: What is the SecurityContextHolder?
        // A: A ThreadLocal holding the current user for the duration of the request.
        //    Because it is bound to the thread, anything downstream can call
        //    SecurityContextHolder.getContext().getAuthentication() without passing the
        //    user around - that is how AuthController receives its Authentication
        //    parameter, and how @PreAuthorize knows who is calling.
        //    It is cleared automatically at the end of the request.
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Hand over to the next filter. Forgetting this line makes the request hang /
        // return an empty response - the controller is never reached.
        filterChain.doFilter(request, response);
    }
}
