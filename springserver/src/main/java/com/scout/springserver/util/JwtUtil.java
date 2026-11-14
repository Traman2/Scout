package com.scout.springserver.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.scout.springserver.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HS256 tokens with the same JWT_SECRET, claims ({ id, email, role }) and 1-day
 * expiry as backend/src/utils/jwt.ts, so tokens issued by either server are
 * accepted by the other.
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    // Must match the cookie maxAge in SignedCookies.
    public static final Duration EXPIRES_IN = Duration.ofDays(1);

    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtUtil(AppProperties props) {
        this.algorithm = Algorithm.HMAC256(props.jwtSecret());
        this.verifier = JWT.require(algorithm).build();
    }

    public String sign(int id, String email, String role) {
        try {
            Instant now = Instant.now();
            return JWT.create()
                    .withClaim("id", id)
                    .withClaim("email", email)
                    .withClaim("role", role)
                    .withIssuedAt(now)
                    .withExpiresAt(now.plus(EXPIRES_IN))
                    .sign(algorithm);
        } catch (RuntimeException e) {
            log.error("Failed to authenticate token", e);
            throw new IllegalStateException("Failed to authenticate token", e);
        }
    }

    public AuthUser verify(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            Integer id = jwt.getClaim("id").asInt();
            if (id == null) throw new JWTVerificationException("Token is missing the id claim");
            return new AuthUser(id, jwt.getClaim("email").asString(), jwt.getClaim("role").asString());
        } catch (JWTVerificationException e) {
            log.error("Failed to authenticate token: {}", e.getMessage());
            throw new IllegalArgumentException("Failed to authenticate token", e);
        }
    }
}
