package com.example.mtiso.address;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic rule-based implementation of AddressSuggestionService.
 * Uses regular expressions and banking heuristics to parse unstructured address lines
 * into street, town, postal code, and ISO country code.
 */
@Service
@Primary
public class RuleBasedAddressSuggestionService implements AddressSuggestionService {

    public static final String PROVIDER_NAME = "RULE_BASED";

    // Regular expressions for postal codes
    private static final Pattern UK_POSTCODE_PATTERN = Pattern.compile(
            "\\b([A-Z]{1,2}[0-9][A-Z0-9]?\\s*[0-9][A-Z]{2})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIXED_POSTCODE_PATTERN = Pattern.compile(
            "\\b([A-Z]{1,2}-[0-9]{4,5})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGIT_5_POSTCODE_PATTERN = Pattern.compile(
            "\\b([0-9]{5}(?:-[0-9]{4})?)\\b");
    private static final Pattern DIGIT_4_POSTCODE_PATTERN = Pattern.compile(
            "\\b([0-9]{4})\\b");

    // Street designator keywords
    private static final List<String> STREET_KEYWORDS = List.of(
            "STREET", "STRASSE", "STR.", "STR", "RUE", "AVENUE", "AVE",
            "BOULEVARD", "BLVD", "ROAD", "RD", "WAY", "LANE", "LN",
            "DRIVE", "DR", "COURT", "CT", "PLATZ", "WEG", "GASSE", "SQUARE", "SQ"
    );

    // Country normalization mapping to ISO 3166-1 alpha-2
    private static final Map<String, String> COUNTRY_NAME_TO_CODE = new HashMap<>();

    static {
        COUNTRY_NAME_TO_CODE.put("UK", "GB");
        COUNTRY_NAME_TO_CODE.put("UNITED KINGDOM", "GB");
        COUNTRY_NAME_TO_CODE.put("GREAT BRITAIN", "GB");
        COUNTRY_NAME_TO_CODE.put("ENGLAND", "GB");
        COUNTRY_NAME_TO_CODE.put("GB", "GB");

        COUNTRY_NAME_TO_CODE.put("GERMANY", "DE");
        COUNTRY_NAME_TO_CODE.put("DEUTSCHLAND", "DE");
        COUNTRY_NAME_TO_CODE.put("DE", "DE");

        COUNTRY_NAME_TO_CODE.put("BELGIUM", "BE");
        COUNTRY_NAME_TO_CODE.put("BELGIQUE", "BE");
        COUNTRY_NAME_TO_CODE.put("BELGIE", "BE");
        COUNTRY_NAME_TO_CODE.put("BE", "BE");

        COUNTRY_NAME_TO_CODE.put("FRANCE", "FR");
        COUNTRY_NAME_TO_CODE.put("FR", "FR");

        COUNTRY_NAME_TO_CODE.put("UNITED STATES", "US");
        COUNTRY_NAME_TO_CODE.put("USA", "US");
        COUNTRY_NAME_TO_CODE.put("US", "US");

        COUNTRY_NAME_TO_CODE.put("NETHERLANDS", "NL");
        COUNTRY_NAME_TO_CODE.put("HOLLAND", "NL");
        COUNTRY_NAME_TO_CODE.put("NL", "NL");

        COUNTRY_NAME_TO_CODE.put("SWITZERLAND", "CH");
        COUNTRY_NAME_TO_CODE.put("SCHWEIZ", "CH");
        COUNTRY_NAME_TO_CODE.put("SUISSE", "CH");
        COUNTRY_NAME_TO_CODE.put("CH", "CH");

        COUNTRY_NAME_TO_CODE.put("SPAIN", "ES");
        COUNTRY_NAME_TO_CODE.put("ESPAÑA", "ES");
        COUNTRY_NAME_TO_CODE.put("ES", "ES");

        COUNTRY_NAME_TO_CODE.put("ITALY", "IT");
        COUNTRY_NAME_TO_CODE.put("ITALIA", "IT");
        COUNTRY_NAME_TO_CODE.put("IT", "IT");

        COUNTRY_NAME_TO_CODE.put("CANADA", "CA");
        COUNTRY_NAME_TO_CODE.put("CA", "CA");

        COUNTRY_NAME_TO_CODE.put("AUSTRALIA", "AU");
        COUNTRY_NAME_TO_CODE.put("AU", "AU");

        COUNTRY_NAME_TO_CODE.put("SINGAPORE", "SG");
        COUNTRY_NAME_TO_CODE.put("SG", "SG");

        COUNTRY_NAME_TO_CODE.put("JAPAN", "JP");
        COUNTRY_NAME_TO_CODE.put("JP", "JP");

        COUNTRY_NAME_TO_CODE.put("IRELAND", "IE");
        COUNTRY_NAME_TO_CODE.put("IE", "IE");

        COUNTRY_NAME_TO_CODE.put("AUSTRIA", "AT");
        COUNTRY_NAME_TO_CODE.put("ÖSTERREICH", "AT");
        COUNTRY_NAME_TO_CODE.put("AT", "AT");

        COUNTRY_NAME_TO_CODE.put("SWEDEN", "SE");
        COUNTRY_NAME_TO_CODE.put("SVERIGE", "SE");
        COUNTRY_NAME_TO_CODE.put("SE", "SE");

        COUNTRY_NAME_TO_CODE.put("NORWAY", "NO");
        COUNTRY_NAME_TO_CODE.put("NORGE", "NO");
        COUNTRY_NAME_TO_CODE.put("NO", "NO");

        COUNTRY_NAME_TO_CODE.put("DENMARK", "DK");
        COUNTRY_NAME_TO_CODE.put("DANMARK", "DK");
        COUNTRY_NAME_TO_CODE.put("DK", "DK");

        COUNTRY_NAME_TO_CODE.put("FINLAND", "FI");
        COUNTRY_NAME_TO_CODE.put("SUOMI", "FI");
        COUNTRY_NAME_TO_CODE.put("FI", "FI");

        COUNTRY_NAME_TO_CODE.put("POLAND", "PL");
        COUNTRY_NAME_TO_CODE.put("POLSKA", "PL");
        COUNTRY_NAME_TO_CODE.put("PL", "PL");
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public AddressSuggestion suggestAddress(List<String> addressLines) {
        if (addressLines == null || addressLines.isEmpty()) {
            return new AddressSuggestion(
                    AddressField.empty(),
                    AddressField.empty(),
                    AddressField.empty(),
                    AddressField.empty(),
                    PROVIDER_NAME
            );
        }

        List<String> cleanLines = addressLines.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        if (cleanLines.isEmpty()) {
            return new AddressSuggestion(
                    AddressField.empty(),
                    AddressField.empty(),
                    AddressField.empty(),
                    AddressField.empty(),
                    PROVIDER_NAME
            );
        }

        String street = null;
        double streetConf = 0.0;

        String postCode = null;
        double postCodeConf = 0.0;

        String country = null;
        double countryConf = 0.0;

        String town = null;
        double townConf = 0.0;

        // 1. Identify Street Line
        int streetIndex = -1;
        for (int i = 0; i < cleanLines.size(); i++) {
            String line = cleanLines.get(i);
            if (isStreetLine(line)) {
                street = line;
                streetConf = 0.90;
                streetIndex = i;
                break;
            }
        }

        // If no keyword match, check line 0 if there are multiple lines
        if (street == null && cleanLines.size() >= 2) {
            String firstLine = cleanLines.get(0);
            if (firstLine.matches(".*\\d+.*")) {
                street = firstLine;
                streetConf = 0.75;
                streetIndex = 0;
            }
        }

        // 2. Scan remaining lines for Post Code, Country, and Town
        for (int i = 0; i < cleanLines.size(); i++) {
            if (i == streetIndex) {
                continue;
            }
            String line = cleanLines.get(i);
            String working = line;

            // Extract Country
            if (country == null) {
                ExtractedCountry extCountry = extractCountry(working);
                if (extCountry != null) {
                    country = extCountry.code;
                    countryConf = extCountry.confidence;
                    working = extCountry.remainingText;
                }
            }

            // Extract Post Code
            if (postCode == null) {
                ExtractedToken extPostCode = extractPostCode(working);
                if (extPostCode != null) {
                    postCode = extPostCode.token;
                    postCodeConf = extPostCode.confidence;
                    working = extPostCode.remainingText;
                }
            }

            // Clean working text for Town
            String cleanedTown = cleanTownCandidate(working);
            if (!cleanedTown.isBlank() && town == null) {
                town = cleanedTown;
                townConf = 0.85;
            }
        }

        // Fallback: If street still not found and we have an unused line
        if (street == null && cleanLines.size() == 1) {
            // single line fallback: if we found town/postcode/country, what remains could be street
            street = cleanLines.get(0);
            streetConf = 0.50;
        }

        return new AddressSuggestion(
                new AddressField(street, streetConf),
                new AddressField(town, townConf),
                new AddressField(postCode, postCodeConf),
                new AddressField(country, countryConf),
                PROVIDER_NAME
        );
    }

    private boolean isStreetLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        for (String keyword : STREET_KEYWORDS) {
            if (upper.matches(".*\\b" + Pattern.quote(keyword) + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    private ExtractedCountry extractCountry(String text) {
        // First check trailing country name/code separated by comma or space
        String upper = text.toUpperCase(Locale.ROOT);

        // Sort by descending key length to match multi-word countries first (e.g. "UNITED KINGDOM" before "UK")
        List<Map.Entry<String, String>> entries = new ArrayList<>(COUNTRY_NAME_TO_CODE.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        for (Map.Entry<String, String> entry : entries) {
            String countryName = entry.getKey();
            Pattern p = Pattern.compile("(?i)(?:,\\s*|\\s+)\\b" + Pattern.quote(countryName) + "\\b\\s*$");
            Matcher m = p.matcher(text);
            if (m.find()) {
                String remaining = text.substring(0, m.start()).trim();
                double conf = countryName.length() == 2 ? 0.95 : 0.90;
                return new ExtractedCountry(entry.getValue(), conf, remaining);
            }
        }

        // Also check if entire text is a country name
        String trimmedUpper = upper.trim();
        if (COUNTRY_NAME_TO_CODE.containsKey(trimmedUpper)) {
            return new ExtractedCountry(COUNTRY_NAME_TO_CODE.get(trimmedUpper), 0.95, "");
        }

        return null;
    }

    private ExtractedToken extractPostCode(String text) {
        // Try UK postcode first
        Matcher ukMatcher = UK_POSTCODE_PATTERN.matcher(text);
        if (ukMatcher.find()) {
            String token = ukMatcher.group(1).trim().toUpperCase(Locale.ROOT);
            String remaining = (text.substring(0, ukMatcher.start()) + " " + text.substring(ukMatcher.end())).trim();
            return new ExtractedToken(token, 0.95, remaining);
        }

        // Try Prefixed postcode (e.g. D-60329)
        Matcher prefMatcher = PREFIXED_POSTCODE_PATTERN.matcher(text);
        if (prefMatcher.find()) {
            String token = prefMatcher.group(1).trim().toUpperCase(Locale.ROOT);
            String remaining = (text.substring(0, prefMatcher.start()) + " " + text.substring(prefMatcher.end())).trim();
            return new ExtractedToken(token, 0.90, remaining);
        }

        // Try 5-digit ZIP
        Matcher zip5Matcher = DIGIT_5_POSTCODE_PATTERN.matcher(text);
        if (zip5Matcher.find()) {
            String token = zip5Matcher.group(1).trim();
            String remaining = (text.substring(0, zip5Matcher.start()) + " " + text.substring(zip5Matcher.end())).trim();
            return new ExtractedToken(token, 0.90, remaining);
        }

        // Try 4-digit code
        Matcher zip4Matcher = DIGIT_4_POSTCODE_PATTERN.matcher(text);
        if (zip4Matcher.find()) {
            String token = zip4Matcher.group(1).trim();
            String remaining = (text.substring(0, zip4Matcher.start()) + " " + text.substring(zip4Matcher.end())).trim();
            return new ExtractedToken(token, 0.85, remaining);
        }

        return null;
    }

    private String cleanTownCandidate(String text) {
        if (text == null) return "";
        // Remove trailing commas, hyphens, and whitespace
        String cleaned = text.replaceAll("^[\\s,.-]+|[\\s,.-]+$", "").trim();
        // Remove duplicate internal whitespace
        cleaned = cleaned.replaceAll("\\s{2,}", " ");
        return cleaned;
    }

    private record ExtractedCountry(String code, double confidence, String remainingText) {}
    private record ExtractedToken(String token, double confidence, String remainingText) {}
}

