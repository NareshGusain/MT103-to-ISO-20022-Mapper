package com.example.mtiso.api;

import com.example.mtiso.address.AddressSuggestionManager;
import com.example.mtiso.address.AddressSuggestionResponse;
import com.example.mtiso.address.ApprovalDecisionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@Tag(name = "Address Suggestions", description = "AI & Heuristic Address Structuring, Governance, and Re-conversion API")
public class AddressSuggestionController {

    private final AddressSuggestionManager suggestionManager;

    @Autowired
    public AddressSuggestionController(AddressSuggestionManager suggestionManager) {
        this.suggestionManager = Objects.requireNonNull(suggestionManager, "suggestionManager must not be null");
    }

    @PostMapping(
            value = "/conversions/{id}/suggestions",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Generate address suggestions for an MT103 conversion",
            description = "Analyzes free-text address lines in Field :50K: and/or :59: using either the default deterministic rule-based engine or an LLM-backed model (if enabled). Generated suggestions are saved in PENDING status."
    )
    @ApiResponse(responseCode = "200", description = "Address suggestions generated and stored as PENDING")
    @ApiResponse(responseCode = "404", description = "Conversion not found for the given ID")
    public ResponseEntity<List<AddressSuggestionResponse>> generateSuggestions(
            @Parameter(description = "Conversion unique identifier (UUID)", required = true)
            @PathVariable("id") UUID id,
            @Parameter(description = "Provider engine preference: RULE_BASED (default) or LLM", example = "RULE_BASED")
            @RequestParam(name = "provider", defaultValue = "RULE_BASED") String provider
    ) {
        List<AddressSuggestionResponse> suggestions = suggestionManager.generateSuggestions(id, provider);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping(
            value = "/conversions/{id}/suggestions",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Get address suggestions for a conversion",
            description = "Retrieves all previously generated address suggestions, their field values, extraction confidence scores, and current approval lifecycle status (PENDING, APPROVED, REJECTED)."
    )
    @ApiResponse(responseCode = "200", description = "List of address suggestions")
    public ResponseEntity<List<AddressSuggestionResponse>> getSuggestions(
            @Parameter(description = "Conversion unique identifier (UUID)", required = true)
            @PathVariable("id") UUID id
    ) {
        List<AddressSuggestionResponse> suggestions = suggestionManager.getSuggestions(id);
        return ResponseEntity.ok(suggestions);
    }

    @PostMapping(
            value = "/suggestions/{id}/approve",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Approve an address suggestion",
            description = "Marks the specified suggestion as APPROVED, recording the approving actor, timestamp, and audit notes into the central bank audit log. Only approved suggestions are applied upon conversion re-run."
    )
    @ApiResponse(responseCode = "200", description = "Suggestion approved successfully")
    @ApiResponse(responseCode = "404", description = "Suggestion not found")
    public ResponseEntity<AddressSuggestionResponse> approveSuggestion(
            @Parameter(description = "Suggestion unique identifier (UUID)", required = true)
            @PathVariable("id") UUID id,
            @Valid @RequestBody ApprovalDecisionRequest request
    ) {
        AddressSuggestionResponse response = suggestionManager.approveSuggestion(id, request.actor(), request.note());
        return ResponseEntity.ok(response);
    }

    @PostMapping(
            value = "/suggestions/{id}/reject",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Reject an address suggestion",
            description = "Marks the specified suggestion as REJECTED, recording the rejecting actor, timestamp, and audit notes into the central bank audit log. Rejected suggestions are never applied to payment conversions."
    )
    @ApiResponse(responseCode = "200", description = "Suggestion rejected successfully")
    @ApiResponse(responseCode = "404", description = "Suggestion not found")
    public ResponseEntity<AddressSuggestionResponse> rejectSuggestion(
            @Parameter(description = "Suggestion unique identifier (UUID)", required = true)
            @PathVariable("id") UUID id,
            @Valid @RequestBody ApprovalDecisionRequest request
    ) {
        AddressSuggestionResponse response = suggestionManager.rejectSuggestion(id, request.actor(), request.note());
        return ResponseEntity.ok(response);
    }

    @PostMapping(
            value = "/conversions/{id}/rerun",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Re-run conversion applying approved address suggestions",
            description = "Re-converts the original plain-text MT103 message applying all APPROVED address suggestions. Unstructured lines are replaced with structured ISO 20022 postal address elements, upgrading the risk verdict from REVIEW to SAFE."
    )
    @ApiResponse(responseCode = "200", description = "Conversion successfully re-run with structured address elements")
    @ApiResponse(responseCode = "404", description = "Conversion not found")
    public ResponseEntity<ConversionResponse> rerunConversion(
            @Parameter(description = "Conversion unique identifier (UUID)", required = true)
            @PathVariable("id") UUID id
    ) {
        ConversionResponse response = suggestionManager.rerunConversion(id);
        return ResponseEntity.ok(response);
    }
}

