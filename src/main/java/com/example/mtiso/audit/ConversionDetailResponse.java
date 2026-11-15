package com.example.mtiso.audit;

import com.example.mtiso.report.ConversionReport;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Full audit history and details for a stored conversion")
public record ConversionDetailResponse(
        @Schema(description = "Conversion unique identifier", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Timestamp when message was ingested and converted")
        OffsetDateTime receivedAt,

        @Schema(description = "SHA-256 hash of original raw input text for tamper detection and idempotency")
        String inputHash,

        @Schema(description = "Original raw MT103 input text")
        String inputText,

        @Schema(description = "Conversion outcome status", example = "SUCCESS")
        String status,

        @Schema(description = "Generated ISO 20022 pacs.008 XML (if successful)")
        String pacsXml,

        @Schema(description = "Structured conversion report containing verdict, metrics, and markdown")
        ConversionReport report,

        @Schema(description = "Raw JSON representation of conversion report")
        String reportJson,

        @Schema(description = "Service version that executed the conversion", example = "1.0.0")
        String toolVersion,

        @Schema(description = "List of operational sign-off approvals associated with this conversion")
        List<ApprovalDto> approvals
) {
}

