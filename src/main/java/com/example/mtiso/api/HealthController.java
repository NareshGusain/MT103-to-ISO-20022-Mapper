package com.example.mtiso.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "Service health check and readiness API")
public class HealthController {

    @GetMapping
    @Operation(summary = "Check service health", description = "Returns the operational status of the mt-iso-mapper service.")
    @ApiResponse(responseCode = "200", description = "Service is operational")
    public ResponseEntity<HealthResponse> getHealth() {
        return ResponseEntity.ok(new HealthResponse("UP", Instant.now()));
    }
}

