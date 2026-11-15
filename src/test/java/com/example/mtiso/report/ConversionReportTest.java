package com.example.mtiso.report;

import com.example.mtiso.mapper.MappingNote;
import com.example.mtiso.mapper.MappingResult;
import com.example.mtiso.mapper.Pacs008Mapper;
import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.MT103Parser;
import com.example.mtiso.validation.MT103Validator;
import com.example.mtiso.validation.Pacs008XmlValidator;
import com.example.mtiso.validation.ValidationFinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversionReportTest {

    private MT103Parser parser;
    private MT103Validator validator;
    private Pacs008Mapper mapper;
    private Pacs008XmlValidator xmlValidator;
    private ConversionReportGenerator reportGenerator;

    @BeforeEach
    void setUp() {
        parser = new MT103Parser();
        validator = new MT103Validator();
        mapper = new Pacs008Mapper();
        xmlValidator = new Pacs008XmlValidator();
        reportGenerator = new ConversionReportGenerator();
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
    @DisplayName("Verdict SAFE: Clean message with no unstructured address, zero assumptions, and no schema errors")
    void testSafeVerdict() {
        String raw = loadResource("/samples/mt103/01_minimal_valid.txt");
        MT103Message message = parser.parse(raw);
        List<ValidationFinding> mtFindings = validator.validateFindings(message);
        MappingResult mappingResult = mapper.map(message);
        List<ValidationFinding> xmlFindings = xmlValidator.validateXml(mappingResult.xml());

        List<ValidationFinding> allFindings = new ArrayList<>(mtFindings);
        allFindings.addAll(xmlFindings);

        ConversionReport report = reportGenerator.generateReport(message, mappingResult, allFindings);

        assertThat(report.verdict()).isEqualTo(ConversionVerdict.SAFE);
        assertThat(report.verdictRule()).startsWith("SAFE:");
        assertThat(report.summary().mappedCount()).isGreaterThan(0);
        assertThat(report.summary().truncatedCount()).isZero();
        assertThat(report.summary().unmappedCount()).isZero();
        assertThat(report.dataLoss()).isEmpty();
        assertThat(report.addressWarnings()).isEmpty();
        assertThat(report.markdown()).contains("**Verdict:** `SAFE`");
    }

    @Test
    @DisplayName("Verdict REVIEW: Free-text unstructured address lines trigger REVIEW verdict and address warnings")
    void testReviewVerdictDueToUnstructuredAddress() {
        String raw = loadResource("/samples/mt103/06_50k_name_address.txt");
        MT103Message message = parser.parse(raw);
        List<ValidationFinding> mtFindings = validator.validateFindings(message);
        MappingResult mappingResult = mapper.map(message);
        List<ValidationFinding> xmlFindings = xmlValidator.validateXml(mappingResult.xml());

        List<ValidationFinding> allFindings = new ArrayList<>(mtFindings);
        allFindings.addAll(xmlFindings);

        ConversionReport report = reportGenerator.generateReport(message, mappingResult, allFindings);

        assertThat(report.verdict()).isEqualTo(ConversionVerdict.REVIEW);
        assertThat(report.verdictRule()).startsWith("REVIEW:");
        assertThat(report.addressWarnings()).isNotEmpty();
        assertThat(report.addressWarnings()).anyMatch(w -> w.contains("Debtor (:50K:)"));
        assertThat(report.markdown()).contains("**Verdict:** `REVIEW`");
        assertThat(report.markdown()).contains("Debtor (:50K:)");
    }

    @Test
    @DisplayName("Verdict REVIEW: Unsupported unknown tags cause data loss items and trigger REVIEW verdict")
    void testReviewVerdictDueToUnknownTagDataLoss() {
        String rawWithUnknown = """
                {1:F01BANKBEBBAXXX0000000000}{2:I103BANKDEFFXXXXN}{4:
                :20:SYNTH2026111599
                :23B:CRED
                :32A:261115EUR1000,00
                :50K:/1234567890
                SYNTHETIC CORP
                :59:/9876543210
                RECEIVER CORP
                :71A:SHA
                :72:/BNF/FURTHER INSTRUCTIONS
                -}
                """;
        MT103Message message = parser.parse(rawWithUnknown);
        List<ValidationFinding> mtFindings = validator.validateFindings(message);
        MappingResult mappingResult = mapper.map(message);
        List<ValidationFinding> xmlFindings = xmlValidator.validateXml(mappingResult.xml());

        List<ValidationFinding> allFindings = new ArrayList<>(mtFindings);
        allFindings.addAll(xmlFindings);

        ConversionReport report = reportGenerator.generateReport(message, mappingResult, allFindings);

        assertThat(report.verdict()).isEqualTo(ConversionVerdict.REVIEW);
        assertThat(report.dataLoss()).hasSize(1);
        DataLossItem lossItem = report.dataLoss().getFirst();
        assertThat(lossItem.field()).isEqualTo(":72:");
        assertThat(lossItem.originalValue()).isEqualTo("/BNF/FURTHER INSTRUCTIONS");
        assertThat(lossItem.mappedValue()).isEqualTo("null");
        assertThat(report.summary().unmappedCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Verdict BLOCK: Fatal validation error (missing :32A:) triggers BLOCK verdict")
    void testBlockVerdictDueToMissingMandatoryTag() {
        String rawMissing32A = loadResource("/samples/mt103/08_missing_32a.txt");
        MT103Message message = parser.parse(rawMissing32A);
        List<ValidationFinding> mtFindings = validator.validateFindings(message);
        MappingResult mappingResult = mapper.map(message);
        List<ValidationFinding> xmlFindings = xmlValidator.validateXml(mappingResult.xml());

        List<ValidationFinding> allFindings = new ArrayList<>(mtFindings);
        allFindings.addAll(xmlFindings);

        ConversionReport report = reportGenerator.generateReport(message, mappingResult, allFindings);

        assertThat(report.verdict()).isEqualTo(ConversionVerdict.BLOCK);
        assertThat(report.verdictRule()).startsWith("BLOCK:");
        assertThat(report.markdown()).contains("**Verdict:** `BLOCK`");
        assertThat(report.markdown()).contains("TAG_MISSING");
    }

    @Test
    @DisplayName("Markdown report contains all required headers, tables, and sections")
    void testMarkdownReportStructure() {
        String raw = loadResource("/samples/mt103/02_charge_our.txt");
        MT103Message message = parser.parse(raw);
        List<ValidationFinding> mtFindings = validator.validateFindings(message);
        MappingResult mappingResult = mapper.map(message);
        List<ValidationFinding> xmlFindings = xmlValidator.validateXml(mappingResult.xml());

        List<ValidationFinding> allFindings = new ArrayList<>(mtFindings);
        allFindings.addAll(xmlFindings);

        ConversionReport report = reportGenerator.generateReport(message, mappingResult, allFindings);

        String md = report.markdown();
        assertThat(md).contains("# MT103 to ISO 20022 Conversion Risk Report");
        assertThat(md).contains("**Verdict:**");
        assertThat(md).contains("**Decision Rule:**");
        assertThat(md).contains("## Field Mapping Summary");
        assertThat(md).contains("## Address Quality Warnings");
        assertThat(md).contains("## Data Loss & Truncations");
    }
}

