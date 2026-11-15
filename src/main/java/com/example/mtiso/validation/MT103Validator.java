package com.example.mtiso.validation;

import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.RawTag;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates parsed MT103 messages against SWIFT FIN format specifications and semantic banking rules.
 * Produces a list of ValidationFindings (errors and warnings) without throwing exceptions.
 */
@Component
public class MT103Validator {

    public static final String CODE_TAG_MISSING = "TAG_MISSING";
    public static final String CODE_INVALID_DATE_FORMAT = "INVALID_DATE_FORMAT";
    public static final String CODE_INVALID_CURRENCY_CODE = "INVALID_CURRENCY_CODE";
    public static final String CODE_INVALID_AMOUNT_SEPARATOR = "INVALID_AMOUNT_SEPARATOR";
    public static final String CODE_INVALID_AMOUNT_FORMAT = "INVALID_AMOUNT_FORMAT";
    public static final String CODE_INVALID_CHARGE_CODE = "INVALID_CHARGE_CODE";
    public static final String CODE_LINE_COUNT_EXCEEDED = "LINE_COUNT_EXCEEDED";
    public static final String CODE_LINE_LENGTH_EXCEEDED = "LINE_LENGTH_EXCEEDED";
    public static final String CODE_NON_STANDARD_OPERATION_CODE = "NON_STANDARD_OPERATION_CODE";

    private static final List<String> MANDATORY_TAGS = List.of(
            "20", "23B", "32A", "50K", "59", "71A"
    );

    private static final Set<String> VALID_CHARGE_CODES = Set.of("OUR", "BEN", "SHA");

    // Strict YYMMDD date parser using strict resolver style
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    // Default line constraints per tag
    // ASSUMPTION - verify: 50K and 59 allow up to 5 lines (1 account line + 4 party name/address lines)
    // ASSUMPTION - verify: 70 allows up to 4 lines of 35 characters
    public static final Map<String, TagLineLimit> DEFAULT_LINE_LIMITS = Map.of(
            "20", TagLineLimit.of(1, 16),
            "23B", TagLineLimit.of(1, 4),
            "32A", TagLineLimit.of(1, 24),
            "50K", TagLineLimit.of(5, 35),
            "59", TagLineLimit.of(5, 35),
            "70", TagLineLimit.of(4, 35),
            "71A", TagLineLimit.of(1, 3)
    );

    // Preloaded valid ISO 4217 currency codes
    private static final Set<String> VALID_ISO_CURRENCIES;

    static {
        Set<String> currencies = new HashSet<>();
        for (Currency currency : Currency.getAvailableCurrencies()) {
            currencies.add(currency.getCurrencyCode());
        }
        VALID_ISO_CURRENCIES = Collections.unmodifiableSet(currencies);
    }

    private final Map<String, TagLineLimit> lineLimits;

    public MT103Validator() {
        this(DEFAULT_LINE_LIMITS);
    }

    public MT103Validator(Map<String, TagLineLimit> lineLimits) {
        this.lineLimits = Objects.requireNonNull(lineLimits, "lineLimits must not be null");
    }

    /**
     * Validates an MT103Message and returns an aggregated ValidationResult.
     */
    public ValidationResult validate(MT103Message message) {
        return ValidationResult.of(validateFindings(message));
    }

    /**
     * Validates an MT103Message and returns a list of individual findings.
     */
    public List<ValidationFinding> validateFindings(MT103Message message) {
        if (message == null) {
            return List.of(ValidationFinding.error("MESSAGE_NULL", "N/A", "MT103 message cannot be null"));
        }

        List<ValidationFinding> findings = new ArrayList<>();

        checkMandatoryTags(message, findings);
        checkTag32A(message, findings);
        checkTag71A(message, findings);
        checkTag23B(message, findings);
        checkLineLimits(message, findings);

        return Collections.unmodifiableList(findings);
    }

    private void checkMandatoryTags(MT103Message message, List<ValidationFinding> findings) {
        for (String tagCode : MANDATORY_TAGS) {
            String fullTag = ":" + tagCode + ":";
            if (message.getTag(tagCode).isEmpty()) {
                findings.add(ValidationFinding.error(
                        CODE_TAG_MISSING,
                        fullTag,
                        String.format("Mandatory field %s is missing from message", fullTag)
                ));
            }
        }
    }

    private void checkTag32A(MT103Message message, List<ValidationFinding> findings) {
        Optional<RawTag> tag32AOpt = message.tag32A();
        if (tag32AOpt.isEmpty()) {
            return; // Missing tag handled by checkMandatoryTags
        }

        String rawValue = tag32AOpt.get().value().trim();
        String tag = ":32A:";

        // Tag 32A format: 6!n (Date YYMMDD) + 3!a (Currency) + 15d (Amount with comma)
        if (rawValue.length() < 10) {
            findings.add(ValidationFinding.error(
                    CODE_INVALID_AMOUNT_FORMAT,
                    tag,
                    String.format("Field :32A: length (%d) is too short for 6!n3!a15d format: '%s'", rawValue.length(), rawValue)
            ));
            return;
        }

        String datePart = rawValue.substring(0, 6);
        String currencyPart = rawValue.substring(6, 9);
        String amountPart = rawValue.substring(9);

        // 1. Validate Date (YYMMDD)
        validateDatePart(datePart, tag, findings);

        // 2. Validate Currency (3!a ISO 4217)
        validateCurrencyPart(currencyPart, tag, findings);

        // 3. Validate Amount (15d with comma as decimal separator)
        validateAmountPart(amountPart, tag, findings);
    }

    private void validateDatePart(String datePart, String tag, List<ValidationFinding> findings) {
        if (!datePart.matches("^\\d{6}$")) {
            findings.add(ValidationFinding.error(
                    CODE_INVALID_DATE_FORMAT,
                    tag,
                    String.format("Value date in :32A: must be 6 numeric digits (YYMMDD), but was '%s'", datePart)
            ));
            return;
        }

        try {
            LocalDate.parse(datePart, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            findings.add(ValidationFinding.error(
                    CODE_INVALID_DATE_FORMAT,
                    tag,
                    String.format("Value date '%s' in :32A: is not a valid calendar date (YYMMDD)", datePart)
            ));
        }
    }

    private void validateCurrencyPart(String currencyPart, String tag, List<ValidationFinding> findings) {
        if (!currencyPart.matches("^[A-Z]{3}$")) {
            findings.add(ValidationFinding.error(
                    CODE_INVALID_CURRENCY_CODE,
                    tag,
                    String.format("Currency code in :32A: must be 3 uppercase letters, but was '%s'", currencyPart)
            ));
            return;
        }

        if (!VALID_ISO_CURRENCIES.contains(currencyPart)) {
            findings.add(ValidationFinding.error(
                    CODE_INVALID_CURRENCY_CODE,
                    tag,
                    String.format("Currency code '%s' in :32A: is not a valid ISO 4217 currency", currencyPart)
            ));
        }
    }

    private void validateAmountPart(String amountPart, String tag, List<ValidationFinding> findings) {
        if (amountPart.isEmpty()) {
            findings.add(ValidationFinding.error(
                    CODE_INVALID_AMOUNT_FORMAT,
                    tag,
                    "Amount in :32A: is missing after currency code"
            ));
            return;
        }

        // SWIFT FIN mandates comma as decimal separator, rejecting period '.'
        if (amountPart.contains(".")) {
            findings.add(ValidationFinding.error(
                    CODE_INVALID_AMOUNT_SEPARATOR,
                    tag,
                    String.format("Amount '%s' in :32A: uses '.' decimal separator; SWIFT FIN requires comma ','", amountPart)
            ));
            return;
        }

        // SWIFT FIN amount pattern: digits optionally followed by comma and digits (e.g. 100, or 100,50)
        if (!amountPart.matches("^\\d+(?:,\\d*)?$")) {
            findings.add(ValidationFinding.error(
                    CODE_INVALID_AMOUNT_FORMAT,
                    tag,
                    String.format("Amount '%s' in :32A: contains invalid non-numeric characters", amountPart)
            ));
            return;
        }

        // Total digits count (excluding the comma separator) must not exceed 15
        String digitsOnly = amountPart.replace(",", "");
        if (digitsOnly.length() > 15) {
            findings.add(ValidationFinding.error(
                    CODE_INVALID_AMOUNT_FORMAT,
                    tag,
                    String.format("Amount '%s' has %d digits, exceeding the SWIFT maximum of 15 digits", amountPart, digitsOnly.length())
            ));
            return;
        }

        // Validate strictly positive amount
        try {
            String javaAmount = amountPart.endsWith(",") ? amountPart.substring(0, amountPart.length() - 1) : amountPart.replace(',', '.');
            BigDecimal parsedAmount = new BigDecimal(javaAmount);
            if (parsedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                findings.add(ValidationFinding.error(
                        CODE_INVALID_AMOUNT_FORMAT,
                        tag,
                        String.format("Amount in :32A: must be strictly positive (> 0), but was '%s'", amountPart)
                ));
            }
        } catch (NumberFormatException e) {
            findings.add(ValidationFinding.error(
                    CODE_INVALID_AMOUNT_FORMAT,
                    tag,
                    String.format("Failed to parse numeric amount '%s' in :32A:", amountPart)
            ));
        }
    }

    private void checkTag71A(MT103Message message, List<ValidationFinding> findings) {
        Optional<RawTag> tag71AOpt = message.tag71A();
        if (tag71AOpt.isEmpty()) {
            return; // Handled by checkMandatoryTags
        }

        String value = tag71AOpt.get().value().trim();
        String tag = ":71A:";

        if (!VALID_CHARGE_CODES.contains(value)) {
            findings.add(ValidationFinding.error(
                    CODE_INVALID_CHARGE_CODE,
                    tag,
                    String.format("Details of charges in :71A: must be one of OUR, BEN, or SHA, but was '%s'", value)
            ));
        }
    }

    private void checkTag23B(MT103Message message, List<ValidationFinding> findings) {
        Optional<RawTag> tag23BOpt = message.tag23B();
        if (tag23BOpt.isEmpty()) {
            return;
        }

        String value = tag23BOpt.get().value().trim();
        if (!"CRED".equals(value)) {
            findings.add(ValidationFinding.warning(
                    CODE_NON_STANDARD_OPERATION_CODE,
                    ":23B:",
                    String.format("Bank operation code in :23B: is '%s'; standard customer transfer is 'CRED'", value)
            ));
        }
    }

    private void checkLineLimits(MT103Message message, List<ValidationFinding> findings) {
        for (RawTag rawTag : message.allTags()) {
            String code = rawTag.tagCode().toUpperCase(Locale.ROOT);
            TagLineLimit limit = lineLimits.get(code);
            if (limit == null) {
                continue;
            }

            String fullTag = rawTag.tag();
            List<String> lines = rawTag.lines();

            // Check line count
            if (lines.size() > limit.maxLines()) {
                findings.add(ValidationFinding.error(
                        CODE_LINE_COUNT_EXCEEDED,
                        fullTag,
                        String.format("Field %s line count (%d) exceeds maximum allowed limit of %d lines",
                                fullTag, lines.size(), limit.maxLines())
                ));
            }

            // Check line lengths
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.length() > limit.maxLineLength()) {
                    findings.add(ValidationFinding.error(
                            CODE_LINE_LENGTH_EXCEEDED,
                            fullTag,
                            String.format("Field %s line %d length (%d) exceeds maximum allowed length of %d characters: '%s'",
                                    fullTag, i + 1, line.length(), limit.maxLineLength(), line)
                    ));
                }
            }
        }
    }
}

