package com.example.mtiso.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MT103ParserTest {

    private MT103Parser parser;

    @BeforeEach
    void setUp() {
        parser = new MT103Parser();
    }

    private String loadSample(String filename) {
        String path = "/samples/mt103/" + filename;
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Sample file not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read sample: " + path, e);
        }
    }

    @Test
    @DisplayName("Sample 01: Parse minimal valid MT103 message")
    void testSample01MinimalValid() {
        String sample = loadSample("01_minimal_valid.txt");
        MT103Message msg = parser.parse(sample);

        assertThat(msg).isNotNull();
        assertThat(msg.headers().getBlock1()).contains("F01BANKBEBBAXXX0000000000");
        assertThat(msg.headers().getBlock2()).contains("I103BANKDEFFXXXXN");
        assertThat(msg.tag20()).isPresent();
        assertThat(msg.tag20().get().value()).isEqualTo("SYNTH2026111501");
        assertThat(msg.tag23B()).isPresent();
        assertThat(msg.tag23B().get().value()).isEqualTo("CRED");
        assertThat(msg.tag32A()).isPresent();
        assertThat(msg.tag32A().get().value()).isEqualTo("261115EUR10500,00");
        assertThat(msg.tag50K()).isPresent();
        assertThat(msg.tag50K().get().lines()).containsExactly("/1234567890", "SYNTHETIC SERVICES NV");
        assertThat(msg.tag59()).isPresent();
        assertThat(msg.tag59().get().lines()).containsExactly("/9876543210", "GLOBAL TRADING LTD");
        assertThat(msg.tag71A()).isPresent();
        assertThat(msg.tag71A().get().value()).isEqualTo("SHA");
        assertThat(msg.tag70()).isEmpty();
        assertThat(msg.unknownTags()).isEmpty();
    }

    @Test
    @DisplayName("Sample 02: Parse MT103 with charge code OUR")
    void testSample02ChargeOur() {
        String sample = loadSample("02_charge_our.txt");
        MT103Message msg = parser.parse(sample);

        assertThat(msg.tag71A()).isPresent();
        assertThat(msg.tag71A().get().value()).isEqualTo("OUR");
        assertThat(msg.tag70()).isPresent();
        assertThat(msg.tag70().get().value()).isEqualTo("/INV/2026-OUR-01");
    }

    @Test
    @DisplayName("Sample 03: Parse MT103 with charge code BEN")
    void testSample03ChargeBen() {
        String sample = loadSample("03_charge_ben.txt");
        MT103Message msg = parser.parse(sample);

        assertThat(msg.tag71A()).isPresent();
        assertThat(msg.tag71A().get().value()).isEqualTo("BEN");
        assertThat(msg.tag32A().get().value()).isEqualTo("261115EUR7500,50");
    }

    @Test
    @DisplayName("Sample 04: Parse MT103 with charge code SHA and USD currency")
    void testSample04ChargeSha() {
        String sample = loadSample("04_charge_sha.txt");
        MT103Message msg = parser.parse(sample);

        assertThat(msg.tag71A()).isPresent();
        assertThat(msg.tag71A().get().value()).isEqualTo("SHA");
        assertThat(msg.tag32A().get().value()).isEqualTo("261115USD125000,00");
    }

    @Test
    @DisplayName("Sample 05: Parse MT103 with multi-line :70: remittance")
    void testSample05MultilineRemittance70() {
        String sample = loadSample("05_multiline_remittance_70.txt");
        MT103Message msg = parser.parse(sample);

        assertThat(msg.tag70()).isPresent();
        RawTag tag70 = msg.tag70().get();
        assertThat(tag70.lines()).hasSize(4);
        assertThat(tag70.lines()).containsExactly(
                "/INV/2026-9901",
                "PAYMENT FOR CONTRACT 88",
                "ANNUAL SOFTWARE SUBSCRIPTION",
                "TAX INVOICE ATTACHED"
        );
    }

    @Test
    @DisplayName("Sample 06: Parse MT103 with :50K: carrying account, name, and address lines")
    void testSample06OrderingCustomer50K() {
        String sample = loadSample("06_50k_name_address.txt");
        MT103Message msg = parser.parse(sample);

        assertThat(msg.tag50K()).isPresent();
        RawTag tag50K = msg.tag50K().get();
        assertThat(tag50K.lines()).containsExactly(
                "/GB29NWBK60161331926819",
                "ACME INDUSTRIAL SOLUTIONS",
                "100 MAIN STREET",
                "LONDON EC2N 2DB, UK"
        );
    }

    @Test
    @DisplayName("Sample 07: Parse MT103 with :59: carrying account, name, and address lines")
    void testSample07BeneficiaryCustomer59() {
        String sample = loadSample("07_59_account_name.txt");
        MT103Message msg = parser.parse(sample);

        assertThat(msg.tag59()).isPresent();
        RawTag tag59 = msg.tag59().get();
        assertThat(tag59.lines()).containsExactly(
                "/DE89370400440532013000",
                "BENEFICIARY IMPORT CORP",
                "INDUSTRIESTRASSE 15",
                "60329 FRANKFURT, GERMANY"
        );
    }

    @Test
    @DisplayName("Sample 08: Parse MT103 missing :32A: without throwing format exception")
    void testSample08Missing32A() {
        String sample = loadSample("08_missing_32a.txt");
        MT103Message msg = parser.parse(sample);

        assertThat(msg).isNotNull();
        assertThat(msg.tag32A()).isEmpty();
        assertThat(msg.tag20()).isPresent();
        assertThat(msg.tag50K()).isPresent();
        assertThat(msg.tag59()).isPresent();
    }

    @Test
    @DisplayName("Sample 09: Parse MT103 with over-length line throws MT103ParseException naming tag and line")
    void testSample09OverlengthLineThrowsException() {
        String sample = loadSample("09_overlength_line.txt");

        assertThatThrownBy(() -> parser.parse(sample))
                .isInstanceOf(MT103ParseException.class)
                .satisfies(ex -> {
                    MT103ParseException parseEx = (MT103ParseException) ex;
                    assertThat(parseEx.getTag()).isEqualTo(":70:");
                    assertThat(parseEx.getLine()).isEqualTo(10);
                    assertThat(parseEx.getMessage()).contains("exceeds SWIFT maximum");
                });
    }

    @Test
    @DisplayName("Sample 10: Parse MT103 with unusual currency (XOF)")
    void testSample10UnusualCurrency() {
        String sample = loadSample("10_unusual_currency.txt");
        MT103Message msg = parser.parse(sample);

        assertThat(msg.tag32A()).isPresent();
        assertThat(msg.tag32A().get().value()).isEqualTo("261115XOF75000000,");
        assertThat(msg.tag59().get().lines()).contains("WEST AFRICA IMPORT SA");
    }

    @Test
    @DisplayName("Parser preserves unknown tags instead of dropping them")
    void shouldPreserveUnknownTags() {
        String messageWithUnknowns = """
                {1:F01BANKBEBBAXXX0000000000}{2:I103BANKDEFFXXXXN}{4:
                :20:SYNTH2026111599
                :23B:CRED
                :32A:261115EUR1000,00
                :50K:/1234567890
                SENDER CORP
                :52A:BANKBEBB
                :59:/9876543210
                RECEIVER CORP
                :71A:SHA
                :72:/BNF/FURTHER DETAILS
                :77B:/BENEFRES/SYNTHETIC
                -}
                """;

        MT103Message msg = parser.parse(messageWithUnknowns);

        assertThat(msg.unknownTags()).hasSize(3);
        List<String> unknownTagCodes = msg.unknownTags().stream().map(RawTag::tagCode).toList();
        assertThat(unknownTagCodes).containsExactly("52A", "72", "77B");
        assertThat(msg.getTagValue("72")).contains("/BNF/FURTHER DETAILS");
        assertThat(msg.getTagValue("77B")).contains("/BENEFRES/SYNTHETIC");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("Parser throws MT103ParseException for empty or whitespace-only messages")
    void shouldThrowOnEmptyMessage(String blankInput) {
        assertThatThrownBy(() -> parser.parse(blankInput))
                .isInstanceOf(MT103ParseException.class)
                .hasMessageContaining("Message content cannot be null or empty");
    }

    @Test
    @DisplayName("Parser throws MT103ParseException when Block 4 is missing")
    void shouldThrowWhenBlock4Missing() {
        String invalid = "{1:F01BANKBEBBAXXX0000000000}{2:I103BANKDEFFXXXXN}";
        assertThatThrownBy(() -> parser.parse(invalid))
                .isInstanceOf(MT103ParseException.class)
                .hasMessageContaining("Missing SWIFT Block 4");
    }

    @Test
    @DisplayName("Parser throws MT103ParseException when Block 4 is not closed")
    void shouldThrowWhenBlock4Unclosed() {
        String unclosed = """
                {1:F01BANKBEBBAXXX0000000000}{2:I103BANKDEFFXXXXN}{4:
                :20:SYNTH2026111501
                :23B:CRED
                """;
        assertThatThrownBy(() -> parser.parse(unclosed))
                .isInstanceOf(MT103ParseException.class)
                .hasMessageContaining("Block 4 is not closed");
    }

    @Test
    @DisplayName("Parser throws MT103ParseException on malformed tag syntax")
    void shouldThrowOnMalformedTag() {
        String malformed = """
                {4:
                :INVALID_TAG:12345
                :23B:CRED
                -}
                """;
        assertThatThrownBy(() -> parser.parse(malformed))
                .isInstanceOf(MT103ParseException.class)
                .satisfies(ex -> {
                    MT103ParseException parseEx = (MT103ParseException) ex;
                    assertThat(parseEx.getTag()).isEqualTo(":INVALID_TAG:");
                    assertThat(parseEx.getLine()).isEqualTo(2);
                });
    }

    @Test
    @DisplayName("Parser throws MT103ParseException on unexpected continuation line without an active tag")
    void shouldThrowOnUnexpectedContinuationLine() {
        String badContinuation = """
                {4:
                ORPHAN CONTINUATION LINE
                :20:REF123
                -}
                """;
        assertThatThrownBy(() -> parser.parse(badContinuation))
                .isInstanceOf(MT103ParseException.class)
                .hasMessageContaining("Unexpected continuation line without an active tag");
    }
}

