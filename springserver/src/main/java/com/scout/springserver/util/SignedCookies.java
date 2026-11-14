package com.scout.springserver.util;

import com.scout.springserver.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Signed cookies in the exact format cookie-parser / cookie-signature use
 * (backend/src/utils/cookies.ts): the stored value is
 * encodeURIComponent("s:" + value + "." + base64(HMAC-SHA256(COOKIE_SECRET, value)) without "=" padding).
 *
 * Keeping the format identical means a session cookie set by the Express
 * server keeps working against this one, and vice versa.
 */
@Component
public class SignedCookies {

    private static final Duration MAX_AGE = JwtUtil.EXPIRES_IN;

    private final byte[] secret;
    private final boolean secure;

    public SignedCookies(AppProperties props) {
        this.secret = props.cookieSecret().getBytes(StandardCharsets.UTF_8);
        this.secure = props.isProduction();
    }

    public void set(HttpServletResponse res, String name, String value) {
        String signed = "s:" + value + "." + signature(value);
        ResponseCookie cookie = baseCookie(name, URLEncoder.encode(signed, StandardCharsets.UTF_8))
                .maxAge(MAX_AGE)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse res, String name) {
        ResponseCookie cookie = baseCookie(name, "").maxAge(0).build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** req.signedCookies[name]: the unsigned value, or empty if missing or tampered with. */
    public Optional<String> get(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return unsign(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** Same as get(), but from a raw Cookie header (used by the Socket.IO handshake). */
    public Optional<String> getFromHeader(String cookieHeader, String name) {
        if (cookieHeader == null) return Optional.empty();
        for (String part : cookieHeader.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            if (name.equals(part.substring(0, eq).trim())) {
                String value = part.substring(eq + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return unsign(value);
            }
        }
        return Optional.empty();
    }

    private Optional<String> unsign(String rawValue) {
        String decoded;
        try {
            decoded = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!decoded.startsWith("s:")) return Optional.empty();

        String signed = decoded.substring(2);
        int dot = signed.lastIndexOf('.');
        if (dot < 0) return Optional.empty();

        String value = signed.substring(0, dot);
        String expected = signature(value);
        String actual = signed.substring(dot + 1);
        boolean valid = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
        return valid ? Optional.of(value) : Optional.empty();
    }

    private String signature(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest).replaceAll("=+$", "");
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Could not sign cookie", e);
        }
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/");
    }
}
