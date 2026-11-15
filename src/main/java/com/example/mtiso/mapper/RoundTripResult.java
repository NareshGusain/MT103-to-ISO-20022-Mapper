package com.example.mtiso.mapper;

import com.example.mtiso.parser.MT103Message;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

/**
 * Encapsulates the complete result of an MT103 -> pacs.008 -> MT103 round-trip conversion,
 * including tag-by-tag audit diffs.
 */
@Schema(description = "Full outcome of a bidirectional MT103 -> pacs.008 -> MT103 round trip")
public record RoundTripResult(
        MT103Message originalMessage,
        String pacs008Xml,
        MT103Message reconstructedMessage,
        String reconstructedRawText,
        List<TagComparison> comparisons
) {
    public RoundTripResult {
        Objects.requireNonNull(originalMessage, "originalMessage must not be null");
        Objects.requireNonNull(comparisons, "comparisons must not be null");
        comparisons = List.copyOf(comparisons);
    }

    public boolean isFullyLossless() {
        return comparisons.stream().allMatch(c -> c.status() == TagComparisonStatus.EQUAL);
    }

    public long equalCount() {
        return comparisons.stream().filter(c -> c.status() == TagComparisonStatus.EQUAL).count();
    }

    public long changedCount() {
        return comparisons.stream().filter(c -> c.status() == TagComparisonStatus.CHANGED).count();
    }

    public long lostCount() {
        return comparisons.stream().filter(c -> c.status() == TagComparisonStatus.LOST).count();
    }

    public long unsupportedCount() {
        return comparisons.stream().filter(c -> c.status() == TagComparisonStatus.UNSUPPORTED).count();
    }

    public String markdownSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("### Round-Trip Verification Summary\n\n");
        sb.append(String.format("- **Overall Status:** %s\n", isFullyLossless() ? "**LOSSLESS**" : "**DATA LOSS / DIFFERENCES DETECTED**"));
        sb.append(String.format("- **Total Tags Evaluated:** %d\n", comparisons.size()));
        sb.append(String.format("- **Equal Tags:** %d\n", equalCount()));
        sb.append(String.format("- **Changed Tags:** %d\n", changedCount()));
        sb.append(String.format("- **Lost Tags:** %d\n", lostCount()));
        sb.append(String.format("- **Unsupported Tags:** %d\n\n", unsupportedCount()));

        sb.append("| Tag | Status | Original Value | Reconstructed Value | Notes |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- |\n");
        for (TagComparison c : comparisons) {
            String orig = c.originalValue() != null ? c.originalValue().replace("\r", "").replace("\n", "<br/>") : "-";
            String recon = c.reconstructedValue() != null ? c.reconstructedValue().replace("\r", "").replace("\n", "<br/>") : "-";
            sb.append(String.format("| `%s` | `%s` | `%s` | `%s` | %s |\n",
                    c.tag(), c.status(), orig, recon, c.details()));
        }
        return sb.toString();
    }
}

