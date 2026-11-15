package com.example.mtiso.address;

/**
 * Interface abstraction for external or mocked LLM completion providers.
 */
public interface LlmClient {

    /**
     * Executes prompt completion against an LLM endpoint.
     *
     * @param prompt formatted instruction prompt
     * @return raw response string (typically JSON)
     */
    String completePrompt(String prompt);
}

