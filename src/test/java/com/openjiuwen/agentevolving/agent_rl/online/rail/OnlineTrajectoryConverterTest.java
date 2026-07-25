/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.RailBatchIngestor;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python tests for
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/converter.py}.
 */
class OnlineTrajectoryConverterTest {

    @Test
    void readsPromptAndResponseTokenIdsFromProviderResponse() {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("m1")
                .messages(List.of(Map.of("role", "user", "content", "hello")))
                .response(Map.of("role", "assistant", "content", "pong"))
                .meta(Map.of("provider_response_json", Map.of(
                        "prompt_token_ids", List.of(1, 2, 3),
                        "choices", List.of(Map.of(
                                "token_ids", List.of(4, 5),
                                "logprobs", List.of(-0.1d, -0.2d)
                        ))
                )))
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-1")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(TrajectoryStep.builder().kind("llm").detail(detail).build()))
                .build();

        RailV1Batch batch = new OnlineTrajectoryConverter("user-1").convert(trajectory);

        assertEquals(1, batch.getSamples().size());
        assertEquals(List.of(1, 2, 3), batch.getSamples().get(0).getPromptIds());
        assertEquals(List.of(4, 5), batch.getSamples().get(0).getResponseTokens());
    }

    @Test
    void normalizesStreamingLogprobsForGateway() {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("m1")
                .messages(List.of(Map.of("role", "user", "content", "hello")))
                .response(Map.of("role", "assistant", "content", "pong"))
                .build();
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(detail)
                .promptTokenIds(List.of(1, 2, 3))
                .completionTokenIds(List.of(4, 5))
                .logprobs(Map.of("content", List.of(Map.of("logprob", -0.1d), Map.of("logprob", -0.2d))))
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-stream")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(step))
                .build();

        Map<String, Object> batch = new OnlineTrajectoryConverter("user-1").convert(trajectory).toDict();
        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = RailBatchIngestor.normalizeRailSample(
                batch,
                ((List<Map<String, Object>>) batch.get("samples")).get(0),
                ""
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> normalizedTrajectory = (Map<String, Object>) normalized.get("trajectory");
        assertEquals(List.of(1, 2, 3), normalizedTrajectory.get("prompt_ids"));
        assertEquals(List.of(4, 5), normalizedTrajectory.get("response_ids"));
        assertEquals(List.of(-0.1d, -0.2d), normalizedTrajectory.get("response_logprobs"));
    }

    @Test
    void toleratesMessageModelDumpFailure() {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("m1")
                .messages(List.of(
                        Map.of("role", "user", "content", "hello"),
                        new BrokenMessage()
                ))
                .response(Map.of("role", "assistant", "content", "pong"))
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-broken-message")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(TrajectoryStep.builder().kind("llm").detail(detail).build()))
                .build();

        RailV1Batch batch = new OnlineTrajectoryConverter("user-1").convert(trajectory);

        assertEquals(1, batch.getSamples().size());
        assertEquals(
                Map.of("role", "assistant", "content", "previous turn"),
                batch.getSamples().get(0).getMessages().get(1)
        );
    }

    private static final class BrokenMessage {
        private final String role = "assistant";
        private final String content = "previous turn";

        @SuppressWarnings("unused")
        public Map<String, Object> model_dump() {
            throw new IllegalStateException("MockValSer");
        }
    }
}
