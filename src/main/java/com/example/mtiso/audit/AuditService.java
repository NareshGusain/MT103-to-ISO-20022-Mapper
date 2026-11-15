package com.example.mtiso.audit;

import com.example.mtiso.report.ConversionReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing persistence and audit retrieval of MT103 conversions and approvals.
 */
@Service
public class AuditService {

    public static final String TOOL_VERSION = "1.0.0";

    private final ConversionRepository conversionRepository;
    private final ApprovalRepository approvalRepository;
    private final ObjectMapper objectMapper;

    public AuditService(
            ConversionRepository conversionRepository,
            ApprovalRepository approvalRepository,
            ObjectMapper objectMapper
    ) {
        this.conversionRepository = Objects.requireNonNull(conversionRepository, "conversionRepository must not be null");
        this.approvalRepository = Objects.requireNonNull(approvalRepository, "approvalRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Persists a conversion audit record into the conversions table.
     */
    @Transactional
    public ConversionEntity recordConversion(
            String inputText,
            String status,
            String pacsXml,
            ConversionReport report,
            String toolVersion
    ) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String hash = computeSha256(inputText);
        String reportJson = serializeReport(report);
        String version = (toolVersion != null && !toolVersion.isBlank()) ? toolVersion : TOOL_VERSION;

        ConversionEntity entity = new ConversionEntity(
                id,
                now,
                hash,
                inputText,
                status,
                pacsXml,
                reportJson,
                version
        );

        return conversionRepository.save(entity);
    }

    /**
     * Updates an existing conversion record with re-run outputs (XML, report, status).
     */
    @Transactional
    public ConversionEntity updateConversion(
            UUID id,
            String status,
            String pacsXml,
            ConversionReport report
    ) {
        ConversionEntity entity = conversionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Conversion not found with id: " + id));

        entity.setStatus(status);
        entity.setPacsXml(pacsXml);
        entity.setReportJson(serializeReport(report));

        return conversionRepository.save(entity);
    }

    /**
     * Retrieves the raw ConversionEntity by ID.
     */
    @Transactional(readOnly = true)
    public Optional<ConversionEntity> findEntityById(UUID id) {
        return conversionRepository.findById(id);
    }

    /**
     * Retrieves a stored conversion by its unique UUID.
     */
    @Transactional(readOnly = true)
    public Optional<ConversionDetailResponse> getConversionDetail(UUID id) {
        return conversionRepository.findById(id).map(this::toDetailResponse);
    }

    /**
     * Records an operational review approval on an existing conversion record.
     */
    @Transactional
    public ApprovalDto recordApproval(UUID conversionId, String action, String actor, String note) {
        ConversionEntity conversion = conversionRepository.findById(conversionId)
                .orElseThrow(() -> new IllegalArgumentException("Conversion not found with id: " + conversionId));

        ApprovalEntity approval = new ApprovalEntity(
                UUID.randomUUID(),
                conversion,
                action,
                actor,
                OffsetDateTime.now(ZoneOffset.UTC),
                note
        );

        ApprovalEntity saved = approvalRepository.save(approval);
        return ApprovalDto.from(saved);
    }

    private ConversionDetailResponse toDetailResponse(ConversionEntity entity) {
        ConversionReport report = deserializeReport(entity.getReportJson());
        List<ApprovalDto> approvals = entity.getApprovals().stream()
                .map(ApprovalDto::from)
                .toList();

        return new ConversionDetailResponse(
                entity.getId(),
                entity.getReceivedAt(),
                entity.getInputHash(),
                entity.getInputText(),
                entity.getStatus(),
                entity.getPacsXml(),
                report,
                entity.getReportJson(),
                entity.getToolVersion(),
                approvals
        );
    }

    private String serializeReport(ConversionReport report) {
        if (report == null) return null;
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ConversionReport to JSON", e);
        }
    }

    private ConversionReport deserializeReport(String reportJson) {
        if (reportJson == null || reportJson.isBlank()) return null;
        try {
            return objectMapper.readValue(reportJson, ConversionReport.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static String computeSha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

