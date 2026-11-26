/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.common.exception.PermanentModelError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Verifies structured model failures survive the real DeepAgent result boundary. */
class DeepAgentStructuredFailurePropagationTest {
    @TempDir
    private Path tempDir;

    private DeepAgent agent;

    @AfterEach
    void shutdownAgent() {
        if (agent != null) {
            agent.shutdown();
        }
    }

    @Test
    void contextLengthFailureReachesRoundFinalResultAndTopLevel() {
        agent = HarnessFactory.createDeepAgent(AgentCard.builder()
                .id("structured-failure-" + UUID.randomUUID().toString().replace("-", ""))
                .name("structured-failure")
                .description("structured model failure propagation")
                .build(), DeepAgentConfig.builder()
                .workspacePath(tempDir.toString())
                .enableTaskLoop(true)
                .enableTaskPlanning(false)
                .maxIterations(1)
                .build(), null);
        Model model = mock(Model.class);
        when(model.stream(any(List.class), any(ModelInvokeOptions.class))).thenThrow(new PermanentModelError(
                StatusCode.MODEL_CONTEXT_LENGTH_EXCEEDED));
        agent.getAgent().setLlm(model);

        String sessionId = "structured-failure-" + UUID.randomUUID();
        DeepAgentSession session = new DeepAgentSession(sessionId, null, agent.getCard());
        List<Object> chunks = new java.util.ArrayList<>();
        agent.stream(Map.of("query", "oversized input", "conversation_id", sessionId),
                session, List.of(StreamMode.OUTPUT)).forEachRemaining(chunks::add);

        Map<String, Object> result = topLevelResult(chunks);
        Map<String, Object> expected = Map.of(
                "code", "MODEL_CONTEXT_LENGTH_EXCEEDED",
                "numericCode", 181012,
                "retryable", false);
        List<Map<String, Object>> rounds = roundsOf(result);
        assertThat(rounds).hasSize(1);
        assertThat(result.get("failure")).isEqualTo(expected);
        assertThat(result.get("final_result")).isEqualTo(rounds.get(0));
        assertThat(rounds.get(0).get("failure")).isEqualTo(expected);
    }

    private static Map<String, Object> topLevelResult(List<Object> chunks) {
        for (Object chunk : chunks) {
            if (!(chunk instanceof OutputSchema schema) || !(schema.getPayload() instanceof Map<?, ?> payload)) {
                continue;
            }
            if (payload.get("output") instanceof Map<?, ?> output
                    && "deep_agent_result".equals(output.get("type"))) {
                return stringObjectMap(output);
            }
        }
        throw new AssertionError("No top-level DeepAgent result");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> roundsOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("rounds");
    }
}
