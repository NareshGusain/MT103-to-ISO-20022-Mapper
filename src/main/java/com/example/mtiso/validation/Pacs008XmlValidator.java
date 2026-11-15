package com.example.mtiso.validation;

import org.springframework.stereotype.Component;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates generated ISO 20022 pacs.008 XML against the official pacs.008.001.14 XSD schema,
 * converting XML schema errors and warnings into domain ValidationFindings.
 */
@Component
public class Pacs008XmlValidator {

    public static final String CODE_SCHEMA_ERROR = "SCHEMA_VALIDATION_ERROR";
    public static final String CODE_SCHEMA_WARNING = "SCHEMA_VALIDATION_WARNING";

    private static final Pattern ELEMENT_PATTERN = Pattern.compile("element '([^']+)'");

    private final Schema schema;

    public Pacs008XmlValidator() {
        this("/xsd/pacs.008.001.14.xsd");
    }

    public Pacs008XmlValidator(String schemaClasspath) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // Disable external DTD and schema access for secure XML processing
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            URL schemaUrl = getClass().getResource(schemaClasspath);
            if (schemaUrl == null) {
                throw new IllegalStateException("XSD schema not found on classpath: " + schemaClasspath);
            }
            this.schema = factory.newSchema(schemaUrl);
        } catch (SAXException e) {
            throw new IllegalStateException("Failed to compile pacs.008 XSD schema: " + schemaClasspath, e);
        }
    }

    /**
     * Validates XML string against pacs.008.001.14 XSD and returns any schema findings.
     */
    public List<ValidationFinding> validateXml(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return List.of(ValidationFinding.error(CODE_SCHEMA_ERROR, "XML", "XML content cannot be null or empty"));
        }

        List<ValidationFinding> findings = new ArrayList<>();
        try {
            Validator validator = schema.newValidator();
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    findings.add(ValidationFinding.warning(CODE_SCHEMA_WARNING, extractTarget(e), formatMessage(e)));
                }

                @Override
                public void error(SAXParseException e) {
                    findings.add(ValidationFinding.error(CODE_SCHEMA_ERROR, extractTarget(e), formatMessage(e)));
                }

                @Override
                public void fatalError(SAXParseException e) {
                    findings.add(ValidationFinding.error(CODE_SCHEMA_ERROR, extractTarget(e), formatMessage(e)));
                }
            });

            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (Exception e) {
            if (findings.isEmpty()) {
                findings.add(ValidationFinding.error(CODE_SCHEMA_ERROR, "XML", e.getMessage()));
            }
        }

        return Collections.unmodifiableList(findings);
    }

    private String extractTarget(SAXParseException e) {
        String msg = e.getMessage();
        if (msg != null) {
            Matcher matcher = ELEMENT_PATTERN.matcher(msg);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return String.format("Line %d", e.getLineNumber());
    }

    private String formatMessage(SAXParseException e) {
        return String.format("Schema validation at line %d, column %d: %s",
                e.getLineNumber(), e.getColumnNumber(), e.getMessage());
    }
}

