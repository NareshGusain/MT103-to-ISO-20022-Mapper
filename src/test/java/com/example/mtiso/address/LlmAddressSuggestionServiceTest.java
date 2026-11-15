package com.example.mtiso.address;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmAddressSuggestionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDisabledFlagPreventsExecutionAndNeverCallsClient() {
        FakeLlmClient fakeClient = new FakeLlmClient();
        // Flag explicitly set to false
        LlmAddressSuggestionService service = new LlmAddressSuggestionService(false, fakeClient, objectMapper);

        assertThat(service.isEnabled()).isFalse();

        assertThatThrownBy(() -> service.suggestAddress(List.of("100 MAIN STREET", "LONDON EC2N 2DB, UK")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM-backed address suggestion is disabled");

        // Verify zero invocations of the client
        assertThat(fakeClient.getCallCount()).isZero();
    }

    @Test
    void testEnabledFlagWithFakeClientParsesStructuredFields() {
        FakeLlmClient fakeClient = new FakeLlmClient("""
                {
                  "street": "100 MAIN STREET",
                  "streetConfidence": 0.98,
                  "town": "LONDON",
                  "townConfidence": 0.92,
                  "postCode": "EC2N 2DB",
                  "postCodeConfidence": 0.99,
                  "country": "GB",
                  "countryConfidence": 0.97
                }
                """);

        LlmAddressSuggestionService service = new LlmAddressSuggestionService(true, fakeClient, objectMapper);

        assertThat(service.isEnabled()).isTrue();

        AddressSuggestion suggestion = service.suggestAddress(List.of("100 MAIN STREET", "LONDON EC2N 2DB, UK"));

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.provider()).isEqualTo("LLM");
        assertThat(suggestion.street().value()).isEqualTo("100 MAIN STREET");
        assertThat(suggestion.street().confidence()).isEqualTo(0.98);
        assertThat(suggestion.town().value()).isEqualTo("LONDON");
        assertThat(suggestion.town().confidence()).isEqualTo(0.92);
        assertThat(suggestion.postCode().value()).isEqualTo("EC2N 2DB");
        assertThat(suggestion.postCode().confidence()).isEqualTo(0.99);
        assertThat(suggestion.country().value()).isEqualTo("GB");
        assertThat(suggestion.country().confidence()).isEqualTo(0.97);

        // Verify client was called once with formatted prompt
        assertThat(fakeClient.getCallCount()).isEqualTo(1);
        assertThat(fakeClient.getRecordedPrompts().get(0)).contains("LONDON EC2N 2DB, UK");
    }

    @Test
    void testParsesMarkdownWrappedJsonResponse() {
        FakeLlmClient fakeClient = new FakeLlmClient("""
                ```json
                {
                  "street": "INDUSTRIESTRASSE 15",
                  "streetConfidence": 0.90,
                  "town": "FRANKFURT",
                  "townConfidence": 0.88,
                  "postCode": "60329",
                  "postCodeConfidence": 0.94,
                  "country": "DE",
                  "countryConfidence": 0.96
                }
                ```
                """);

        LlmAddressSuggestionService service = new LlmAddressSuggestionService(true, fakeClient, objectMapper);

        AddressSuggestion suggestion = service.suggestAddress(List.of("INDUSTRIESTRASSE 15", "60329 FRANKFURT, GERMANY"));

        assertThat(suggestion.street().value()).isEqualTo("INDUSTRIESTRASSE 15");
        assertThat(suggestion.town().value()).isEqualTo("FRANKFURT");
        assertThat(suggestion.postCode().value()).isEqualTo("60329");
        assertThat(suggestion.country().value()).isEqualTo("DE");
    }
}

