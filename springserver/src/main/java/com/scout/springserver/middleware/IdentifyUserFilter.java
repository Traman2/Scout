package com.scout.springserver.middleware;

import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.JwtUtil;
import com.scout.springserver.util.SignedCookies;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * identifyUser from backend/src/middleware/auth.middleware.ts: populates the
 * current user from the signed `token` cookie when present and valid, without
 * blocking the request. RequireAuthInterceptor is what actually enforces auth.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class IdentifyUserFilter extends OncePerRequestFilter {

    /** Request attribute holding the AuthUser (req.user). */
    public static final String USER_ATTRIBUTE = "scout.authUser";
    /** Request attribute: was a token cookie sent at all (to pick the right 401 message). */
    public static final String TOKEN_PRESENT_ATTRIBUTE = "scout.tokenPresent";
    /** Request attribute: why a present token was rejected, for logging. */
    public static final String TOKEN_ERROR_ATTRIBUTE = "scout.tokenError";

    private final SignedCookies cookies;
    private final JwtUtil jwt;

    public IdentifyUserFilter(SignedCookies cookies, JwtUtil jwt) {
        this.cookies = cookies;
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> token = cookies.get(req, "token");

        if (token.isPresent()) {
            req.setAttribute(TOKEN_PRESENT_ATTRIBUTE, Boolean.TRUE);
            try {
                AuthUser user = jwt.verify(token.get());
                req.setAttribute(USER_ATTRIBUTE, user);
            } catch (RuntimeException e) {
                // Not an error here — the request just continues unauthenticated.
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                req.setAttribute(TOKEN_ERROR_ATTRIBUTE, cause.getMessage());
            }
        }

        chain.doFilter(req, res);
    }
}
