package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmResponseUtilsTest {

    @Test
    void extractsDirectChoiceLogprobs() {
        Map<String, Object> response = Map.of(
                "choices",
                List.of(Map.of("logprobs", List.of("1.5", 2, "ignored"))));

        assertEquals(List.of(1.5d, 2.0d), LlmResponseUtils.extractLogprobs(response));
    }

    @Test
    void extractsStructuredLogprobsFromMetadataContent() {
        LogprobEnvelope envelope = new LogprobEnvelope(List.of(
                Map.of("logprob", "-0.5"),
                new LogprobItem(-1.25),
                Map.of("logprob", "bad")));
        ProviderResponse response = new ProviderResponse(Map.of(
                "choices",
                List.of(Map.of("logprobs", envelope))));

        assertEquals(List.of(-0.5d, -1.25d), LlmResponseUtils.extractLogprobs(response));
    }

    @Test
    void extractsTokenIdsFromChoiceBeforeTopLevelFallback() {
        Map<String, Object> response = Map.of(
                "choices",
                List.of(Map.of("token_ids", List.of("7", 8, "bad"))),
                "response_tokens",
                List.of(1, 2, 3));

        assertEquals(List.of(7, 8), LlmResponseUtils.extractTokenIds(response));
    }

    @Test
    void extractsPromptIdsFromTopLevelWhenChoiceDoesNotContainThem() {
        ProviderResponse response = new ProviderResponse(Map.of("prompt_ids", List.of(11, "12", "bad")));

        assertEquals(List.of(11, 12), LlmResponseUtils.extractPromptIds(response));
    }

    @Test
    void returnsNullWhenNoSupportedPayloadExists() {
        assertNull(LlmResponseUtils.extractLogprobs(Map.of()));
        assertNull(LlmResponseUtils.extractTokenIds(Map.of("choices", List.of(Map.of()))));
        assertNull(LlmResponseUtils.extractPromptIds(null));
    }

    private record ProviderResponse(Map<String, Object> metadata) {
    }

    private record LogprobEnvelope(List<Object> content) {
    }

    private record LogprobItem(double logprob) {
    }
}
