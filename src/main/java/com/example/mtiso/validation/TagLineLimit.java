package com.example.mtiso.validation;

/**
 * Configurable constraints for line counts and line lengths of a SWIFT FIN tag.
 */
public record TagLineLimit(
        int maxLines,
        int maxLineLength
) {

    public TagLineLimit {
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be at least 1");
        }
        if (maxLineLength < 1) {
            throw new IllegalArgumentException("maxLineLength must be at least 1");
        }
    }

    public static TagLineLimit of(int maxLines, int maxLineLength) {
        return new TagLineLimit(maxLines, maxLineLength);
    }
}

