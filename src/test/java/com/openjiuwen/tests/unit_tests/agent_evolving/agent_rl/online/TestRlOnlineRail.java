/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.online;

import com.openjiuwen.agent_evolving.agent_rl.online.rail.OnlineRlBatch;
import com.openjiuwen.agent_evolving.agent_rl.online.rail.RLOnlineRail;
import com.openjiuwen.agent_evolving.agent_rl.online.rail.TrajectoryUploader;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RlOnlineRail.
 * <p>
 * Mirrors Python's {@code test_rl_online_rail.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/}.
 */
@DisplayName("RlOnlineRail Tests")
class TestRlOnlineRail {

    @Test
    @DisplayName("before invoke enables token capture")
    void testRlOnlineRailBeforeInvokeEnablesTokenCapture() {
        FakeAgent agent = new FakeAgent();
        RLOnlineRail rail = new RLOnlineRail("s1", "http://gateway.local", "user-1", null);
        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).build();

        rail.onBeforeInvoke(ctx);

        FakeConfig config = agent.react_agent.config;
        assertThat(config.llm_return_token_ids).isTrue();
        assertThat(config.llm_logprobs).isTrue();
        assertThat(config.llm_top_logprobs).isEqualTo(1);
        assertThat(config.custom_headers).containsEntry("x-user-id", "user-1");
    }

    @Test
    @DisplayName("background evolution uploads batch")
    void testRlOnlineRailBackgroundEvolutionUploadsBatch() {
        CollectingUploader uploader = new CollectingUploader();
        RLOnlineRail rail = new RLOnlineRail("s1", "http://gateway.local", "user-1", uploader);
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-1")
                .sessionId("s1")
                .source("rl_online")
                .steps(List.of(TrajectoryStep.builder()
                        .kind("llm")
                        .detail(LLMCallDetail.builder()
                                .model("m1")
                                .messages(List.of(Map.of("role", "user", "content", "hi")))
                                .response(Map.of("role", "assistant", "content", "hello"))
                                .build())
                        .build()))
                .build();

        rail.safeRunEvolution(Map.of("trajectory", trajectory));

        assertThat(uploader.batches).hasSize(1);
        assertThat(uploader.batches.getFirst().getTenantId()).isEqualTo("user-1");
        assertThat(uploader.batches.getFirst().getSamples().getFirst().getResponseText()).isEqualTo("hello");
    }

    static final class CollectingUploader implements TrajectoryUploader {
        final List<OnlineRlBatch> batches = new ArrayList<>();

        @Override
        public void enqueue(OnlineRlBatch batch) {
            batches.add(batch);
        }
    }

    static final class FakeAgent {
        public final FakeReactAgent react_agent = new FakeReactAgent();
    }

    static final class FakeReactAgent {
        public final FakeConfig config = new FakeConfig();
    }

    static final class FakeConfig {
        public boolean llm_return_token_ids;
        public boolean llm_logprobs;
        public int llm_top_logprobs;
        public Map<String, Object> custom_headers;
    }
}
