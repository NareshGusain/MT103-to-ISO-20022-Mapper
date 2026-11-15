package com.example.mtiso.mapper;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outcome status for tag-by-tag comparison in a round-trip conversion.
 */
@Schema(description = "Comparison outcome for an individual SWIFT tag across round-trip conversion")
public enum TagComparisonStatus {

    @Schema(description = "Supported tag whose value is perfectly preserved")
    EQUAL,

    @Schema(description = "Supported tag whose value was altered during conversion")
    CHANGED,

    @Schema(description = "Supported tag that was present in the original message but missing in the reconstructed message")
    LOST,

    @Schema(description = "Tag is not supported by the pacs.008 mapping; data is not carried over")
    UNSUPPORTED
}

