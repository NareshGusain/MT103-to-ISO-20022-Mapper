package com.example.mtiso.mapper;

import com.example.mtiso.iso.pacs008.Document;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome of mapping an MT103Message into an ISO 20022 pacs.008 XML message.
 * Contains the serialized XML, the underlying JAXB Document model, and all mapping notes.
 */
public record MappingResult(
        String xml,
        Document document,
        List<MappingNote> notes
) {
    public MappingResult {
        Objects.requireNonNull(xml, "xml must not be null");
        Objects.requireNonNull(document, "document must not be null");
        notes = notes != null ? List.copyOf(notes) : Collections.emptyList();
    }
}

