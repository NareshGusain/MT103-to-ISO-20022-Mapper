package com.example.mtiso.address;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body to approve or reject an address suggestion")
public record ApprovalDecisionRequest(
        @NotBlank(message = "actor is required")
        @Schema(description = "User or system actor recording the operational decision", example = "alice.smith@compliance.bank")
        String actor,

        @Schema(description = "Optional justification or KYC review notes", example = "Verified against national company registry")
        String note
) {
}

