package com.example.mtiso.api;

import com.example.mtiso.mapper.MappingNote;
import com.example.mtiso.report.ConversionReport;
import com.example.mtiso.validation.ValidationFinding;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Schema(description = "Response returned upon converting an MT103 into ISO 20022 pacs.008 XML")
public record ConversionResponse(
        @Schema(description = "Conversion audit record unique ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Overall conversion outcome status", example = "SUCCESS")
        String status,

        @Schema(description = "Generated ISO 20022 pacs.008 XML string")
        String pacs008Xml,

        @Schema(description = "List of validation findings from semantic rules and XSD schema validation")
        List<ValidationFinding> findings,

        @Schema(description = "Audit trail of field mappings, truncations, and assumptions")
        List<MappingNote> mappingNotes,

        @Schema(description = "Risk and data-loss report detailing mapping verdict, summary counts, and markdown report")
        ConversionReport report
) {
    public ConversionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(pacs008Xml, "pacs008Xml must not be null");
        Objects.requireNonNull(report, "report must not be null");
        findings = findings != null ? List.copyOf(findings) : Collections.emptyList();
        mappingNotes = mappingNotes != null ? List.copyOf(mappingNotes) : Collections.emptyList();
    }
}
