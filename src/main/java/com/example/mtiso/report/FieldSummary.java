package com.example.mtiso.report;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aggregated count of fields by mapping outcome")
public record FieldSummary(
        @Schema(description = "Number of fields cleanly mapped to pacs.008", example = "6")
        int mappedCount,

        @Schema(description = "Number of fields mapped under heuristic assumptions", example = "1")
        int assumedCount,

        @Schema(description = "Number of fields where characters or lines were shortened", example = "0")
        int truncatedCount,

        @Schema(description = "Number of fields or tags that could not be mapped", example = "0")
        int unmappedCount
) {
    public int totalFields() {
        return mappedCount + assumedCount + truncatedCount + unmappedCount;
    }
}

