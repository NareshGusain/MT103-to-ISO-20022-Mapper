package com.example.mtiso.validation;

import java.util.Objects;

/**
 * Immutable validation finding representing an error or warning encountered on a SWIFT tag.
 */
public record ValidationFinding(
        Severity severity,
        String code,
        String tag,
        String message
) {

    public ValidationFinding {
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(tag, "tag must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static ValidationFinding error(String code, String tag, String message) {
        return new ValidationFinding(Severity.ERROR, code, tag, message);
    }

    public static ValidationFinding warning(String code, String tag, String message) {
        return new ValidationFinding(Severity.WARNING, code, tag, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
    }
}

