package com.example.mtiso.parser;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic parser for SWIFT MT103 FIN messages.
 * Reads SWIFT blocks ({1:...}{2:...}{3:...}{4:...-}{5:...}), extracts MT103 tags,
 * validates line length constraints, preserves unknown tags, and returns an immutable MT103Message.
 */
@Component
public class MT103Parser {

    private static final Pattern BLOCK1_PATTERN = Pattern.compile("\\{1:([^}]+)\\}");
    private static final Pattern BLOCK2_PATTERN = Pattern.compile("\\{2:([^}]+)\\}");
    private static final Pattern BLOCK3_PATTERN = Pattern.compile("\\{3:(\\{[^}]+\\}+)\\}");
    private static final Pattern BLOCK5_PATTERN = Pattern.compile("\\{5:(\\{[^}]+\\}+)\\}");
    private static final Pattern TAG_PATTERN = Pattern.compile("^:([0-9]{2}[A-Za-z]?):(.*)$");

    private static final Set<String> KNOWN_TAGS = Set.of(
            "20", "23B", "32A", "50K", "59", "70", "71A"
    );

    // SWIFT FIN Standard maximum characters per line per field
    private static final Map<String, Integer> FIELD_MAX_LINE_LENGTH = Map.of(
            "20", 16,
            "23B", 4,
            "32A", 24,
            "50K", 35,
            "59", 35,
            "70", 35,
            "71A", 3
    );

    public MT103Message parse(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            throw new MT103ParseException(1, "Message content cannot be null or empty");
        }

        SwiftHeaders headers = extractHeaders(rawMessage);
        List<RawTag> tags = parseBlock4Tags(rawMessage);

        RawTag tag20 = null;
        RawTag tag23B = null;
        RawTag tag32A = null;
        RawTag tag50K = null;
        RawTag tag59 = null;
        RawTag tag70 = null;
        RawTag tag71A = null;
        List<RawTag> unknownTags = new ArrayList<>();

        for (RawTag tag : tags) {
            String code = tag.tagCode().toUpperCase(Locale.ROOT);
            switch (code) {
                case "20" -> tag20 = tag;
                case "23B" -> tag23B = tag;
                case "32A" -> tag32A = tag;
                case "50K" -> tag50K = tag;
                case "59" -> tag59 = tag;
                case "70" -> tag70 = tag;
                case "71A" -> tag71A = tag;
                default -> unknownTags.add(tag);
            }
        }

        return new MT103Message(
                headers,
                tag20,
                tag23B,
                tag32A,
                tag50K,
                tag59,
                tag70,
                tag71A,
                Collections.unmodifiableList(unknownTags),
                Collections.unmodifiableList(tags)
        );
    }

    private SwiftHeaders extractHeaders(String rawMessage) {
        String b1 = findMatch(BLOCK1_PATTERN, rawMessage);
        String b2 = findMatch(BLOCK2_PATTERN, rawMessage);
        String b3 = findMatch(BLOCK3_PATTERN, rawMessage);
        String b5 = findMatch(BLOCK5_PATTERN, rawMessage);
        return new SwiftHeaders(b1, b2, b3, b5);
    }

    private String findMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private List<RawTag> parseBlock4Tags(String rawMessage) {
        String[] lines = rawMessage.split("\r?\n", -1);
        boolean inBlock4 = false;
        boolean block4Closed = false;

        List<RawTag> parsedTags = new ArrayList<>();
        TagAccumulator currentTag = null;

        for (int i = 0; i < lines.length; i++) {
            int lineNum = i + 1;
            String line = lines[i];
            String trimmed = line.trim();

            // Ignore comments outside or inside blocks (for synthetic test file labeling)
            if (trimmed.startsWith("#")) {
                continue;
            }

            if (!inBlock4) {
                if (line.contains("{4:")) {
                    inBlock4 = true;
                    // Check if block 4 starts on the same line after {4:
                    int block4Index = line.indexOf("{4:");
                    String remainder = line.substring(block4Index + 3).trim();
                    if (!remainder.isEmpty()) {
                        currentTag = processLine(remainder, lineNum, currentTag, parsedTags);
                    }
                }
                continue;
            }

            // Inside Block 4
            if (trimmed.equals("-}") || trimmed.endsWith("-}")) {
                block4Closed = true;
                // If there was content before -} on the same line
                String beforeEnd = trimmed.substring(0, trimmed.length() - 2).trim();
                if (!beforeEnd.isEmpty()) {
                    currentTag = processLine(beforeEnd, lineNum, currentTag, parsedTags);
                }
                if (currentTag != null) {
                    parsedTags.add(currentTag.buildAndValidate());
                    currentTag = null;
                }
                break;
            }

            if (trimmed.isEmpty()) {
                continue;
            }

            currentTag = processLine(line, lineNum, currentTag, parsedTags);
        }

        if (!inBlock4) {
            throw new MT103ParseException(1, "Missing SWIFT Block 4 ({4:)");
        }
        if (!block4Closed) {
            throw new MT103ParseException(lines.length, "Block 4 is not closed with '-}'");
        }

        return parsedTags;
    }

    private TagAccumulator processLine(String line, int lineNum, TagAccumulator currentTag, List<RawTag> parsedTags) {
        if (line.startsWith(":")) {
            Matcher matcher = TAG_PATTERN.matcher(line);
            if (!matcher.matches()) {
                // Starts with colon but invalid tag syntax
                int secondColon = line.indexOf(':', 1);
                String tagCandidate = (secondColon > 1) ? line.substring(0, secondColon + 1) : line;
                throw new MT103ParseException(tagCandidate, lineNum, "Malformed SWIFT tag format");
            }

            // Finalize previous tag
            if (currentTag != null) {
                parsedTags.add(currentTag.buildAndValidate());
            }

            String tagCode = matcher.group(1);
            String initialContent = matcher.group(2);
            return new TagAccumulator(tagCode, initialContent, lineNum);
        } else {
            // Continuation line for the current tag
            if (currentTag == null) {
                throw new MT103ParseException(lineNum, "Unexpected continuation line without an active tag: '" + line + "'");
            }
            currentTag.addLine(line, lineNum);
            return currentTag;
        }
    }

    private static class TagAccumulator {
        private final String tagCode;
        private final int startLineNumber;
        private final List<LineEntry> lines = new ArrayList<>();

        TagAccumulator(String tagCode, String initialContent, int lineNumber) {
            this.tagCode = tagCode;
            this.startLineNumber = lineNumber;
            this.lines.add(new LineEntry(initialContent, lineNumber));
        }

        void addLine(String content, int lineNumber) {
            this.lines.add(new LineEntry(content, lineNumber));
        }

        RawTag buildAndValidate() {
            String fullTag = ":" + tagCode + ":";
            Integer maxLength = FIELD_MAX_LINE_LENGTH.get(tagCode.toUpperCase(Locale.ROOT));

            List<String> contentLines = new ArrayList<>();
            for (LineEntry entry : lines) {
                if (maxLength != null && entry.content().length() > maxLength) {
                    throw new MT103ParseException(
                            fullTag,
                            entry.lineNumber(),
                            String.format("Line length (%d) exceeds SWIFT maximum of %d characters for field %s",
                                    entry.content().length(), maxLength, fullTag)
                    );
                }
                contentLines.add(entry.content());
            }

            String value = String.join("\n", contentLines);
            return new RawTag(fullTag, value, Collections.unmodifiableList(contentLines), startLineNumber);
        }
    }

    private record LineEntry(String content, int lineNumber) {}
}

