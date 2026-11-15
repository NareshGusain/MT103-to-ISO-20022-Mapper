package com.example.mtiso.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(description = "Details of an individual field or tag where information was lost or shortened")
public record DataLossItem(
        @Schema(description = "SWIFT field or element identifier", example = ":72:")
        String field,

        @Schema(description = "Original raw value in MT103", example = "/BNF/FURTHER DETAILS")
        String originalValue,

        @Schema(description = "Mapped value in pacs.008 or null if completely lost", example = "null")
        String mappedValue,

        @Schema(description = "Reason or business impact for the data loss", example = "Tag :72: sender-to-receiver information is unmapped in basic pacs.008")
        String reason
) {
    public DataLossItem {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}

