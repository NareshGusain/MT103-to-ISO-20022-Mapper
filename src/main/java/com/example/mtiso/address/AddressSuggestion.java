package com.example.mtiso.address;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Aggregation of suggested structured address fields (street, town, post code, country)
 * produced by an AddressSuggestionService implementation.
 */
@Schema(description = "Suggested structured address breakdown containing town, country, post code, and street")
public record AddressSuggestion(
        @Schema(description = "Suggested street name and building number")
        AddressField street,

        @Schema(description = "Suggested town / city name")
        AddressField town,

        @Schema(description = "Suggested postal code / ZIP")
        AddressField postCode,

        @Schema(description = "Suggested 2-letter ISO country code")
        AddressField country,

        @Schema(description = "Provider that generated this suggestion (e.g. RULE_BASED, LLM)", example = "RULE_BASED")
        String provider
) {
    public AddressSuggestion {
        street = street != null ? street : AddressField.empty();
        town = town != null ? town : AddressField.empty();
        postCode = postCode != null ? postCode : AddressField.empty();
        country = country != null ? country : AddressField.empty();
        provider = provider != null ? provider : "RULE_BASED";
    }

    public static AddressSuggestion of(
            AddressField street,
            AddressField town,
            AddressField postCode,
            AddressField country,
            String provider
    ) {
        return new AddressSuggestion(street, town, postCode, country, provider);
    }

    public String getStreetValue() {
        return street.value();
    }

    public String getTownValue() {
        return town.value();
    }

    public String getPostCodeValue() {
        return postCode.value();
    }

    public String getCountryValue() {
        return country.value();
    }
}

