package com.scout.springserver.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scout.springserver.util.Bodies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * requireAuth from backend/src/middleware/auth.middleware.ts. Registered in
 * WebConfig on the same paths the Express routers protect.
 */
@Component
public class RequireAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequireAuthInterceptor.class);

    private final ObjectMapper objectMapper;

    public RequireAuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        // CORS preflights carry no cookies; let Spring's CORS handling answer them.
        if (CorsUtils.isPreFlightRequest(req)) return true;

        if (req.getAttribute(IdentifyUserFilter.USER_ATTRIBUTE) != null) return true;

        if (req.getAttribute(IdentifyUserFilter.TOKEN_PRESENT_ATTRIBUTE) == null) {
            reject(res, Bodies.of("error", "Unauthorized", "message", "Authentication required"));
            return false;
        }

        log.warn("Rejected invalid or expired token: {}", req.getAttribute(IdentifyUserFilter.TOKEN_ERROR_ATTRIBUTE));
        reject(res, Bodies.of("error", "Unauthorized", "message", "Invalid or expired token"));
        return false;
    }

    private void reject(HttpServletResponse res, Map<String, Object> body) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(res.getOutputStream(), body);
    }
}
