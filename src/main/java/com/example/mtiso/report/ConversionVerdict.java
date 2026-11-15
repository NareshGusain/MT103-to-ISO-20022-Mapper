package com.example.mtiso.report;

/**
 * High-level verdict determining whether a payment conversion is safe for straight-through processing,
 * requires human review, or must be blocked.
 */
public enum ConversionVerdict {
    SAFE,
    REVIEW,
    BLOCK
}

