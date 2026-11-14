package com.scout.springserver.validation;

import com.scout.springserver.exception.DomainExceptions.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Small zod-like reader over a raw JSON body. Collects every problem, then
 * throwIfInvalid() reports them joined with ", " — the same shape as
 * formatValidationError in backend/src/utils/format.ts.
 */
final class BodyReader {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final Map<String, Object> body;
    private final List<String> errors = new ArrayList<>();

    BodyReader(Map<String, Object> body) {
        this.body = body == null ? Map.of() : body;
    }

    boolean has(String field) {
        return body.containsKey(field);
    }

    /** z.string().min(min).max(max), optionally .trim(). */
    String string(String field, int min, int max, boolean trim) {
        Object raw = body.get(field);
        if (!(raw instanceof String s)) {
            errors.add(field + ": Invalid input: expected string, received " + typeOf(raw));
            return null;
        }
        String value = trim ? s.trim() : s;
        if (value.length() < min) {
            errors.add(field + ": Too small: expected string to have >=" + min + " characters");
        } else if (value.length() > max) {
            errors.add(field + ": Too big: expected string to have <=" + max + " characters");
        }
        return value;
    }

    /** z.email().max(max).toLowerCase().trim(). */
    String email(String field, int max) {
        Object raw = body.get(field);
        if (!(raw instanceof String s)) {
            errors.add(field + ": Invalid input: expected string, received " + typeOf(raw));
            return null;
        }
        String value = s.trim().toLowerCase();
        if (!EMAIL.matcher(value).matches()) {
            errors.add(field + ": Invalid email address");
        } else if (value.length() > max) {
            errors.add(field + ": Too big: expected string to have <=" + max + " characters");
        }
        return value;
    }

    /** z.coerce.number().min(min).max(max). */
    Double number(String field, double min, double max) {
        double value = coerceNumber(body.get(field));
        if (!Double.isFinite(value)) {
            errors.add(field + ": Invalid input: expected number, received NaN");
            return null;
        }
        if (value < min) {
            errors.add(field + ": Too small: expected number to be >=" + format(min));
        } else if (value > max) {
            errors.add(field + ": Too big: expected number to be <=" + format(max));
        }
        return value;
    }

    /** z.coerce.number().int().min(min).max(max). */
    Integer integer(String field, int min, int max) {
        Double value = number(field, min, max);
        if (value == null) return null;
        if (value != Math.rint(value)) {
            errors.add(field + ": Invalid input: expected int, received number");
            return null;
        }
        return value.intValue();
    }

    void addError(String message) {
        errors.add(message);
    }

    void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join(", ", errors));
        }
    }

    /** JavaScript's Number(value), which is what z.coerce.number() applies. */
    static double coerceNumber(Object raw) {
        if (raw == null) return 0;
        if (raw instanceof Number n) return n.doubleValue();
        if (raw instanceof Boolean b) return b ? 1 : 0;
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) return 0;
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    private static String typeOf(Object raw) {
        if (raw == null) return "undefined";
        if (raw instanceof Number) return "number";
        if (raw instanceof Boolean) return "boolean";
        if (raw instanceof List<?>) return "array";
        if (raw instanceof Map<?, ?>) return "object";
        return raw.getClass().getSimpleName().toLowerCase();
    }

    private static String format(double n) {
        return n == Math.rint(n) ? String.valueOf((long) n) : String.valueOf(n);
    }
}
