package com.example.mtiso.parser;

import java.util.Objects;

/**
 * Exception thrown when an MT103 message violates SWIFT FIN structural or format specifications.
 * Identifies the offending tag (if applicable) and the 1-based source line number.
 */
public class MT103ParseException extends RuntimeException {

    private final String tag;
    private final int line;

    public MT103ParseException(String tag, int line, String reason) {
        super(formatMessage(tag, line, reason));
        this.tag = tag;
        this.line = line;
    }

    public MT103ParseException(int line, String reason) {
        super(String.format("Parse error at line %d: %s", line, reason));
        this.tag = null;
        this.line = line;
    }

    private static String formatMessage(String tag, int line, String reason) {
        if (tag != null && !tag.isBlank()) {
            return String.format("Parse error on tag '%s' at line %d: %s", tag, line, reason);
        }
        return String.format("Parse error at line %d: %s", line, reason);
    }

    public String getTag() {
        return tag;
    }

    public int getLine() {
        return line;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MT103ParseException that = (MT103ParseException) o;
        return line == that.line && Objects.equals(tag, that.tag) && Objects.equals(getMessage(), that.getMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, line, getMessage());
    }
}

