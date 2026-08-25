package tn.esprit.user.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates and verifies JSON Web Tokens (library: io.jsonwebtoken / jjwt).
 *
 * Q: What is a JWT made of?
 * A: Three Base64-URL parts separated by dots:  header.payload.signature
 *      - header    : algorithm and type            {"alg":"HS256","typ":"JWT"}
 *      - payload   : the "claims" (see below)      {"sub":"wassim","role":"TRAINEE",...}
 *      - signature : HMAC-SHA256(header.payload, secret)
 *
 * Q: Is the token encrypted? Can I put the password in it?
 * A: NO. It is only SIGNED, not encrypted. Anyone can Base64-decode the payload and
 *    read it (try it on jwt.io). The signature guarantees INTEGRITY (nobody can modify
 *    the claims without the secret), not CONFIDENTIALITY. Never put a password or any
 *    secret data in a JWT.
 *
 * Q: Why use a token instead of an HTTP session?
 * A: The token is STATELESS: the server stores nothing. Any instance of any
 *    microservice can verify it with the shared secret, which is what makes horizontal
 *    scaling and a microservice architecture practical. A session would require sticky
 *    sessions or a shared session store (Redis).
 *
 * Q: What is the drawback of stateless tokens?
 * A: You CANNOT revoke one. If a token is stolen it stays valid until it expires —
 *    hence the short lifetime. Real systems add a blacklist or short-lived access
 *    tokens plus refresh tokens.
 */
@Service
public class JwtService {

    /**
     * Token lifetime: 24 hours, written as a readable calculation rather than
     * the magic number 86400000. Shorter = safer but forces frequent re-login.
     */
    private static final long EXPIRATION_MS = 1000L * 60 * 60 * 24;

    /** The HMAC key derived from the shared secret. Built once, reused for every token. */
    private final Key signingKey;

    /**
     * Q: Where does the secret come from?
     * A: @Value injects `jwt.secret` from application.properties, which itself reads the
     *    JWT_SECRET environment variable with a default value (12-factor style, so the
     *    secret can be changed in Docker without rebuilding).
     *
     * Q: Why Base64-decode it?
     * A: HS256 needs a key of at least 256 bits (32 bytes). Storing it Base64-encoded
     *    keeps the properties file free of special characters. Keys.hmacShaKeyFor throws
     *    WeakKeyException if the decoded key is shorter than 32 bytes.
     *
     * NOTE (likely question): the SAME secret must be configured in every microservice
     * that validates tokens, otherwise the signature check fails.
     */
    public JwtService(@Value("${jwt.secret}") String secret) {
        byte[] keyBytes = java.util.Base64.getDecoder().decode(secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Builds a signed token for a freshly authenticated user.
     *
     * Q: Why store the role INSIDE the token?
     * A: So that any service can authorize the request without a database round-trip.
     *    Trade-off: if an admin changes a user's role, the old token still carries the
     *    old role until it expires.
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name()); // custom (private) claim
        claims.put("userId", user.getId());        // custom claim, avoids a lookup by name

        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
            .setClaims(claims)
            .setSubject(user.getUsername()) // "sub" = standard claim identifying the principal
            .setIssuedAt(now)               // "iat"
            .setExpiration(expiry)          // "exp" — checked by isTokenValid()
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();                     // serialises to the final header.payload.signature string
    }

    /** Reads the "sub" claim — used by JwtAuthFilter to know WHO is calling. */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** Reads the custom "role" claim and converts it back to the enum. */
    public Role extractRole(String token) {
        String role = parseClaims(token).get("role", String.class);
        return Role.valueOf(role);
    }

    /**
     * Q: What exactly is validated here?
     * A: Two things. parseClaims() verifies the SIGNATURE (throws if the token was
     *    tampered with or signed with another secret) and jjwt also rejects an expired
     *    token by throwing ExpiredJwtException. The explicit getExpiration() check is a
     *    belt-and-braces re-verification.
     *
     * Q: Why catch a broad Exception and return false?
     * A: Any malformed / unsigned / expired token simply means "not authenticated".
     *    We deliberately do not leak WHY the token failed to the client.
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Central parsing point: verifies the signature with the shared key and returns
     * the payload. parseClaimsJws (Jws = signed) is what enforces the signature —
     * parseClaimsJwt (unsigned) would NOT, which is a classic JWT vulnerability.
     */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }
}
