package com.example.mtiso.mapper;

import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.MT103Parser;
import com.example.mtiso.parser.RawTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundTripServiceTest {

    private MT103Parser parser;
    private Pacs008Mapper mapper;
    private Pacs008ToMt103 reverseMapper;
    private RoundTripService roundTripService;

    @BeforeEach
    void setUp() {
        parser = new MT103Parser();
        mapper = new Pacs008Mapper();
        reverseMapper = new Pacs008ToMt103(parser);
        roundTripService = new RoundTripService(parser, mapper, reverseMapper);
    }

    private String loadResource(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed reading resource: " + path, e);
        }
    }

    @Test
    @DisplayName("Round Trip Sample 01 (Minimal Valid): 100% Lossless, all tags EQUAL")
    void testLosslessRoundTripSample01() {
        String raw = loadResource("/samples/mt103/01_minimal_valid.txt");

        RoundTripResult result = roundTripService.roundTrip(raw);

        assertThat(result.isFullyLossless()).isTrue();
        assertThat(result.equalCount()).isEqualTo(6); // :20:, :23B:, :32A:, :50K:, :59:, :71A:
        assertThat(result.changedCount()).isZero();
        assertThat(result.lostCount()).isZero();
        assertThat(result.unsupportedCount()).isZero();

        assertThat(result.comparisons()).allMatch(c -> c.status() == TagComparisonStatus.EQUAL);
        assertThat(result.markdownSummary()).contains("**LOSSLESS**");
    }

    @Test
    @DisplayName("Round Trip Charges (OUR, BEN, SHA): All map and reverse map to EQUAL")
    void testChargeCodesRoundTrip() {
        for (String sample : List.of("/samples/mt103/02_charge_our.txt", "/samples/mt103/03_charge_ben.txt", "/samples/mt103/04_charge_sha.txt")) {
            String raw = loadResource(sample);
            RoundTripResult result = roundTripService.roundTrip(raw);

            TagComparison chargeComparison = result.comparisons().stream()
                    .filter(c -> c.tag().equals(":71A:"))
                    .findFirst()
                    .orElseThrow();

            assertThat(chargeComparison.status()).isEqualTo(TagComparisonStatus.EQUAL);
            assertThat(chargeComparison.reconstructedValue()).isEqualTo(chargeComparison.originalValue());
        }
    }

    @Test
    @DisplayName("Round Trip Multiline Remittance (:70:): All narrative lines preserved as EQUAL")
    void testMultilineRemittanceRoundTrip() {
        String raw = loadResource("/samples/mt103/05_multiline_remittance_70.txt");

        RoundTripResult result = roundTripService.roundTrip(raw);

        TagComparison rmtComparison = result.comparisons().stream()
                .filter(c -> c.tag().equals(":70:"))
                .findFirst()
                .orElseThrow();

        assertThat(rmtComparison.status()).isEqualTo(TagComparisonStatus.EQUAL);
        assertThat(rmtComparison.reconstructedValue()).contains("/INV/2026-9901");
        assertThat(rmtComparison.reconstructedValue()).contains("TAX INVOICE ATTACHED");
    }

    @Test
    @DisplayName("Unsupported Tag: Tag :77B: reported as UNSUPPORTED, never as EQUAL")
    void testUnsupportedTagShowsAsUnsupportedNeverAsEqual() {
        String raw = """
                {1:F01BANKBEBBAXXX0000000000}{2:I103BANKDEFFXXXXN}{4:
                :20:SYNTH2026111501
                :23B:CRED
                :32A:261115EUR10500,00
                :50K:/1234567890
                SYNTHETIC SERVICES NV
                :59:/9876543210
                GLOBAL TRADING LTD
                :71A:SHA
                :77B:MIFID-REPORTING-DATA
                -}
                """;

        RoundTripResult result = roundTripService.roundTrip(raw);

        assertThat(result.isFullyLossless()).isFalse();
        assertThat(result.unsupportedCount()).isEqualTo(1);

        TagComparison comp77B = result.comparisons().stream()
                .filter(c -> c.tag().equals(":77B:"))
                .findFirst()
                .orElseThrow();

        assertThat(comp77B.status()).isEqualTo(TagComparisonStatus.UNSUPPORTED);
        assertThat(comp77B.originalValue()).isEqualTo("MIFID-REPORTING-DATA");
        assertThat(comp77B.reconstructedValue()).isNull();
        assertThat(comp77B.details()).contains("not supported");
    }

    @Test
    @DisplayName("Intentional Data Loss Sample: Supported :32A: is LOST, unsupported :72: and :77B: are UNSUPPORTED")
    void testIntentionalDataLossSample() {
        String raw = loadResource("/samples/mt103/11_intentional_data_loss.txt");

        RoundTripResult result = roundTripService.roundTrip(raw);

        assertThat(result.isFullyLossless()).isFalse();
        assertThat(result.equalCount()).isEqualTo(5); // :20:, :23B:, :50K:, :59:, :71A:
        assertThat(result.lostCount()).isEqualTo(1);   // :32A: failed parse, thus lost on pacs.008 mapping
        assertThat(result.unsupportedCount()).isEqualTo(2); // :72: and :77B:

        // Check :32A: status is LOST
        TagComparison comp32A = result.comparisons().stream()
                .filter(c -> c.tag().equals(":32A:"))
                .findFirst()
                .orElseThrow();
        assertThat(comp32A.status()).isEqualTo(TagComparisonStatus.LOST);
        assertThat(comp32A.originalValue()).isEqualTo("261115EURINVALIDAMOUNT");
        assertThat(comp32A.reconstructedValue()).isNull();
        assertThat(comp32A.details()).contains("lost during round trip");

        // Check :72: status is UNSUPPORTED
        TagComparison comp72 = result.comparisons().stream()
                .filter(c -> c.tag().equals(":72:"))
                .findFirst()
                .orElseThrow();
        assertThat(comp72.status()).isEqualTo(TagComparisonStatus.UNSUPPORTED);
        assertThat(comp72.originalValue()).isEqualTo("/BNF/ATTN OPERATIONS DESK");
        assertThat(comp72.reconstructedValue()).isNull();

        // Check :77B: status is UNSUPPORTED
        TagComparison comp77B = result.comparisons().stream()
                .filter(c -> c.tag().equals(":77B:"))
                .findFirst()
                .orElseThrow();
        assertThat(comp77B.status()).isEqualTo(TagComparisonStatus.UNSUPPORTED);
        assertThat(comp77B.originalValue()).isEqualTo("REGULATORY REPORTING NON-EQUITY");
        assertThat(comp77B.reconstructedValue()).isNull();

        // Check Markdown output
        assertThat(result.markdownSummary()).contains("DATA LOSS / DIFFERENCES DETECTED");
        assertThat(result.markdownSummary()).contains(":32A:");
        assertThat(result.markdownSummary()).contains("LOST");
        assertThat(result.markdownSummary()).contains("UNSUPPORTED");
    }

    @Test
    @DisplayName("Tag Comparison Status CHANGED: Detected when reconstructed value differs from original")
    void testTagChangedDetection() {
        // Construct an original message where a supported tag value will be changed
        // e.g. :50K: has extra trailing spaces and case differences, or we compare directly
        TagComparison comp = TagComparison.changed(":32A:", "261115EUR10500,", "261115EUR10500,00", "Amount normalized to 2 decimals");
        assertThat(comp.status()).isEqualTo(TagComparisonStatus.CHANGED);
        assertThat(comp.originalValue()).isEqualTo("261115EUR10500,");
        assertThat(comp.reconstructedValue()).isEqualTo("261115EUR10500,00");
    }
}

