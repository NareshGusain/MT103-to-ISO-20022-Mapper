package com.example.mtiso.address;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Details of an AI or rule-based address suggestion and its governance approval status")
public record AddressSuggestionResponse(
        @Schema(description = "Unique identifier of the suggestion")
        UUID id,

        @Schema(description = "Associated conversion record ID")
        UUID conversionId,

        @Schema(description = "Party role: DEBTOR or CREDITOR")
        PartyType partyType,

        @Schema(description = "Original unstructured free-text address from MT103")
        String originalAddress,

        @Schema(description = "Suggested street name and number")
        String street,

        @Schema(description = "Confidence for street extraction (0.0 to 1.0)")
        double streetConfidence,

        @Schema(description = "Suggested town / city")
        String town,

        @Schema(description = "Confidence for town extraction (0.0 to 1.0)")
        double townConfidence,

        @Schema(description = "Suggested postal code")
        String postCode,

        @Schema(description = "Confidence for postal code extraction (0.0 to 1.0)")
        double postCodeConfidence,

        @Schema(description = "Suggested 2-letter ISO country code")
        String country,

        @Schema(description = "Confidence for country extraction (0.0 to 1.0)")
        double countryConfidence,

        @Schema(description = "Lifecycle approval status: PENDING, APPROVED, REJECTED")
        SuggestionStatus status,

        @Schema(description = "Provider engine that created the suggestion: RULE_BASED or LLM")
        String provider,

        @Schema(description = "Timestamp when the suggestion was generated")
        OffsetDateTime createdAt,

        @Schema(description = "Timestamp when the approval decision was recorded")
        OffsetDateTime decidedAt,

        @Schema(description = "Operator or reviewer who approved or rejected the suggestion")
        String decidedBy,

        @Schema(description = "Audit notes associated with the approval decision")
        String decisionNote
) {
    public static AddressSuggestionResponse from(AddressSuggestionEntity entity) {
        return new AddressSuggestionResponse(
                entity.getId(),
                entity.getConversionId(),
                entity.getPartyType(),
                entity.getOriginalAddress(),
                entity.getStreet(),
                entity.getStreetConfidence(),
                entity.getTown(),
                entity.getTownConfidence(),
                entity.getPostCode(),
                entity.getPostCodeConfidence(),
                entity.getCountry(),
                entity.getCountryConfidence(),
                entity.getStatus(),
                entity.getProvider(),
                entity.getCreatedAt(),
                entity.getDecidedAt(),
                entity.getDecidedBy(),
                entity.getDecisionNote()
        );
    }
}

