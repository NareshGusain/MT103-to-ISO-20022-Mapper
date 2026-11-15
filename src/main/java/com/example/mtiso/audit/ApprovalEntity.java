package com.example.mtiso.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA Entity representing operational sign-off / review approval on a conversion record.
 */
@Entity
@Table(name = "approvals", indexes = {
        @Index(name = "idx_approvals_conversion_id", columnList = "conversion_id")
})
public class ApprovalEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversion_id", nullable = false)
    private ConversionEntity conversion;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    @Column(name = "decided_at", nullable = false)
    private OffsetDateTime decidedAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    public ApprovalEntity() {
    }

    public ApprovalEntity(
            UUID id,
            ConversionEntity conversion,
            String action,
            String actor,
            OffsetDateTime decidedAt,
            String note
    ) {
        this.id = id;
        this.conversion = conversion;
        this.action = action;
        this.actor = actor;
        this.decidedAt = decidedAt;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ConversionEntity getConversion() {
        return conversion;
    }

    public void setConversion(ConversionEntity conversion) {
        this.conversion = conversion;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(OffsetDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
