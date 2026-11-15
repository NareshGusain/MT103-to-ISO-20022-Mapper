package com.example.mtiso.report;

import com.example.mtiso.mapper.MappingNote;
import com.example.mtiso.mapper.MappingResult;
import com.example.mtiso.mapper.MappingStatus;
import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.RawTag;
import com.example.mtiso.validation.Severity;
import com.example.mtiso.validation.ValidationFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates mapping outcomes, data truncation, unmapped tags, address structuredness,
 * and validation findings to generate an audit-ready ConversionReport with an operational verdict.
 */
@Component
public class ConversionReportGenerator {

    public ConversionReport generateReport(
            MT103Message message,
            MappingResult mappingResult,
            List<ValidationFinding> findings
    ) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(mappingResult, "mappingResult must not be null");
        findings = findings != null ? findings : Collections.emptyList();

        List<MappingNote> notes = mappingResult.notes();

        // 1. Calculate Field Summary Counts
        int mappedCount = 0;
        int assumedCount = 0;
        int truncatedCount = 0;
        int unmappedCount = 0;

        for (MappingNote note : notes) {
            switch (note.status()) {
                case MAPPED -> mappedCount++;
                case ASSUMED -> assumedCount++;
                case TRUNCATED -> truncatedCount++;
                case UNMAPPED -> unmappedCount++;
            }
        }
        // Account for unknown tags that were unmapped
        unmappedCount += message.unknownTags().size();

        FieldSummary summary = new FieldSummary(mappedCount, assumedCount, truncatedCount, unmappedCount);

        // 2. Identify Data Loss & Truncations
        List<DataLossItem> dataLoss = new ArrayList<>();
        for (MappingNote note : notes) {
            if (note.status() == MappingStatus.TRUNCATED) {
                dataLoss.add(new DataLossItem(
                        note.field(),
                        "Original content",
                        "Truncated content",
                        note.detail()
                ));
            }
        }
        for (RawTag unknownTag : message.unknownTags()) {
            dataLoss.add(new DataLossItem(
                    unknownTag.tag(),
                    unknownTag.value(),
                    "null",
                    String.format("Tag %s is not supported in pacs.008 customer credit transfer; content dropped", unknownTag.tag())
            ));
        }

        // 3. Inspect Address Quality (Unstructured Free-Text Warnings)
        List<String> addressWarnings = new ArrayList<>();
        checkAddressQuality(message, notes, addressWarnings);

        // 4. Determine One-Line Verdict & Decision Rule
        long errorCount = findings.stream().filter(ValidationFinding::isError).count();
        long warningCount = findings.stream().filter(ValidationFinding::isWarning).count();

        ConversionVerdict verdict;
        String verdictRule;

        if (errorCount > 0) {
            verdict = ConversionVerdict.BLOCK;
            verdictRule = String.format("BLOCK: Conversion rejected due to %d fatal validation or schema error(s).", errorCount);
        } else if (truncatedCount > 0 || !dataLoss.isEmpty() || assumedCount > 0 || !addressWarnings.isEmpty() || warningCount > 0) {
            verdict = ConversionVerdict.REVIEW;
            verdictRule = "REVIEW: Payment conversion contains assumptions, unstructured addresses, or data loss requiring operational sign-off.";
        } else {
            verdict = ConversionVerdict.SAFE;
            verdictRule = "SAFE: All mandatory and optional fields mapped deterministically without data loss, assumptions, or schema errors.";
        }

        // 5. Build Markdown Representation
        String markdown = buildMarkdown(verdict, verdictRule, summary, dataLoss, addressWarnings, findings);

        return new ConversionReport(
                verdict,
                verdictRule,
                summary,
                Collections.unmodifiableList(dataLoss),
                Collections.unmodifiableList(addressWarnings),
                markdown
        );
    }

    private void checkAddressQuality(MT103Message message, List<MappingNote> notes, List<String> addressWarnings) {
        boolean debtorStructured = notes.stream()
                .anyMatch(n -> ":50K:[Address]".equals(n.field()) && n.status() == MappingStatus.MAPPED);

        if (!debtorStructured) {
            // Check Debtor (:50K:) address lines
            message.tag50K().ifPresent(tag -> {
                List<String> lines = tag.lines();
                boolean hasAccount = !lines.isEmpty() && lines.get(0).trim().startsWith("/");
                int addressStartIndex = hasAccount ? 2 : 1;
                if (lines.size() > addressStartIndex) {
                    addressWarnings.add("Debtor (:50K:) contains unstructured free-text address lines without separate town, street, or postal code elements.");
                }
            });
        }

        boolean creditorStructured = notes.stream()
                .anyMatch(n -> ":59:[Address]".equals(n.field()) && n.status() == MappingStatus.MAPPED);

        if (!creditorStructured) {
            // Check Creditor (:59:) address lines
            message.tag59().ifPresent(tag -> {
                List<String> lines = tag.lines();
                boolean hasAccount = !lines.isEmpty() && lines.get(0).trim().startsWith("/");
                int addressStartIndex = hasAccount ? 2 : 1;
                if (lines.size() > addressStartIndex) {
                    addressWarnings.add("Creditor (:59:) contains unstructured free-text address lines without separate town, street, or postal code elements.");
                }
            });
        }
    }

    private String buildMarkdown(
            ConversionVerdict verdict,
            String verdictRule,
            FieldSummary summary,
            List<DataLossItem> dataLoss,
            List<String> addressWarnings,
            List<ValidationFinding> findings
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("# MT103 to ISO 20022 Conversion Risk Report\n\n");
        sb.append("**Verdict:** `").append(verdict.name()).append("`  \n");
        sb.append("**Decision Rule:** ").append(verdictRule).append("\n\n");

        sb.append("## Field Mapping Summary\n\n");
        sb.append("| Field Status | Count |\n");
        sb.append("| :--- | :--- |\n");
        sb.append("| Mapped | ").append(summary.mappedCount()).append(" |\n");
        sb.append("| Assumed | ").append(summary.assumedCount()).append(" |\n");
        sb.append("| Truncated | ").append(summary.truncatedCount()).append(" |\n");
        sb.append("| Unmapped / Dropped | ").append(summary.unmappedCount()).append(" |\n\n");

        sb.append("## Address Quality Warnings\n\n");
        if (addressWarnings.isEmpty()) {
            sb.append("_No unstructured address issues identified._\n\n");
        } else {
            for (String warn : addressWarnings) {
                sb.append("- ⚠️ ").append(warn).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Data Loss & Truncations\n\n");
        if (dataLoss.isEmpty()) {
            sb.append("_Zero data loss: no fields were truncated or dropped._\n\n");
        } else {
            sb.append("| Field | Original Value | Mapped Value | Impact / Reason |\n");
            sb.append("| :--- | :--- | :--- | :--- |\n");
            for (DataLossItem item : dataLoss) {
                sb.append("| `").append(escapeMarkdown(item.field())).append("` | `")
                        .append(escapeMarkdown(item.originalValue())).append("` | `")
                        .append(escapeMarkdown(item.mappedValue())).append("` | ")
                        .append(escapeMarkdown(item.reason())).append(" |\n");
            }
            sb.append("\n");
        }

        if (!findings.isEmpty()) {
            sb.append("## Validation Findings\n\n");
            sb.append("| Severity | Code | Element / Tag | Message |\n");
            sb.append("| :--- | :--- | :--- | :--- |\n");
            for (ValidationFinding finding : findings) {
                sb.append("| ").append(finding.severity().name()).append(" | `")
                        .append(finding.code()).append("` | `")
                        .append(escapeMarkdown(finding.tag())).append("` | ")
                        .append(escapeMarkdown(finding.message())).append(" |\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String escapeMarkdown(String input) {
        if (input == null) return "null";
        return input.replace("|", "\\|").replace("\n", " ");
    }
}

