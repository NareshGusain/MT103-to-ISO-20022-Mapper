package com.example.mtiso.mapper;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Audit record comparing an individual tag between original and reconstructed MT103.
 */
@Schema(description = "Tag-by-tag comparison item recording original value, reconstructed value, and status")
public record TagComparison(
        @Schema(description = "SWIFT tag identifier, e.g. ':20:' or ':32A:'", example = ":20:")
        String tag,

        @Schema(description = "Comparison outcome: EQUAL, CHANGED, LOST, or UNSUPPORTED", example = "EQUAL")
        TagComparisonStatus status,

        @Schema(description = "Original value from the incoming MT103 message", example = "SYNTH2026111501")
        String originalValue,

        @Schema(description = "Reconstructed value produced from pacs.008 reverse mapping (null if LOST or UNSUPPORTED)", example = "SYNTH2026111501")
        String reconstructedValue,

        @Schema(description = "Human-readable explanation of the comparison result", example = "Field value preserved losslessly across round-trip conversion")
        String details
) {
    public TagComparison {
        Objects.requireNonNull(tag, "tag must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static TagComparison equal(String tag, String value, String details) {
        return new TagComparison(tag, TagComparisonStatus.EQUAL, value, value, details);
    }

    public static TagComparison changed(String tag, String origVal, String reconVal, String details) {
        return new TagComparison(tag, TagComparisonStatus.CHANGED, origVal, reconVal, details);
    }

    public static TagComparison lost(String tag, String origVal, String details) {
        return new TagComparison(tag, TagComparisonStatus.LOST, origVal, null, details);
    }

    public static TagComparison unsupported(String tag, String origVal, String details) {
        return new TagComparison(tag, TagComparisonStatus.UNSUPPORTED, origVal, null, details);
    }
}

