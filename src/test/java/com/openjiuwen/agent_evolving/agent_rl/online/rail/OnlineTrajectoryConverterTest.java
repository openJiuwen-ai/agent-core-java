/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.RailBatchIngestor;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnlineTrajectoryConverterTest {

    @Test
    void readsPromptAndResponseTokenIdsFromResponse() {
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("prompt_token_ids", List.of(1, 2, 3));
        provider.put("choices", List.of(Map.of("token_ids", List.of(4, 5), "logprobs", List.of(-0.1, -0.2))));
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("m1")
                .messages(List.of(Map.of("role", "user", "content", "hello")))
                .response(Map.of("role", "assistant", "content", "pong"))
                .meta(Map.of("provider_response_json", provider))
                .build();
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(detail)
                .build();

        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-1")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(step))
                .build();
        RailV1Batch batch = new OnlineTrajectoryConverter("user-1").convert(trajectory);

        assertEquals("rail-v1", batch.getProtocolVersion());
        assertEquals("session-1", batch.getSessionId());
        assertEquals("user-1", batch.getTenantId());
        assertEquals("traj-1", batch.getTrajectoryId());
        assertEquals(1, batch.getSamples().size());
        assertEquals(List.of(1, 2, 3), batch.getSamples().getFirst().getPromptIds());
        assertEquals(List.of(4, 5), batch.getSamples().getFirst().getResponseTokens());
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
                .meta(Map.of(
                        "prompt_token_ids", List.of(1, 2, 3),
                        "completion_token_ids", List.of(4, 5),
                        "logprobs", Map.of("content", List.of(Map.of("logprob", -0.1), Map.of("logprob", -0.2)))
                ))
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-stream")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(step))
                .build();

        Map<String, Object> payload = new OnlineTrajectoryConverter("user-1").convert(trajectory).toDict();
        @SuppressWarnings("unchecked")
        Map<String, Object> sample = (Map<String, Object>) ((List<?>) payload.get("samples")).getFirst();
        Map<String, Object> normalized = RailBatchIngestor.normalizeRailSample(payload, sample, "");
        @SuppressWarnings("unchecked")
        Map<String, Object> normalizedTrajectory = (Map<String, Object>) normalized.get("trajectory");

        assertEquals(List.of(1, 2, 3), normalizedTrajectory.get("prompt_ids"));
        assertEquals(List.of(4, 5), normalizedTrajectory.get("response_ids"));
        assertEquals(List.of(-0.1, -0.2), normalizedTrajectory.get("response_logprobs"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void toleratesMessageModelDumpFailureByFallingBackToRoleContentFields() {
        class BrokenMessage {
            public String role = "assistant";
            public String content = "previous turn";

            public Map<String, Object> model_dump() {
                throw new RuntimeException("MockValSer");
            }
        }

        List rawMessages = new ArrayList();
        rawMessages.add(Map.of("role", "user", "content", "hello"));
        rawMessages.add(new BrokenMessage());
        LLMCallDetail detail = new LLMCallDetail(
                "m1",
                rawMessages,
                Map.of("role", "assistant", "content", "pong"),
                null,
                null,
                null
        );
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(detail)
                .build();

        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-broken-message")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(step))
                .build();
        RailV1Batch batch = new OnlineTrajectoryConverter("user-1").convert(trajectory);

        assertEquals(1, batch.getSamples().size());
        assertEquals(Map.of("role", "assistant", "content", "previous turn"),
                batch.getSamples().getFirst().getMessages().get(1));
    }
}
