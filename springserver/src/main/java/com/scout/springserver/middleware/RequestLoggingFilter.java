package com.scout.springserver.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** morgan('combined') access logging from backend/src/app.ts, written through the app logger. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http");
    private static final DateTimeFormatter CLF =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            String uri = req.getRequestURI() + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
            log.info("{} - - [{}] \"{} {} {}\" {} - \"{}\" \"{}\" {}ms",
                    req.getRemoteAddr(),
                    CLF.format(ZonedDateTime.now()),
                    req.getMethod(),
                    uri,
                    req.getProtocol(),
                    res.getStatus(),
                    orDash(req.getHeader("Referer")),
                    orDash(req.getHeader("User-Agent")),
                    (System.nanoTime() - startedAt) / 1_000_000);
        }
    }

    private static String orDash(String value) {
        return value == null ? "-" : value;
    }
}
