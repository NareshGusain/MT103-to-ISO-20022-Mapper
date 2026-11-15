package com.example.mtiso.audit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Operational approval / review decision record")
public record ApprovalDto(
        @Schema(description = "Approval record unique ID")
        UUID id,

        @Schema(description = "Target conversion unique ID")
        UUID conversionId,

        @Schema(description = "Decision action (e.g. APPROVED, REJECTED)", example = "APPROVED")
        String action,

        @Schema(description = "Actor / compliance officer who took the action", example = "compliance_officer_1")
        String actor,

        @Schema(description = "Timestamp when decision was made")
        OffsetDateTime decidedAt,

        @Schema(description = "Audit note or rationale")
        String note
) {
    public static ApprovalDto from(ApprovalEntity entity) {
        return new ApprovalDto(
                entity.getId(),
                entity.getConversion().getId(),
                entity.getAction(),
                entity.getActor(),
                entity.getDecidedAt(),
                entity.getNote()
        );
    }
}

