package com.example.mtiso.api;

import com.example.mtiso.audit.AuditService;
import com.example.mtiso.audit.ConversionDetailResponse;
import com.example.mtiso.mapper.ConversionService;
import com.example.mtiso.parser.MT103ParseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

@RestController
@Tag(name = "Conversion", description = "SWIFT MT103 to ISO 20022 pacs.008 Conversion, Validation, and Audit API")
public class ConversionController {

    private final ConversionService conversionService;
    private final AuditService auditService;

    @Autowired
    public ConversionController(ConversionService conversionService, AuditService auditService) {
        this.conversionService = Objects.requireNonNull(conversionService, "conversionService must not be null");
        this.auditService = auditService;
    }

    public ConversionController(ConversionService conversionService) {
        this(conversionService, null);
    }

    @PostMapping(
            value = {"/convert", "/conversions"},
            consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.ALL_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Convert plain-text MT103 to pacs.008 XML",
            description = "Converts a plain-text SWIFT MT103 message into an ISO 20022 pacs.008.001.14 XML document, executes semantic banking validations and XSD schema checks, generates an audit report, and stores the conversion record."
    )
    @ApiResponse(responseCode = "200", description = "Conversion executed; review status (SUCCESS or VALIDATION_FAILED) and findings")
    @ApiResponse(responseCode = "400", description = "MT103 syntax or line format failure", content = @Content(schema = @Schema(implementation = ParseErrorResponse.class)))
    public ResponseEntity<ConversionResponse> convert(
            @RequestBody(
                    description = "Plain text SWIFT MT103 message (including FIN blocks {1:} through {4:})",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.TEXT_PLAIN_VALUE,
                            schema = @Schema(type = "string"),
                            examples = @ExampleObject(
                                    name = "Synthetic MT103 Customer Transfer",
                                    value = """
                                            {1:F01BANKBEBBAXXX0000000000}{2:I103BANKDEFFXXXXN}{3:{108:SYNTHETIC01}}{4:
                                            :20:SYNTH2026111501
                                            :23B:CRED
                                            :32A:261115EUR10500,00
                                            :50K:/1234567890
                                            SYNTHETIC SERVICES NV
                                            :59:/9876543210
                                            GLOBAL TRADING LTD
                                            :71A:SHA
                                            -}"""
                            )
                    )
            )
            @org.springframework.web.bind.annotation.RequestBody String rawMt103
    ) {
        ConversionResponse response = conversionService.convert(rawMt103);
        return ResponseEntity.ok(response);
    }

    @GetMapping(
            value = "/conversions/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Get stored conversion audit record by ID",
            description = "Retrieves the full historical audit details for a previously executed conversion, including original input text, hash, pacs.008 XML, JSON report, and any approval decisions."
    )
    @ApiResponse(responseCode = "200", description = "Conversion record found")
    @ApiResponse(responseCode = "404", description = "Conversion not found for the given ID")
    public ResponseEntity<ConversionDetailResponse> getConversionById(
            @Parameter(description = "Conversion unique identifier (UUID)", required = true)
            @PathVariable("id") UUID id
    ) {
        if (auditService == null) {
            return ResponseEntity.notFound().build();
        }
        return auditService.getConversionDetail(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(MT103ParseException.class)
    public ResponseEntity<ParseErrorResponse> handleParseException(MT103ParseException ex) {
        ParseErrorResponse errorResponse = new ParseErrorResponse(
                "PARSE_ERROR",
                "Failed to parse MT103 message",
                ex.getTag(),
                ex.getLine(),
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}
