package com.scout.springserver.exception;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.scout.springserver.util.Bodies;

/**
 * Turns exceptions into the same status codes and JSON bodies the Express
 * controllers returned (their catch blocks and handleSpaceControllerError).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- 400: validation (zod safeParse failures) --------------------------------

    /** A @Validated @RequestBody DTO failed its constraints. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException e) {
        return validationFailed(e.getBindingResult().getAllErrors());
    }

    /** Constraint on a path variable (e.g. @Positive id) or body, via Spring's built-in method validation. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> invalidArguments(HandlerMethodValidationException e) {
        return validationFailed(e.getAllErrors());
    }

    /** `/api/spaces/abc` — id isn't a number. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return status(HttpStatus.BAD_REQUEST, Bodies.of(
                "error", "Validation failed",
                "details", e.getName() + " must be a valid number"));
    }

    /** Missing or malformed JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadableBody(HttpMessageNotReadableException e) {
        return status(HttpStatus.BAD_REQUEST, Bodies.of(
                "error", "Validation failed",
                "details", "Request body must be a valid JSON object"));
    }

    // ---- auth ----------------------------------------------------------------------

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

    // ---- spaces ----------------------------------------------------------------------

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

    @ExceptionHandler(MissingCoordinatesException.class)
    public ResponseEntity<Map<String, Object>> missingCoordinates(MissingCoordinatesException e) {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, Bodies.of(
                "error", "Missing coordinates",
                "message", e.getMessage()));
    }

    @ExceptionHandler(NoPoisException.class)
    public ResponseEntity<Map<String, Object>> noPois() {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, Bodies.of(
                "error", "No POIs",
                "message", "Discover POIs before computing routes"));
    }

    // ---- university ------------------------------------------------------------------

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

    // ---- framework / fallthrough -------------------------------------------------------

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<Map<String, Object>> notFound(Exception e) {
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

    /** 400 { error: 'Validation failed', details: 'msg, msg' } — formatValidationError in utils/format.ts. */
    private static ResponseEntity<Map<String, Object>> validationFailed(
            List<? extends MessageSourceResolvable> errors) {
        String details = errors.stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        return status(HttpStatus.BAD_REQUEST, Bodies.of(
                "error", "Validation failed",
                "details", details.isEmpty() ? "Validation failed" : details));
    }

    private static ResponseEntity<Map<String, Object>> status(HttpStatus status, Map<String, Object> body) {
        return ResponseEntity.status(status).body(body);
    }
}
