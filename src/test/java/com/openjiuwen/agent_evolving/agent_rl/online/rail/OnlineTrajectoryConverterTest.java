/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnlineTrajectoryConverterTest {

    @Test
    void readsPromptAndResponseTokenIdsFromMeta() {
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("prompt_token_ids", List.of(1, 2, 3));
        provider.put("choices", List.of(Map.of("token_ids", List.of(4, 5), "logprobs", List.of(-0.1, -0.2))));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("provider_response_json", provider);

        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .inputs(Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))))
                .outputs(Map.of("content", "pong"))
                .meta(meta)
                .build();

        Trajectory trajectory = Trajectory.builder().executionId("traj-1").steps(List.of(step)).build();
        OnlineRlBatch batch = new OnlineTrajectoryConverter("user-1").convert(trajectory);

        assertEquals(1, batch.getSamples().size());
        assertEquals(List.of(1, 2, 3), batch.getSamples().get(0).getPromptIds());
        assertEquals(List.of(4, 5), batch.getSamples().get(0).getResponseTokens());
    }

    @Test
    void toleratesMessageModelDumpFailureByFallingBackToRoleContentFields() {
        class BrokenMessage {
            public String role = "assistant";
            public String content = "previous turn";
            public Map<String, Object> model_dump() {
                throw new RuntimeException("MockValSer");
            }
        }

        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .inputs(Map.of("messages", List.of(Map.of("role", "user", "content", "hello"), new BrokenMessage())))
                .outputs(Map.of("content", "pong"))
                .build();

        Trajectory trajectory = Trajectory.builder().executionId("traj-broken-message").steps(List.of(step)).build();
        OnlineRlBatch batch = new OnlineTrajectoryConverter("user-1").convert(trajectory);

        assertEquals(1, batch.getSamples().size());
        assertEquals(Map.of("role", "assistant", "content", "previous turn"), batch.getSamples().get(0).getMessages().get(1));
    }
}
