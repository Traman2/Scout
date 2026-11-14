package com.scout.springserver.exception;

import com.scout.springserver.exception.DomainExceptions.AddressNotFoundException;
import com.scout.springserver.exception.DomainExceptions.EmailAlreadyExistsException;
import com.scout.springserver.exception.DomainExceptions.InvalidCredentialsException;
import com.scout.springserver.exception.DomainExceptions.NoGeocodedSpacesException;
import com.scout.springserver.exception.DomainExceptions.SpaceNotFoundException;
import com.scout.springserver.exception.DomainExceptions.UniversityAddressNotFoundException;
import com.scout.springserver.exception.DomainExceptions.UniversityNotConfiguredException;
import com.scout.springserver.exception.DomainExceptions.UserNotFoundException;
import com.scout.springserver.exception.DomainExceptions.ValidationException;
import com.scout.springserver.util.Bodies;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps service errors to the same status codes and bodies the Express
 * controllers produced (see their catch blocks and handleSpaceControllerError).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(e.getBody());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(ValidationException e) {
        return status(HttpStatus.BAD_REQUEST, Bodies.of("error", "Validation failed", "details", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadableBody(HttpMessageNotReadableException e) {
        return status(HttpStatus.BAD_REQUEST,
                Bodies.of("error", "Validation failed", "details", "Request body must be a valid JSON object"));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> emailExists() {
        return status(HttpStatus.CONFLICT, Bodies.of("error", "Email already exists"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> invalidCredentials() {
        return status(HttpStatus.UNAUTHORIZED, Bodies.of("error", "Invalid email or password"));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> userNotFound() {
        return status(HttpStatus.UNAUTHORIZED, Bodies.of("error", "Unauthorized", "message", "User not found"));
    }

    @ExceptionHandler(SpaceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> spaceNotFound() {
        return status(HttpStatus.NOT_FOUND, Bodies.of("error", "Space not found"));
    }

    @ExceptionHandler(AddressNotFoundException.class)
    public ResponseEntity<Map<String, Object>> addressNotFound() {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, Bodies.of(
                "error", "Address not found",
                "message", "Could not find coordinates for this address"));
    }

    @ExceptionHandler(UniversityAddressNotFoundException.class)
    public ResponseEntity<Map<String, Object>> universityAddressNotFound() {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, Bodies.of(
                "error", "Address not found",
                "message", "Could not find coordinates for this university address"));
    }

    @ExceptionHandler(UniversityNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> universityNotConfigured() {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, Bodies.of(
                "error", "University not configured",
                "message", "Add your university before calculating commutes"));
    }

    @ExceptionHandler(NoGeocodedSpacesException.class)
    public ResponseEntity<Map<String, Object>> noGeocodedSpaces() {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, Bodies.of(
                "error", "No geocoded spaces",
                "message", "Add or geocode an apartment before calculating commutes"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoResourceFoundException e) {
        return status(HttpStatus.NOT_FOUND, Bodies.of("error", "Not found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return status(HttpStatus.NOT_FOUND, Bodies.of("error", "Not found"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> unsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return status(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                Bodies.of("error", "Unsupported media type", "message", "Send the request body as JSON"));
    }

    /** Anything else — Express's `next(e)` fallthrough. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return status(HttpStatus.INTERNAL_SERVER_ERROR, Bodies.of("error", "Internal server error"));
    }

    private static ResponseEntity<Map<String, Object>> status(HttpStatus status, Map<String, Object> body) {
        return ResponseEntity.status(status).body(body);
    }
}
