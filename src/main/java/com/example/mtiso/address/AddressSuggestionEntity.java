package com.example.mtiso.address;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a structured address suggestion for an MT103 party,
 * tracking field values, extraction confidence, and governance approval decisions.
 */
@Entity
@Table(name = "address_suggestions")
public class AddressSuggestionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversion_id", nullable = false)
    private UUID conversionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 16)
    private PartyType partyType;

    @Column(name = "original_address", nullable = false, columnDefinition = "TEXT")
    private String originalAddress;

    @Column(name = "street", length = 256)
    private String street;

    @Column(name = "street_confidence", nullable = false)
    private double streetConfidence;

    @Column(name = "town", length = 256)
    private String town;

    @Column(name = "town_confidence", nullable = false)
    private double townConfidence;

    @Column(name = "post_code", length = 64)
    private String postCode;

    @Column(name = "post_code_confidence", nullable = false)
    private double postCodeConfidence;

    @Column(name = "country", length = 64)
    private String country;

    @Column(name = "country_confidence", nullable = false)
    private double countryConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SuggestionStatus status;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    protected AddressSuggestionEntity() {
        // JPA requirement
    }

    public AddressSuggestionEntity(
            UUID id,
            UUID conversionId,
            PartyType partyType,
            String originalAddress,
            String street,
            double streetConfidence,
            String town,
            double townConfidence,
            String postCode,
            double postCodeConfidence,
            String country,
            double countryConfidence,
            SuggestionStatus status,
            String provider,
            OffsetDateTime createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.conversionId = Objects.requireNonNull(conversionId, "conversionId must not be null");
        this.partyType = Objects.requireNonNull(partyType, "partyType must not be null");
        this.originalAddress = Objects.requireNonNull(originalAddress, "originalAddress must not be null");
        this.street = street;
        this.streetConfidence = streetConfidence;
        this.town = town;
        this.townConfidence = townConfidence;
        this.postCode = postCode;
        this.postCodeConfidence = postCodeConfidence;
        this.country = country;
        this.countryConfidence = countryConfidence;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversionId() {
        return conversionId;
    }

    public PartyType getPartyType() {
        return partyType;
    }

    public String getOriginalAddress() {
        return originalAddress;
    }

    public String getStreet() {
        return street;
    }

    public double getStreetConfidence() {
        return streetConfidence;
    }

    public String getTown() {
        return town;
    }

    public double getTownConfidence() {
        return townConfidence;
    }

    public String getPostCode() {
        return postCode;
    }

    public double getPostCodeConfidence() {
        return postCodeConfidence;
    }

    public String getCountry() {
        return country;
    }

    public double getCountryConfidence() {
        return countryConfidence;
    }

    public SuggestionStatus getStatus() {
        return status;
    }

    public void setStatus(SuggestionStatus status) {
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(OffsetDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public AddressSuggestion toAddressSuggestion() {
        return new AddressSuggestion(
                new AddressField(street, streetConfidence),
                new AddressField(town, townConfidence),
                new AddressField(postCode, postCodeConfidence),
                new AddressField(country, countryConfidence),
                provider
        );
    }
}

