package com.example.mtiso.api;

import com.example.mtiso.mapper.RoundTripResult;
import com.example.mtiso.mapper.RoundTripService;
import com.example.mtiso.parser.MT103ParseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/roundtrip")
@Tag(name = "Round-Trip Audit", description = "Bidirectional MT103 -> pacs.008 -> MT103 Conversion and Fidelity Audit")
public class RoundTripController {

    private final RoundTripService roundTripService;

    public RoundTripController(RoundTripService roundTripService) {
        this.roundTripService = Objects.requireNonNull(roundTripService, "roundTripService must not be null");
    }

    @PostMapping(
            consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.ALL_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Perform bidirectional MT103 -> pacs.008 -> MT103 round-trip conversion",
            description = "Converts an MT103 message to ISO 20022 pacs.008 and back, then performs a tag-by-tag audit diff comparing each field as EQUAL, CHANGED, LOST, or UNSUPPORTED."
    )
    @ApiResponse(responseCode = "200", description = "Round-trip conversion executed; returns tag-by-tag audit diff")
    @ApiResponse(responseCode = "400", description = "MT103 syntax or line format failure", content = @Content(schema = @Schema(implementation = ParseErrorResponse.class)))
    public ResponseEntity<RoundTripResponse> roundTrip(
            @RequestBody(
                    description = "Plain text SWIFT MT103 message",
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
        RoundTripResult result = roundTripService.roundTrip(rawMt103);
        return ResponseEntity.ok(RoundTripResponse.from(result));
    }

    @ExceptionHandler(MT103ParseException.class)
    public ResponseEntity<ParseErrorResponse> handleParseException(MT103ParseException ex) {
        ParseErrorResponse errorResponse = new ParseErrorResponse(
                "PARSE_ERROR",
                "Failed to parse MT103 message during round-trip",
                ex.getTag(),
                ex.getLine(),
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}

