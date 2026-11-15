package com.example.mtiso.parser;

import java.util.*;

/**
 * Immutable representation of a parsed SWIFT MT103 message.
 * Holds standard required tags (:20:, :23B:, :32A:, :50K:, :59:, :70:, :71A:),
 * envelope headers, and any unknown/additional tags encountered in Block 4.
 */
public final class MT103Message {

    private final SwiftHeaders headers;
    private final RawTag tag20;
    private final RawTag tag23B;
    private final RawTag tag32A;
    private final RawTag tag50K;
    private final RawTag tag59;
    private final RawTag tag70;
    private final RawTag tag71A;
    private final List<RawTag> unknownTags;
    private final List<RawTag> allTags;
    private final Map<String, RawTag> tagIndex;

    public MT103Message(
            SwiftHeaders headers,
            RawTag tag20,
            RawTag tag23B,
            RawTag tag32A,
            RawTag tag50K,
            RawTag tag59,
            RawTag tag70,
            RawTag tag71A,
            List<RawTag> unknownTags,
            List<RawTag> allTags
    ) {
        this.headers = headers != null ? headers : SwiftHeaders.empty();
        this.tag20 = tag20;
        this.tag23B = tag23B;
        this.tag32A = tag32A;
        this.tag50K = tag50K;
        this.tag59 = tag59;
        this.tag70 = tag70;
        this.tag71A = tag71A;
        this.unknownTags = unknownTags != null ? List.copyOf(unknownTags) : List.of();
        this.allTags = allTags != null ? List.copyOf(allTags) : List.of();

        Map<String, RawTag> index = new HashMap<>();
        for (RawTag t : this.allTags) {
            index.put(normalizeTagCode(t.tagCode()), t);
        }
        this.tagIndex = Collections.unmodifiableMap(index);
    }

    public SwiftHeaders headers() {
        return headers;
    }

    public Optional<RawTag> tag20() {
        return Optional.ofNullable(tag20);
    }

    public Optional<RawTag> tag23B() {
        return Optional.ofNullable(tag23B);
    }

    public Optional<RawTag> tag32A() {
        return Optional.ofNullable(tag32A);
    }

    public Optional<RawTag> tag50K() {
        return Optional.ofNullable(tag50K);
    }

    public Optional<RawTag> tag59() {
        return Optional.ofNullable(tag59);
    }

    public Optional<RawTag> tag70() {
        return Optional.ofNullable(tag70);
    }

    public Optional<RawTag> tag71A() {
        return Optional.ofNullable(tag71A);
    }

    public List<RawTag> unknownTags() {
        return unknownTags;
    }

    public List<RawTag> allTags() {
        return allTags;
    }

    public Optional<RawTag> getTag(String tag) {
        if (tag == null) return Optional.empty();
        return Optional.ofNullable(tagIndex.get(normalizeTagCode(tag)));
    }

    public Optional<String> getTagValue(String tag) {
        return getTag(tag).map(RawTag::value);
    }

    public List<String> getTagLines(String tag) {
        return getTag(tag).map(RawTag::lines).orElse(List.of());
    }

    private static String normalizeTagCode(String tag) {
        return tag.replaceAll("^:+|:+$", "").toUpperCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MT103Message that = (MT103Message) o;
        return Objects.equals(headers, that.headers) &&
                Objects.equals(tag20, that.tag20) &&
                Objects.equals(tag23B, that.tag23B) &&
                Objects.equals(tag32A, that.tag32A) &&
                Objects.equals(tag50K, that.tag50K) &&
                Objects.equals(tag59, that.tag59) &&
                Objects.equals(tag70, that.tag70) &&
                Objects.equals(tag71A, that.tag71A) &&
                Objects.equals(unknownTags, that.unknownTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(headers, tag20, tag23B, tag32A, tag50K, tag59, tag70, tag71A, unknownTags);
    }

    @Override
    public String toString() {
        return "MT103Message{" +
                "tag20=" + (tag20 != null ? tag20.value() : "null") +
                ", tag23B=" + (tag23B != null ? tag23B.value() : "null") +
                ", tag32A=" + (tag32A != null ? tag32A.value() : "null") +
                ", tag50K=" + (tag50K != null ? tag50K.value() : "null") +
                ", tag59=" + (tag59 != null ? tag59.value() : "null") +
                ", tag70=" + (tag70 != null ? tag70.value() : "null") +
                ", tag71A=" + (tag71A != null ? tag71A.value() : "null") +
                ", unknownTagsCount=" + unknownTags.size() +
                '}';
    }
}

