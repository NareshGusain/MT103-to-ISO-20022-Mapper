package com.example.mtiso.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Pacs008XmlValidatorTest {

    private Pacs008XmlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new Pacs008XmlValidator();
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
    @DisplayName("Invalid XML snippet should report schema validation findings")
    void invalidXmlShouldReportSchemaFindings() {
        String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.14">
                    <FIToFICstmrCdtTrf>
                        <GrpHdr>
                            <MsgId>INVALID</MsgId>
                        </GrpHdr>
                    </FIToFICstmrCdtTrf>
                </Document>
                """;

        List<ValidationFinding> findings = validator.validateXml(invalidXml);

        assertThat(findings).isNotEmpty();
        assertThat(findings).anyMatch(f -> f.code().equals(Pacs008XmlValidator.CODE_SCHEMA_ERROR));
    }

    @Test
    @DisplayName("Validate golden 01 XML against pacs.008 XSD schema")
    void testValidateGolden01() {
        String xml = loadResource("/golden/golden_01_minimal.xml");
        List<ValidationFinding> findings = validator.validateXml(xml);
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("Validate golden 02 XML against pacs.008 XSD schema")
    void testValidateGolden02() {
        String xml = loadResource("/golden/golden_02_charge_our.xml");
        List<ValidationFinding> findings = validator.validateXml(xml);
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("Empty or null XML should return a schema error finding")
    void emptyXmlShouldReturnError() {
        List<ValidationFinding> findings = validator.validateXml("");
        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().code()).isEqualTo(Pacs008XmlValidator.CODE_SCHEMA_ERROR);
    }
}
