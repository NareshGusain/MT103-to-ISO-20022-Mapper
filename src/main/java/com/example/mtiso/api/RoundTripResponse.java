package com.example.mtiso.api;

import com.example.mtiso.mapper.RoundTripResult;
import com.example.mtiso.mapper.TagComparison;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * REST response payload summarizing round-trip fidelity, tag diffs, and reconstructed MT103 text.
 */
@Schema(description = "Audit payload for bidirectional MT103 -> pacs.008 -> MT103 round trip")
public record RoundTripResponse(
        @Schema(description = "Whether all tags in the original message were preserved losslessly", example = "true")
        boolean lossless,

        @Schema(description = "Count of tags with identical original and reconstructed values", example = "6")
        long equalCount,

        @Schema(description = "Count of supported tags whose value changed across the round trip", example = "0")
        long changedCount,

        @Schema(description = "Count of supported tags present in original but absent in reconstructed message", example = "0")
        long lostCount,

        @Schema(description = "Count of tags present in original that are not supported in pacs.008 mapping", example = "0")
        long unsupportedCount,

        @Schema(description = "Tag-by-tag audit comparison items")
        List<TagComparison> comparisons,

        @Schema(description = "Intermediate ISO 20022 pacs.008.001.14 XML")
        String pacs008Xml,

        @Schema(description = "Reconstructed SWIFT MT103 FIN message text")
        String reconstructedMt103,

        @Schema(description = "Human-readable Markdown audit table")
        String markdownSummary
) {
    public static RoundTripResponse from(RoundTripResult result) {
        return new RoundTripResponse(
                result.isFullyLossless(),
                result.equalCount(),
                result.changedCount(),
                result.lostCount(),
                result.unsupportedCount(),
                result.comparisons(),
                result.pacs008Xml(),
                result.reconstructedRawText(),
                result.markdownSummary()
        );
    }
}

