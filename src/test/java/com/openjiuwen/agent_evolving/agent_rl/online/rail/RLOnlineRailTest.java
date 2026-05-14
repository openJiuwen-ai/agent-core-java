/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RLOnlineRailTest {

    static class CollectingUploader implements TrajectoryUploader {
        final List<OnlineRlBatch> batches = new ArrayList<>();
        @Override
        public void enqueue(OnlineRlBatch batch) {
            batches.add(batch);
        }
    }

    static class Config {
        public boolean llm_return_token_ids = false;
        public boolean llm_logprobs = false;
        public int llm_top_logprobs = 0;
        public Map<String, Object> custom_headers = null;
    }

    static class ReactAgent {
        public Config config = new Config();
    }

    static class Agent {
        public ReactAgent react_agent = new ReactAgent();
    }

    @Test
    void beforeInvokeEnablesTokenCapture() {
        RLOnlineRail rail = new RLOnlineRail("s1", "http://gateway.local", "user-1", null);
        Agent agent = new Agent();
        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).build();

        rail.onBeforeInvoke(ctx);

        assertTrue(agent.react_agent.config.llm_return_token_ids);
        assertTrue(agent.react_agent.config.llm_logprobs);
        assertEquals(1, agent.react_agent.config.llm_top_logprobs);
        assertEquals("user-1", agent.react_agent.config.custom_headers.get("x-user-id"));
    }

    @Test
    void safeRunEvolutionUploadsBatch() {
        CollectingUploader uploader = new CollectingUploader();
        RLOnlineRail rail = new RLOnlineRail("s1", "http://gateway.local", "user-1", uploader);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", "hello");
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .outputs(response)
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-1")
                .steps(List.of(step))
                .build();

        rail.safeRunEvolution(Map.of("trajectory", trajectory));

        assertEquals(1, uploader.batches.size());
        assertEquals("user-1", uploader.batches.get(0).getTenantId());
        assertEquals("hello", uploader.batches.get(0).getSamples().get(0).getResponseText());
    }
}
