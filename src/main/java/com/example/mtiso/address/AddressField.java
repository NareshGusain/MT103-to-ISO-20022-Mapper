package com.example.mtiso.address;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Individual structured address element with an associated confidence score (0.0 to 1.0).
 */
@Schema(description = "Structured address field with extracted value and AI/rule confidence score")
public record AddressField(
        @Schema(description = "Extracted textual value of the field", example = "100 MAIN STREET")
        String value,

        @Schema(description = "Confidence level between 0.0 and 1.0", example = "0.95")
        double confidence
) {
    public AddressField {
        confidence = Math.clamp(confidence, 0.0, 1.0);
    }

    public static AddressField of(String value, double confidence) {
        return new AddressField(value, confidence);
    }

    public static AddressField empty() {
        return new AddressField(null, 0.0);
    }

    public boolean isPresent() {
        return value != null && !value.isBlank();
    }
}

