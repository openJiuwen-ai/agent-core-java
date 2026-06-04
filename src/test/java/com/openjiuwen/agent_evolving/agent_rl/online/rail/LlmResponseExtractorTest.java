/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmResponseExtractorTest {

    static class MetadataResponse {
        final Map<String, Object> metadata;

        MetadataResponse(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    @Test
    void extractsTokenIdsFromChoiceThenResponsePayload() {
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of("completion_token_ids", List.of("11", 12.9, "bad"))),
                "prompt_ids", Arrays.asList("1", 2, null, "x"));

        assertEquals(List.of(11, 12), LlmResponseExtractor.extractTokenIds(response));
        assertEquals(List.of(1, 2), LlmResponseExtractor.extractPromptIds(response));
    }

    @Test
    void extractsLogprobsFromDirectListAndContentItems() {
        assertEquals(
                List.of(-0.1, -0.2),
                LlmResponseExtractor.extractLogprobs(Map.of("logprobs", List.of("-0.1", -0.2, "bad"))));

        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of(
                        "logprobs", Map.of("content", List.of(
                                Map.of("logprob", "-1.5"),
                                Map.of("logprob", -2.0),
                                Map.of("other", "skip"))))));
        assertEquals(List.of(-1.5, -2.0), LlmResponseExtractor.extractLogprobs(response));
    }

    @Test
    void readsMetadataAttributeLikePythonGetattr() {
        MetadataResponse response = new MetadataResponse(Map.of(
                "choices", List.of(Map.of(
                        "token_ids", List.of("7", "8"),
                        "logprobs", List.of("-0.7", "-0.8")))));

        assertEquals(List.of(7, 8), LlmResponseExtractor.extractTokenIds(response));
        assertEquals(List.of(-0.7, -0.8), LlmResponseExtractor.extractLogprobs(response));
    }

    @Test
    void nullReturnsMatchPythonNoneBranches() {
        assertNull(LlmResponseExtractor.extractTokenIds(null));
        assertNull(LlmResponseExtractor.extractPromptIds(Map.of("choices", List.of())));
        assertNull(LlmResponseExtractor.extractLogprobs(Map.of("choices", List.of(Map.of()))));
        assertNull(LlmResponseExtractor.extractTokenIds(Map.of("token_ids", List.of("bad"))));
    }
}
