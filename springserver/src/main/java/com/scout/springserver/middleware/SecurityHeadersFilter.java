package com.scout.springserver.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** The default response headers helmet() adds in backend/src/app.ts. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP = String.join(";",
            "default-src 'self'",
            "base-uri 'self'",
            "font-src 'self' https: data:",
            "form-action 'self'",
            "frame-ancestors 'self'",
            "img-src 'self' data:",
            "object-src 'none'",
            "script-src 'self'",
            "script-src-attr 'none'",
            "style-src 'self' https: 'unsafe-inline'",
            "upgrade-insecure-requests");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("Content-Security-Policy", CSP);
        res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        res.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        res.setHeader("Origin-Agent-Cluster", "?1");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-DNS-Prefetch-Control", "off");
        res.setHeader("X-Download-Options", "noopen");
        res.setHeader("X-Frame-Options", "SAMEORIGIN");
        res.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        res.setHeader("X-XSS-Protection", "0");
        chain.doFilter(req, res);
    }
}
