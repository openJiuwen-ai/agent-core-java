/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Verifies that streaming resume executes and checkpoints the same authoritative session.
 *
 * @since 0.1.17
 */
class DeepAgentStreamingResumeStateTest {
    private static final AgentCard CARD = new AgentCard("resume", "resume", "resume regression");

    private DeepAgent agent;

    @AfterEach
    void shutdownAgent() {
        if (agent != null) {
            agent.shutdown();
        }
    }

    @Test
    void successiveResumesPreserveUpdatesAndDeletionsInCallerAndCheckpoint() {
        String sessionId = UUID.randomUUID().toString();
        AgentSession caller = new AgentSession(sessionId, null, CARD);
        caller.preRun(Map.of("inputs", Map.of()));
        caller.updateState(Map.of("pending", "first"));
        agent = newAgent();

        resume(agent, sessionId, caller, "first", "second");
        assertThat(caller.getState("pending")).isEqualTo("second");
        assertThat(caller.getState("completed")).isEqualTo("first");
        assertCheckpoint(sessionId, "first", "second");

        resume(agent, sessionId, caller, "second", "");
        assertThat(caller.getState("pending")).isNull();
        assertThat(caller.getState("completed")).isEqualTo("second");
        assertCheckpoint(sessionId, "second", null);
    }

    @Test
    void resumesWithoutCallerLoadAndSaveEffectiveSessionCheckpoint() {
        String sessionId = UUID.randomUUID().toString();
        DeepAgentSession seed = new DeepAgentSession(sessionId, null, CARD);
        seed.preRun(Map.of());
        seed.updateState(Map.of("pending", "first"));
        seed.postRun();
        agent = newAgent();

        resume(agent, sessionId, null, "first", "second");
        assertCheckpoint(sessionId, "first", "second");
        resume(agent, sessionId, null, "second", "");
        assertCheckpoint(sessionId, "second", null);
    }

    private static DeepAgent newAgent() {
        DeepAgent configuredAgent = new DeepAgent(CARD);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnableTaskLoop(true);
        configuredAgent.configure(config);
        configuredAgent.setReactAgent(new ResumingReactAgent(), true);
        return configuredAgent;
    }

    private static void resume(DeepAgent agent, String sessionId, AgentSessionApi caller,
            String expectedPending, String nextPending) {
        InteractiveInput query = new InteractiveInput();
        query.update("decision", Map.of("expected", expectedPending, "next", nextPending));
        List<Object> chunks = new ArrayList<>();
        agent.stream(Map.of("query", query, "conversation_id", sessionId), caller, List.of(StreamMode.OUTPUT))
                .forEachRemaining(chunks::add);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk).isInstanceOf(OutputSchema.class);
            if (chunk instanceof OutputSchema output) {
                assertThat(output.getType()).isEqualTo("answer");
            }
        });
    }

    private static void assertCheckpoint(String sessionId, String completed, String pending) {
        DeepAgentSession restored = new DeepAgentSession(sessionId, null, CARD);
        restored.preRun(Map.of());
        assertThat(restored.getState("completed")).isEqualTo(completed);
        assertThat(restored.getState("pending")).isEqualTo(pending);
    }

    private static final class ResumingReactAgent {
        /**
         * Applies one resume decision to the supplied session, removing a finished pending key.
         *
         * @param inputs invocation inputs containing the decision
         * @param session session owning the resumed execution
         * @return completed answer
         * @since 0.1.17
         */
        public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            assertThat(session).isNotNull();
            assertThat(inputs.get("query")).isInstanceOf(InteractiveInput.class);
            InteractiveInput query = (InteractiveInput) inputs.get("query");
            assertThat(query.getUserInputs().get("decision")).isInstanceOf(Map.class);
            Map<?, ?> decision = (Map<?, ?>) query.getUserInputs().get("decision");
            assertThat(session.getState("pending")).isEqualTo(decision.get("expected"));
            State state = sessionState(session);
            Map<String, Object> updated = new LinkedHashMap<>();
            updated.put("completed", decision.get("expected"));
            state.setState(Map.of(State.GLOBAL_STATE_KEY, updated));
            if (!"".equals(decision.get("next"))) {
                session.updateState(Map.of("pending", decision.get("next")));
            }
            return CompletableFuture.completedFuture(Map.of("output", "resumed", "result_type", "answer"));
        }

        private static State sessionState(AgentSessionApi session) {
            if (session instanceof AgentSession agentSession) {
                return agentSession.getInner().state();
            }
            if (session instanceof DeepAgentSession deepSession) {
                return deepSession.getInner().state();
            }
            throw new IllegalArgumentException("Unsupported resume session: " + session.getClass().getName());
        }
    }
}
