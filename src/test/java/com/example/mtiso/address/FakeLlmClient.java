package com.example.mtiso.address;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory test double for LlmClient.
 * Operates strictly offline with ZERO network calls.
 */
public class FakeLlmClient implements LlmClient {

    private String cannedResponse;
    private final List<String> recordedPrompts = new ArrayList<>();
    private int callCount = 0;

    public FakeLlmClient() {
        this("""
                {
                  "street": "100 MAIN STREET",
                  "streetConfidence": 0.95,
                  "town": "LONDON",
                  "townConfidence": 0.90,
                  "postCode": "EC2N 2DB",
                  "postCodeConfidence": 0.95,
                  "country": "GB",
                  "countryConfidence": 0.95
                }
                """);
    }

    public FakeLlmClient(String cannedResponse) {
        this.cannedResponse = cannedResponse;
    }

    public void setCannedResponse(String cannedResponse) {
        this.cannedResponse = cannedResponse;
    }

    @Override
    public String completePrompt(String prompt) {
        this.recordedPrompts.add(prompt);
        this.callCount++;
        return cannedResponse;
    }

    public List<String> getRecordedPrompts() {
        return Collections.unmodifiableList(recordedPrompts);
    }

    public int getCallCount() {
        return callCount;
    }

    public void reset() {
        this.recordedPrompts.clear();
        this.callCount = 0;
    }
}

