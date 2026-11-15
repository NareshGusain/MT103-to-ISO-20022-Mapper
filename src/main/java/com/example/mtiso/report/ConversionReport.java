package com.example.mtiso.report;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Schema(description = "Comprehensive risk and data-loss report for an MT103 to ISO 20022 conversion")
public record ConversionReport(
        @Schema(description = "One-line operational verdict: SAFE, REVIEW, or BLOCK", example = "SAFE")
        ConversionVerdict verdict,

        @Schema(description = "The specific business or technical rule that decided the verdict", example = "SAFE: All fields mapped deterministically without data loss or schema errors.")
        String verdictRule,

        @Schema(description = "Summary counts of mapped, assumed, truncated, and unmapped fields")
        FieldSummary summary,

        @Schema(description = "List of fields where data was lost, unmapped, or shortened")
        List<DataLossItem> dataLoss,

        @Schema(description = "Warnings regarding party addresses (e.g. unstructured free-text lines)")
        List<String> addressWarnings,

        @Schema(description = "Human-readable conversion report formatted in GitHub Flavored Markdown")
        String markdown
) {
    public ConversionReport {
        Objects.requireNonNull(verdict, "verdict must not be null");
        Objects.requireNonNull(verdictRule, "verdictRule must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(markdown, "markdown must not be null");
        dataLoss = dataLoss != null ? List.copyOf(dataLoss) : Collections.emptyList();
        addressWarnings = addressWarnings != null ? List.copyOf(addressWarnings) : Collections.emptyList();
    }
}

