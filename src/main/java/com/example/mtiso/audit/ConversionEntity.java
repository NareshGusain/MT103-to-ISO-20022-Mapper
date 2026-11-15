package com.example.mtiso.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity representing an immutable audit record for an MT103 to pacs.008 conversion.
 */
@Entity
@Table(name = "conversions", indexes = {
        @Index(name = "idx_conversions_input_hash", columnList = "input_hash"),
        @Index(name = "idx_conversions_received_at", columnList = "received_at")
})
public class ConversionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
    private String inputText;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "pacs_xml", columnDefinition = "TEXT")
    private String pacsXml;

    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "tool_version", nullable = false, length = 32)
    private String toolVersion;

    @OneToMany(mappedBy = "conversion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ApprovalEntity> approvals = new ArrayList<>();

    public ConversionEntity() {
    }

    public ConversionEntity(
            UUID id,
            OffsetDateTime receivedAt,
            String inputHash,
            String inputText,
            String status,
            String pacsXml,
            String reportJson,
            String toolVersion
    ) {
        this.id = id;
        this.receivedAt = receivedAt;
        this.inputHash = inputHash;
        this.inputText = inputText;
        this.status = status;
        this.pacsXml = pacsXml;
        this.reportJson = reportJson;
        this.toolVersion = toolVersion;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getInputHash() {
        return inputHash;
    }

    public void setInputHash(String inputHash) {
        this.inputHash = inputHash;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPacsXml() {
        return pacsXml;
    }

    public void setPacsXml(String pacsXml) {
        this.pacsXml = pacsXml;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
    }

    public String getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    public List<ApprovalEntity> getApprovals() {
        return approvals;
    }

    public void setApprovals(List<ApprovalEntity> approvals) {
        this.approvals = approvals;
    }

    public void addApproval(ApprovalEntity approval) {
        approvals.add(approval);
        approval.setConversion(this);
    }
}
