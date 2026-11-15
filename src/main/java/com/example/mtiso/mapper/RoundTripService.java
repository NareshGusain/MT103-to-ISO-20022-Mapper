package com.example.mtiso.mapper;

import com.example.mtiso.iso.pacs008.Document;
import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.MT103Parser;
import com.example.mtiso.parser.RawTag;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service orchestrating bidirectional conversion (MT103 -> pacs.008 -> MT103) and
 * executing a strict tag-by-tag audit diff comparing the original parsed message
 * with the reconstructed result.
 *
 * Enforces rule: "Only claim what is supported: unsupported tags must show as UNSUPPORTED, never as EQUAL."
 */
@Service
public class RoundTripService {

    /**
     * Set of SWIFT MT103 tags supported for mapping to/from ISO 20022 pacs.008.001.14.
     */
    public static final Set<String> SUPPORTED_TAGS = Set.of(
            "20", "23B", "32A", "50K", "59", "70", "71A"
    );

    private final MT103Parser parser;
    private final Pacs008Mapper pacs008Mapper;
    private final Pacs008ToMt103 pacs008ToMt103;

    public RoundTripService(MT103Parser parser, Pacs008Mapper pacs008Mapper, Pacs008ToMt103 pacs008ToMt103) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.pacs008Mapper = Objects.requireNonNull(pacs008Mapper, "pacs008Mapper must not be null");
        this.pacs008ToMt103 = Objects.requireNonNull(pacs008ToMt103, "pacs008ToMt103 must not be null");
    }

    /**
     * Executes a full round trip from a raw MT103 string.
     */
    public RoundTripResult roundTrip(String rawMt103) {
        Objects.requireNonNull(rawMt103, "rawMt103 must not be null");
        MT103Message originalMessage = parser.parse(rawMt103);
        return roundTrip(originalMessage);
    }

    /**
     * Executes a full round trip from an already parsed MT103Message.
     */
    public RoundTripResult roundTrip(MT103Message originalMessage) {
        Objects.requireNonNull(originalMessage, "originalMessage must not be null");

        // 1. Forward Conversion: MT103 -> pacs.008 XML & Document
        MappingResult mappingResult = pacs008Mapper.map(originalMessage);
        String pacs008Xml = mappingResult.xml();
        Document document = mappingResult.document();

        // 2. Reverse Conversion: pacs.008 Document -> MT103
        String reconstructedRawText = pacs008ToMt103.toMt103Text(document);
        MT103Message reconstructedMessage = pacs008ToMt103.toMt103(document);

        // 3. Tag-by-Tag Comparison
        List<TagComparison> comparisons = compareTags(originalMessage, reconstructedMessage);

        return new RoundTripResult(
                originalMessage,
                pacs008Xml,
                reconstructedMessage,
                reconstructedRawText,
                comparisons
        );
    }

    private List<TagComparison> compareTags(MT103Message original, MT103Message reconstructed) {
        List<TagComparison> comparisons = new ArrayList<>();

        for (RawTag origTag : original.allTags()) {
            String tagCode = normalizeTag(origTag.tag());
            String displayTag = ":" + tagCode + ":";

            // Rule: Unsupported tags must show as UNSUPPORTED, never as EQUAL
            if (!SUPPORTED_TAGS.contains(tagCode)) {
                comparisons.add(TagComparison.unsupported(
                        displayTag,
                        origTag.value(),
                        String.format("Tag %s is not supported in MT103 <-> pacs.008 mapping; data dropped", displayTag)
                ));
                continue;
            }

            // Supported tag: check presence in reconstructed message
            Optional<RawTag> reconTagOpt = reconstructed.getTag(tagCode);
            if (reconTagOpt.isEmpty()) {
                comparisons.add(TagComparison.lost(
                        displayTag,
                        origTag.value(),
                        String.format("Supported tag %s was lost during round trip; not reconstructed from pacs.008", displayTag)
                ));
                continue;
            }

            // Supported tag present in both: compare normalized values
            String origNormalized = normalizeValue(origTag.value());
            String reconNormalized = normalizeValue(reconTagOpt.get().value());

            if (origNormalized.equals(reconNormalized)) {
                comparisons.add(TagComparison.equal(
                        displayTag,
                        origTag.value(),
                        "Field value preserved losslessly across round-trip conversion"
                ));
            } else {
                comparisons.add(TagComparison.changed(
                        displayTag,
                        origTag.value(),
                        reconTagOpt.get().value(),
                        String.format("Field value changed during round trip (original: '%s', reconstructed: '%s')",
                                origTag.value(), reconTagOpt.get().value())
                ));
            }
        }

        // Check for any tags present in reconstructed message that were absent in original
        Set<String> originalTagCodes = original.allTags().stream()
                .map(t -> normalizeTag(t.tag()))
                .collect(Collectors.toSet());

        for (RawTag reconTag : reconstructed.allTags()) {
            String tagCode = normalizeTag(reconTag.tag());
            if (!originalTagCodes.contains(tagCode)) {
                comparisons.add(TagComparison.changed(
                        ":" + tagCode + ":",
                        null,
                        reconTag.value(),
                        String.format("Tag :%s: was introduced during round trip", tagCode)
                ));
            }
        }

        return Collections.unmodifiableList(comparisons);
    }

    private static String normalizeTag(String tag) {
        return tag.replaceAll("^:+|:+$", "").toUpperCase(Locale.ROOT);
    }

    private static String normalizeValue(String val) {
        if (val == null) return "";
        return val.lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"))
                .strip();
    }
}

