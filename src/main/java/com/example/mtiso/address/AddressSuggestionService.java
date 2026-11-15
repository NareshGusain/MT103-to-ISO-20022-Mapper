package com.example.mtiso.address;

import java.util.List;

/**
 * Service providing address enrichment suggestions by extracting structured components
 * (street, town, post code, country) with confidence scores from raw free-text address lines.
 */
public interface AddressSuggestionService {

    /**
     * Extracts and suggests structured address components from free-text lines.
     *
     * @param addressLines list of raw address lines
     * @return suggested structured address with confidence scores (0.0 to 1.0)
     */
    AddressSuggestion suggestAddress(List<String> addressLines);

    /**
     * Identifier for the underlying engine (e.g. "RULE_BASED", "LLM").
     */
    String getProviderName();
}

