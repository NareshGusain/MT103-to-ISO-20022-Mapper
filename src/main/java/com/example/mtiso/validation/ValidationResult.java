package com.example.mtiso.validation;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable aggregation of validation findings produced by MT103Validator.
 */
public record ValidationResult(
        List<ValidationFinding> findings
) {

    public ValidationResult {
        findings = findings != null ? List.copyOf(findings) : List.of();
    }

    public static ValidationResult of(List<ValidationFinding> findings) {
        return new ValidationResult(findings);
    }

    public static ValidationResult valid() {
        return new ValidationResult(Collections.emptyList());
    }

    public boolean isValid() {
        return findings.stream().noneMatch(ValidationFinding::isError);
    }

    public boolean hasErrors() {
        return !isValid();
    }

    public boolean hasWarnings() {
        return findings.stream().anyMatch(ValidationFinding::isWarning);
    }

    public List<ValidationFinding> getErrors() {
        return findings.stream().filter(ValidationFinding::isError).toList();
    }

    public List<ValidationFinding> getWarnings() {
        return findings.stream().filter(ValidationFinding::isWarning).toList();
    }
}

