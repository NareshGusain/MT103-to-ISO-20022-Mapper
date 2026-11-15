package com.example.mtiso.mapper;

import com.example.mtiso.address.AddressSuggestion;
import com.example.mtiso.address.PartyType;
import com.example.mtiso.api.ConversionResponse;
import com.example.mtiso.audit.AuditService;
import com.example.mtiso.audit.ConversionEntity;
import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.MT103Parser;
import com.example.mtiso.report.ConversionReport;
import com.example.mtiso.report.ConversionReportGenerator;
import com.example.mtiso.validation.MT103Validator;
import com.example.mtiso.validation.Pacs008XmlValidator;
import com.example.mtiso.validation.ValidationFinding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Orchestrates MT103 parsing, semantic validation, ISO 20022 pacs.008 mapping,
 * downstream XSD schema validation, risk report generation, and audit logging.
 */
@Service
public class ConversionService {

    private final MT103Parser parser;
    private final MT103Validator validator;
    private final Pacs008Mapper mapper;
    private final Pacs008XmlValidator xmlValidator;
    private final ConversionReportGenerator reportGenerator;
    private final AuditService auditService;

    @Autowired
    public ConversionService(
            MT103Parser parser,
            MT103Validator validator,
            Pacs008Mapper mapper,
            Pacs008XmlValidator xmlValidator,
            ConversionReportGenerator reportGenerator,
            AuditService auditService
    ) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.xmlValidator = Objects.requireNonNull(xmlValidator, "xmlValidator must not be null");
        this.reportGenerator = Objects.requireNonNull(reportGenerator, "reportGenerator must not be null");
        this.auditService = auditService;
    }

    public ConversionService(
            MT103Parser parser,
            MT103Validator validator,
            Pacs008Mapper mapper,
            Pacs008XmlValidator xmlValidator,
            ConversionReportGenerator reportGenerator
    ) {
        this(parser, validator, mapper, xmlValidator, reportGenerator, null);
    }

    /**
     * Converts plain-text MT103 into pacs.008 XML, executes MT103 and XSD validations,
     * produces a ConversionReport, persists the audit record, and returns an aggregated ConversionResponse.
     */
    public ConversionResponse convert(String rawMt103) {
        return convert(rawMt103, Collections.emptyMap());
    }

    /**
     * Converts plain-text MT103 applying approved address suggestions.
     */
    public ConversionResponse convert(String rawMt103, Map<PartyType, AddressSuggestion> approvedSuggestions) {
        Objects.requireNonNull(rawMt103, "rawMt103 message content must not be null");

        // 1. Parse SWIFT FIN text (throws MT103ParseException on syntax violations)
        MT103Message mt103Message = parser.parse(rawMt103);

        // 2. MT103 Semantic & format validation
        List<ValidationFinding> mt103Findings = validator.validateFindings(mt103Message);

        // 3. Deterministic mapping to pacs.008 XML
        MappingResult mappingResult = mapper.map(mt103Message, approvedSuggestions);

        // 4. XSD schema validation on generated XML
        List<ValidationFinding> schemaFindings = xmlValidator.validateXml(mappingResult.xml());

        // 5. Aggregate all findings
        List<ValidationFinding> allFindings = new ArrayList<>(mt103Findings);
        allFindings.addAll(schemaFindings);

        boolean hasErrors = allFindings.stream().anyMatch(ValidationFinding::isError);
        String status = hasErrors ? "VALIDATION_FAILED" : "SUCCESS";

        // 6. Generate Risk and Data Loss Report
        ConversionReport report = reportGenerator.generateReport(mt103Message, mappingResult, allFindings);

        // 7. Persist to Audit Store
        UUID conversionId;
        if (auditService != null) {
            ConversionEntity entity = auditService.recordConversion(
                    rawMt103,
                    status,
                    mappingResult.xml(),
                    report,
                    AuditService.TOOL_VERSION
            );
            conversionId = entity.getId();
        } else {
            conversionId = UUID.randomUUID();
        }

        return new ConversionResponse(
                conversionId,
                status,
                mappingResult.xml(),
                Collections.unmodifiableList(allFindings),
                mappingResult.notes(),
                report
        );
    }

    /**
     * Re-runs an existing conversion applying approved address suggestions, updating the conversion record.
     */
    public ConversionResponse reconvert(UUID existingConversionId, Map<PartyType, AddressSuggestion> approvedSuggestions) {
        if (auditService == null) {
            throw new IllegalStateException("AuditService is required for reconversion");
        }
        ConversionEntity existing = auditService.findEntityById(existingConversionId)
                .orElseThrow(() -> new IllegalArgumentException("Conversion not found with id: " + existingConversionId));

        MT103Message mt103Message = parser.parse(existing.getInputText());
        List<ValidationFinding> mt103Findings = validator.validateFindings(mt103Message);
        MappingResult mappingResult = mapper.map(mt103Message, approvedSuggestions);
        List<ValidationFinding> schemaFindings = xmlValidator.validateXml(mappingResult.xml());

        List<ValidationFinding> allFindings = new ArrayList<>(mt103Findings);
        allFindings.addAll(schemaFindings);

        boolean hasErrors = allFindings.stream().anyMatch(ValidationFinding::isError);
        String status = hasErrors ? "VALIDATION_FAILED" : "SUCCESS";

        ConversionReport report = reportGenerator.generateReport(mt103Message, mappingResult, allFindings);

        auditService.updateConversion(existingConversionId, status, mappingResult.xml(), report);

        return new ConversionResponse(
                existingConversionId,
                status,
                mappingResult.xml(),
                Collections.unmodifiableList(allFindings),
                mappingResult.notes(),
                report
        );
    }
}
