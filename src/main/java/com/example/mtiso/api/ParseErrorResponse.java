package com.example.mtiso.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Structured error returned when MT103 format parsing fails with HTTP 400")
public record ParseErrorResponse(
        @Schema(description = "Error status code", example = "PARSE_ERROR")
        String status,

        @Schema(description = "High-level summary of the parsing error", example = "Failed to parse MT103 message")
        String error,

        @Schema(description = "Offending SWIFT tag identifier", example = ":70:")
        String tag,

        @Schema(description = "Source line number where the violation occurred", example = "10")
        Integer line,

        @Schema(description = "Detailed diagnostic message explaining the syntax/format constraint violation")
        String detail
) {
}

