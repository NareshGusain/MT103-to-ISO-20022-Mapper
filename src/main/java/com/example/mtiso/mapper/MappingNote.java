package com.example.mtiso.mapper;

import java.util.Objects;

/**
 * Audit note recording how an individual SWIFT field was mapped to ISO 20022 pacs.008.
 */
public record MappingNote(
        String field,
        MappingStatus status,
        String detail
) {
    public MappingNote {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(detail, "detail must not be null");
    }

    public static MappingNote mapped(String field, String detail) {
        return new MappingNote(field, MappingStatus.MAPPED, detail);
    }

    public static MappingNote assumed(String field, String detail) {
        return new MappingNote(field, MappingStatus.ASSUMED, detail);
    }

    public static MappingNote truncated(String field, String detail) {
        return new MappingNote(field, MappingStatus.TRUNCATED, detail);
    }

    public static MappingNote unmapped(String field, String detail) {
        return new MappingNote(field, MappingStatus.UNMAPPED, detail);
    }
}

