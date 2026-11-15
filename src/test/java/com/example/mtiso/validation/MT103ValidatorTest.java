package com.example.mtiso.validation;

import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.MT103Parser;
import com.example.mtiso.parser.RawTag;
import com.example.mtiso.parser.SwiftHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MT103ValidatorTest {

    private MT103Parser parser;
    private MT103Validator validator;

    @BeforeEach
    void setUp() {
        parser = new MT103Parser();
        validator = new MT103Validator();
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

    private MT103Message createMessageWithTag32A(String tag32AValue) {
        return new MT103Message(
                SwiftHeaders.empty(),
                RawTag.of("20", "REF12345", 1),
                RawTag.of("23B", "CRED", 2),
                RawTag.of("32A", tag32AValue, 3),
                RawTag.of("50K", "/12345\nSENDER", 4),
                RawTag.of("59", "/67890\nRECEIVER", 6),
                null,
                RawTag.of("71A", "SHA", 8),
                List.of(),
                List.of(
                        RawTag.of("20", "REF12345", 1),
                        RawTag.of("23B", "CRED", 2),
                        RawTag.of("32A", tag32AValue, 3),
                        RawTag.of("50K", "/12345\nSENDER", 4),
                        RawTag.of("59", "/67890\nRECEIVER", 6),
                        RawTag.of("71A", "SHA", 8)
                )
        );
    }

    private MT103Message createMessageWithTag71A(String tag71AValue) {
        return new MT103Message(
                SwiftHeaders.empty(),
                RawTag.of("20", "REF12345", 1),
                RawTag.of("23B", "CRED", 2),
                RawTag.of("32A", "261115EUR1000,00", 3),
                RawTag.of("50K", "/12345\nSENDER", 4),
                RawTag.of("59", "/67890\nRECEIVER", 6),
                null,
                RawTag.of("71A", tag71AValue, 8),
                List.of(),
                List.of(
                        RawTag.of("20", "REF12345", 1),
                        RawTag.of("23B", "CRED", 2),
                        RawTag.of("32A", "261115EUR1000,00", 3),
                        RawTag.of("50K", "/12345\nSENDER", 4),
                        RawTag.of("59", "/67890\nRECEIVER", 6),
                        RawTag.of("71A", tag71AValue, 8)
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "01_minimal_valid.txt",
            "02_charge_our.txt",
            "03_charge_ben.txt",
            "04_charge_sha.txt",
            "05_multiline_remittance_70.txt",
            "06_50k_name_address.txt",
            "07_59_account_name.txt",
            "10_unusual_currency.txt"
    })
    @DisplayName("Valid samples should produce no error findings")
    void validSamplesShouldHaveNoErrors(String sampleFilename) {
        String raw = loadSample(sampleFilename);
        MT103Message msg = parser.parse(raw);

        ValidationResult result = validator.validate(msg);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Sample 08 missing :32A: should return TAG_MISSING error for :32A:")
    void sample08Missing32AShouldReportError() {
        String raw = loadSample("08_missing_32a.txt");
        MT103Message msg = parser.parse(raw);

        ValidationResult result = validator.validate(msg);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        ValidationFinding finding = result.getErrors().getFirst();
        assertThat(finding.code()).isEqualTo(MT103Validator.CODE_TAG_MISSING);
        assertThat(finding.tag()).isEqualTo(":32A:");
        assertThat(finding.severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    @DisplayName("Missing mandatory tags should report an ERROR finding per missing tag")
    void missingMandatoryTagsShouldReportErrors() {
        MT103Message emptyMsg = new MT103Message(
                SwiftHeaders.empty(), null, null, null, null, null, null, null, List.of(), List.of()
        );

        ValidationResult result = validator.validate(emptyMsg);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(6);

        List<String> missingTags = result.getErrors().stream().map(ValidationFinding::tag).toList();
        assertThat(missingTags).containsExactlyInAnyOrder(
                ":20:", ":23B:", ":32A:", ":50K:", ":59:", ":71A:"
        );
        assertThat(result.getErrors()).allMatch(f -> f.code().equals(MT103Validator.CODE_TAG_MISSING));
    }

    @Test
    @DisplayName("Tag :32A: with period '.' instead of comma ',' should report INVALID_AMOUNT_SEPARATOR")
    void tag32AWithPeriodSeparatorShouldReportError() {
        MT103Message msg = createMessageWithTag32A("261115EUR10500.50");

        ValidationResult result = validator.validate(msg);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(MT103Validator.CODE_INVALID_AMOUNT_SEPARATOR);
            assertThat(f.tag()).isEqualTo(":32A:");
            assertThat(f.message()).contains("uses '.' decimal separator");
        });
    }

    @Test
    @DisplayName("Tag :32A: with invalid calendar date should report INVALID_DATE_FORMAT")
    void tag32AWithInvalidDateShouldReportError() {
        // Feb 30th is invalid in strict calendar resolver
        MT103Message msg = createMessageWithTag32A("260230EUR10500,50");

        ValidationResult result = validator.validate(msg);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(MT103Validator.CODE_INVALID_DATE_FORMAT);
            assertThat(f.tag()).isEqualTo(":32A:");
            assertThat(f.message()).contains("not a valid calendar date");
        });
    }

    @Test
    @DisplayName("Tag :32A: with invalid non-numeric date should report INVALID_DATE_FORMAT")
    void tag32AWithNonNumericDateShouldReportError() {
        MT103Message msg = createMessageWithTag32A("26AB15EUR10500,50");

        ValidationResult result = validator.validate(msg);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(MT103Validator.CODE_INVALID_DATE_FORMAT);
            assertThat(f.tag()).isEqualTo(":32A:");
        });
    }

    @Test
    @DisplayName("Tag :32A: with unrecognized ISO 4217 currency should report INVALID_CURRENCY_CODE")
    void tag32AWithInvalidCurrencyShouldReportError() {
        MT103Message msg = createMessageWithTag32A("261115ZZZ10500,50");

        ValidationResult result = validator.validate(msg);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(MT103Validator.CODE_INVALID_CURRENCY_CODE);
            assertThat(f.tag()).isEqualTo(":32A:");
            assertThat(f.message()).contains("not a valid ISO 4217 currency");
        });
    }

    @Test
    @DisplayName("Tag :32A: with zero or negative amount should report INVALID_AMOUNT_FORMAT")
    void tag32AWithNonPositiveAmountShouldReportError() {
        MT103Message msgZero = createMessageWithTag32A("261115EUR0,00");
        ValidationResult resultZero = validator.validate(msgZero);

        assertThat(resultZero.isValid()).isFalse();
        assertThat(resultZero.getErrors()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(MT103Validator.CODE_INVALID_AMOUNT_FORMAT);
            assertThat(f.message()).contains("must be strictly positive");
        });
    }

    @Test
    @DisplayName("Tag :32A: with amount exceeding 15 digits should report INVALID_AMOUNT_FORMAT")
    void tag32AWithTooManyDigitsShouldReportError() {
        MT103Message msgTooLong = createMessageWithTag32A("261115EUR1234567890123456,00");
        ValidationResult result = validator.validate(msgTooLong);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(MT103Validator.CODE_INVALID_AMOUNT_FORMAT);
            assertThat(f.message()).contains("exceeding the SWIFT maximum of 15 digits");
        });
    }

    @Test
    @DisplayName("Tag :71A: with invalid charge code should report INVALID_CHARGE_CODE")
    void tag71AWithInvalidCodeShouldReportError() {
        MT103Message msg = createMessageWithTag71A("XYZ");

        ValidationResult result = validator.validate(msg);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(MT103Validator.CODE_INVALID_CHARGE_CODE);
            assertThat(f.tag()).isEqualTo(":71A:");
            assertThat(f.message()).contains("must be one of OUR, BEN, or SHA");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"OUR", "BEN", "SHA"})
    @DisplayName("Tag :71A: with valid charge codes should pass validation")
    void tag71AWithValidCodesShouldPass(String validCode) {
        MT103Message msg = createMessageWithTag71A(validCode);
        ValidationResult result = validator.validate(msg);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Tag line count exceeding limit should report LINE_COUNT_EXCEEDED")
    void tagExceedingLineCountShouldReportError() {
        // Tag 70 has limit of 4 lines; here we provide 5 lines
        RawTag multiline70 = RawTag.of("70", "LINE 1\nLINE 2\nLINE 3\nLINE 4\nLINE 5", 10);
        MT103Message msg = new MT103Message(
                SwiftHeaders.empty(),
                RawTag.of("20", "REF12345", 1),
                RawTag.of("23B", "CRED", 2),
                RawTag.of("32A", "261115EUR1000,00", 3),
                RawTag.of("50K", "/12345\nSENDER", 4),
                RawTag.of("59", "/67890\nRECEIVER", 6),
                multiline70,
                RawTag.of("71A", "SHA", 8),
                List.of(),
                List.of(
                        RawTag.of("20", "REF12345", 1),
                        RawTag.of("23B", "CRED", 2),
                        RawTag.of("32A", "261115EUR1000,00", 3),
                        RawTag.of("50K", "/12345\nSENDER", 4),
                        RawTag.of("59", "/67890\nRECEIVER", 6),
                        multiline70,
                        RawTag.of("71A", "SHA", 8)
                )
        );

        ValidationResult result = validator.validate(msg);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(MT103Validator.CODE_LINE_COUNT_EXCEEDED);
            assertThat(f.tag()).isEqualTo(":70:");
            assertThat(f.message()).contains("line count (5) exceeds maximum allowed limit of 4");
        });
    }

    @Test
    @DisplayName("Tag line length exceeding limit should report LINE_LENGTH_EXCEEDED")
    void tagExceedingLineLengthShouldReportError() {
        // Tag 20 has limit of 16 characters; here we provide 20 characters
        RawTag overlength20 = RawTag.of("20", "REFERENCE-EXCEEDING-16", 1);
        MT103Message msg = new MT103Message(
                SwiftHeaders.empty(),
                overlength20,
                RawTag.of("23B", "CRED", 2),
                RawTag.of("32A", "261115EUR1000,00", 3),
                RawTag.of("50K", "/12345\nSENDER", 4),
                RawTag.of("59", "/67890\nRECEIVER", 6),
                null,
                RawTag.of("71A", "SHA", 8),
                List.of(),
                List.of(
                        overlength20,
                        RawTag.of("23B", "CRED", 2),
                        RawTag.of("32A", "261115EUR1000,00", 3),
                        RawTag.of("50K", "/12345\nSENDER", 4),
                        RawTag.of("59", "/67890\nRECEIVER", 6),
                        RawTag.of("71A", "SHA", 8)
                )
        );

        ValidationResult result = validator.validate(msg);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(MT103Validator.CODE_LINE_LENGTH_EXCEEDED);
            assertThat(f.tag()).isEqualTo(":20:");
            assertThat(f.message()).contains("exceeds maximum allowed length of 16 characters");
        });
    }

    @Test
    @DisplayName("Configurable line limits should be respected when passed via constructor")
    void customLineLimitsShouldBeRespected() {
        // Configure Tag 20 with strict max length of 10 chars
        Map<String, TagLineLimit> customLimits = Map.of(
                "20", TagLineLimit.of(1, 10),
                "23B", TagLineLimit.of(1, 4),
                "32A", TagLineLimit.of(1, 24),
                "50K", TagLineLimit.of(5, 35),
                "59", TagLineLimit.of(5, 35),
                "71A", TagLineLimit.of(1, 3)
        );
        MT103Validator customValidator = new MT103Validator(customLimits);

        // Reference is 12 chars: valid under default (16), but fails under custom (10)
        RawTag tag20 = RawTag.of("20", "REF123456789", 1);
        MT103Message msg = new MT103Message(
                SwiftHeaders.empty(),
                tag20,
                RawTag.of("23B", "CRED", 2),
                RawTag.of("32A", "261115EUR1000,00", 3),
                RawTag.of("50K", "/12345\nSENDER", 4),
                RawTag.of("59", "/67890\nRECEIVER", 6),
                null,
                RawTag.of("71A", "SHA", 8),
                List.of(),
                List.of(
                        tag20,
                        RawTag.of("23B", "CRED", 2),
                        RawTag.of("32A", "261115EUR1000,00", 3),
                        RawTag.of("50K", "/12345\nSENDER", 4),
                        RawTag.of("59", "/67890\nRECEIVER", 6),
                        RawTag.of("71A", "SHA", 8)
                )
        );

        ValidationResult defaultResult = validator.validate(msg);
        assertThat(defaultResult.isValid()).isTrue();

        ValidationResult customResult = customValidator.validate(msg);
        assertThat(customResult.isValid()).isFalse();
        assertThat(customResult.getErrors()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(MT103Validator.CODE_LINE_LENGTH_EXCEEDED);
            assertThat(f.tag()).isEqualTo(":20:");
            assertThat(f.message()).contains("exceeds maximum allowed length of 10 characters");
        });
    }

    @Test
    @DisplayName("Non-standard bank operation code in :23B: should report a WARNING without invalidating message")
    void nonStandardBankOpCodeShouldReportWarning() {
        RawTag tag23B = RawTag.of("23B", "SPAY", 2);
        MT103Message msg = new MT103Message(
                SwiftHeaders.empty(),
                RawTag.of("20", "REF12345", 1),
                tag23B,
                RawTag.of("32A", "261115EUR1000,00", 3),
                RawTag.of("50K", "/12345\nSENDER", 4),
                RawTag.of("59", "/67890\nRECEIVER", 6),
                null,
                RawTag.of("71A", "SHA", 8),
                List.of(),
                List.of(
                        RawTag.of("20", "REF12345", 1),
                        tag23B,
                        RawTag.of("32A", "261115EUR1000,00", 3),
                        RawTag.of("50K", "/12345\nSENDER", 4),
                        RawTag.of("59", "/67890\nRECEIVER", 6),
                        RawTag.of("71A", "SHA", 8)
                )
        );

        ValidationResult result = validator.validate(msg);

        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).hasSize(1);
        ValidationFinding warning = result.getWarnings().getFirst();
        assertThat(warning.code()).isEqualTo(MT103Validator.CODE_NON_STANDARD_OPERATION_CODE);
        assertThat(warning.tag()).isEqualTo(":23B:");
        assertThat(warning.severity()).isEqualTo(Severity.WARNING);
    }
}

