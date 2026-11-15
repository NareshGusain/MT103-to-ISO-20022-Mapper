package com.example.mtiso.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Service health check response payload")
public record HealthResponse(
        @Schema(description = "Service operational status", example = "UP")
        String status,

        @Schema(description = "Current UTC timestamp", example = "2026-11-15T04:45:00Z")
        Instant timestamp
) {
}

