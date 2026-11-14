package com.scout.springserver.exception;

import com.scout.springserver.util.Bodies;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Ends a request with a specific status and JSON body — the Spring equivalent
 * of `return res.status(422).json({ ... })` inside an Express controller.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, Object> body;

    public ApiException(HttpStatus status, Map<String, Object> body) {
        super(String.valueOf(body.get("error")));
        this.status = status;
        this.body = body;
    }

    public ApiException(HttpStatus status, String error, String message) {
        this(status, Bodies.of("error", error, "message", message));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getBody() {
        return body;
    }
}
