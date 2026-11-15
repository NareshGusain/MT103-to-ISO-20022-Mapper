package com.example.mtiso.mapper;

/**
 * Status of an individual field transformation during MT103 to ISO 20022 mapping.
 */
public enum MappingStatus {
    MAPPED,
    TRUNCATED,
    UNMAPPED,
    ASSUMED
}

