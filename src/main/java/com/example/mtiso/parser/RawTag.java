package com.example.mtiso.parser;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an immutable parsed SWIFT FIN tag with its raw string value, individual lines,
 * and the 1-based source line number where it appeared.
 */
public record RawTag(
        String tag,
        String value,
        List<String> lines,
        int lineNumber
) {

    public RawTag {
        Objects.requireNonNull(tag, "tag must not be null");
        Objects.requireNonNull(value, "value must not be null");
        lines = lines != null ? List.copyOf(lines) : List.copyOf(Arrays.asList(value.split("\r?\n", -1)));
    }

    public static RawTag of(String tag, String value, int lineNumber) {
        String normalizedTag = tag.startsWith(":") && tag.endsWith(":") ? tag : ":" + tag.replaceAll("^:+|:+$", "") + ":";
        List<String> lineList = Arrays.asList(value.split("\r?\n", -1));
        return new RawTag(normalizedTag, value, lineList, lineNumber);
    }

    /**
     * Returns tag identifier without colons (e.g. '20' or '50K').
     */
    public String tagCode() {
        return tag.replaceAll("^:+|:+$", "");
    }
}

