package com.example.mtiso.address;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedAddressSuggestionServiceTest {

    private RuleBasedAddressSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new RuleBasedAddressSuggestionService();
    }

    @Test
    void testParseSample06UkAddress() {
        List<String> lines = List.of(
                "100 MAIN STREET",
                "LONDON EC2N 2DB, UK"
        );

        AddressSuggestion suggestion = service.suggestAddress(lines);

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.provider()).isEqualTo("RULE_BASED");

        assertThat(suggestion.street().value()).isEqualTo("100 MAIN STREET");
        assertThat(suggestion.street().confidence()).isGreaterThanOrEqualTo(0.7);

        assertThat(suggestion.town().value()).isEqualTo("LONDON");
        assertThat(suggestion.town().confidence()).isGreaterThanOrEqualTo(0.7);

        assertThat(suggestion.postCode().value()).isEqualTo("EC2N 2DB");
        assertThat(suggestion.postCode().confidence()).isGreaterThanOrEqualTo(0.9);

        assertThat(suggestion.country().value()).isEqualTo("GB");
        assertThat(suggestion.country().confidence()).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    void testParseSample07GermanAddress() {
        List<String> lines = List.of(
                "INDUSTRIESTRASSE 15",
                "60329 FRANKFURT, GERMANY"
        );

        AddressSuggestion suggestion = service.suggestAddress(lines);

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.street().value()).isEqualTo("INDUSTRIESTRASSE 15");
        assertThat(suggestion.town().value()).isEqualTo("FRANKFURT");
        assertThat(suggestion.postCode().value()).isEqualTo("60329");
        assertThat(suggestion.country().value()).isEqualTo("DE");
    }

    @Test
    void testParseBelgianAddress() {
        List<String> lines = List.of(
                "AVENUE DES ARTS 40",
                "1000 BRUSSELS, BELGIUM"
        );

        AddressSuggestion suggestion = service.suggestAddress(lines);

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.street().value()).isEqualTo("AVENUE DES ARTS 40");
        assertThat(suggestion.town().value()).isEqualTo("BRUSSELS");
        assertThat(suggestion.postCode().value()).isEqualTo("1000");
        assertThat(suggestion.country().value()).isEqualTo("BE");
    }

    @Test
    void testParseFrenchAddress() {
        List<String> lines = List.of(
                "12 RUE DE LA PAIX",
                "75002 PARIS, FRANCE"
        );

        AddressSuggestion suggestion = service.suggestAddress(lines);

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.street().value()).isEqualTo("12 RUE DE LA PAIX");
        assertThat(suggestion.town().value()).isEqualTo("PARIS");
        assertThat(suggestion.postCode().value()).isEqualTo("75002");
        assertThat(suggestion.country().value()).isEqualTo("FR");
    }

    @Test
    void testEmptyAndNullHandling() {
        AddressSuggestion emptySuggestion = service.suggestAddress(Collections.emptyList());
        assertThat(emptySuggestion.street().value()).isNull();
        assertThat(emptySuggestion.town().value()).isNull();
        assertThat(emptySuggestion.postCode().value()).isNull();
        assertThat(emptySuggestion.country().value()).isNull();

        AddressSuggestion nullSuggestion = service.suggestAddress(null);
        assertThat(nullSuggestion.street().value()).isNull();
    }
}

