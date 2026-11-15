package com.example.mtiso.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * LLM-backed implementation of AddressSuggestionService.
 * Strictly gated behind the 'mtiso.address.llm.enabled' configuration property.
 * Emits mandatory security warnings that only synthetic test data may be processed.
 */
@Service
public class LlmAddressSuggestionService implements AddressSuggestionService {

    public static final String PROVIDER_NAME = "LLM";
    private static final Logger log = LoggerFactory.getLogger(LlmAddressSuggestionService.class);

    private final boolean enabled;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public LlmAddressSuggestionService(
            @Value("${mtiso.address.llm.enabled:false}") boolean enabled,
            @Autowired(required = false) LlmClient llmClient,
            ObjectMapper objectMapper
    ) {
        this.enabled = enabled;
        this.llmClient = llmClient;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public AddressSuggestion suggestAddress(List<String> addressLines) {
        // Enforce configuration gate
        if (!enabled) {
            throw new IllegalStateException(
                    "LLM-backed address suggestion is disabled. "
                            + "Set configuration flag 'mtiso.address.llm.enabled=true' to enable external LLM suggestions."
            );
        }

        // CRITICAL DATA GOVERNANCE WARNING
        log.warn("WARNING: External LLM address suggestion is enabled. "
                + "In compliance with banking security policy, ONLY SYNTHETIC DATA may be used. "
                + "NEVER submit real customer names, addresses, or PII.");

        if (llmClient == null) {
            throw new IllegalStateException("LlmClient is not configured. Provide an LlmClient implementation bean.");
        }

        if (addressLines == null || addressLines.isEmpty()) {
            return new AddressSuggestion(
                    AddressField.empty(),
                    AddressField.empty(),
                    AddressField.empty(),
                    AddressField.empty(),
                    PROVIDER_NAME
            );
        }

        String prompt = buildPrompt(addressLines);
        String rawResponse = llmClient.completePrompt(prompt);
        return parseResponse(rawResponse);
    }

    private String buildPrompt(List<String> addressLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert banking assistant parsing unstructured postal addresses from SWIFT MT103 messages into ISO 20022 format.\n");
        sb.append("Extract: street (with number), town, postal code (postCode), and 2-letter ISO country code (country).\n");
        sb.append("Assign a confidence score from 0.0 to 1.0 for each field.\n");
        sb.append("Respond ONLY with valid JSON in the following format:\n");
        sb.append("{\n");
        sb.append("  \"street\": \"...\",\n");
        sb.append("  \"streetConfidence\": 0.95,\n");
        sb.append("  \"town\": \"...\",\n");
        sb.append("  \"townConfidence\": 0.90,\n");
        sb.append("  \"postCode\": \"...\",\n");
        sb.append("  \"postCodeConfidence\": 0.95,\n");
        sb.append("  \"country\": \"...\",\n");
        sb.append("  \"countryConfidence\": 0.95\n");
        sb.append("}\n\n");
        sb.append("Input address lines:\n");
        for (String line : addressLines) {
            sb.append("- ").append(line).append("\n");
        }
        return sb.toString();
    }

    private AddressSuggestion parseResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return new AddressSuggestion(
                    AddressField.empty(),
                    AddressField.empty(),
                    AddressField.empty(),
                    AddressField.empty(),
                    PROVIDER_NAME
            );
        }

        try {
            // Strip potential markdown code block formatting (e.g. ```json ... ```)
            String jsonText = rawResponse.trim();
            if (jsonText.startsWith("```")) {
                int firstNewline = jsonText.indexOf('\n');
                int lastBackticks = jsonText.lastIndexOf("```");
                if (firstNewline != -1 && lastBackticks > firstNewline) {
                    jsonText = jsonText.substring(firstNewline + 1, lastBackticks).trim();
                }
            }

            JsonNode node = objectMapper.readTree(jsonText);

            String street = extractText(node, "street");
            double streetConf = extractDouble(node, "streetConfidence");

            String town = extractText(node, "town");
            double townConf = extractDouble(node, "townConfidence");

            String postCode = extractText(node, "postCode");
            double postCodeConf = extractDouble(node, "postCodeConfidence");

            String country = extractText(node, "country");
            double countryConf = extractDouble(node, "countryConfidence");

            return new AddressSuggestion(
                    new AddressField(street, streetConf),
                    new AddressField(town, townConf),
                    new AddressField(postCode, postCodeConf),
                    new AddressField(country, countryConf),
                    PROVIDER_NAME
            );
        } catch (Exception e) {
            log.error("Failed to parse JSON response from LLM client: {}", rawResponse, e);
            return new AddressSuggestion(
                    AddressField.empty(),
                    AddressField.empty(),
                    AddressField.empty(),
                    AddressField.empty(),
                    PROVIDER_NAME
            );
        }
    }

    private String extractText(JsonNode node, String fieldName) {
        if (node.hasNonNull(fieldName)) {
            String text = node.get(fieldName).asText().trim();
            return text.isBlank() ? null : text;
        }
        return null;
    }

    private double extractDouble(JsonNode node, String fieldName) {
        if (node.hasNonNull(fieldName)) {
            return Math.clamp(node.get(fieldName).asDouble(), 0.0, 1.0);
        }
        return 0.0;
    }
}

