/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer;
import com.openjiuwen.core.session.checkpointer.step.PauseRequestRegistry;
import com.openjiuwen.core.session.checkpointer.step.StepCheckpointPolicy;
import com.openjiuwen.core.session.checkpointer.step.StepCursor;
import com.openjiuwen.core.session.checkpointer.step.StepStatus;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User-pause flow through the real ReActAgent invoke loop: a pause request
 * published while the model runs is consumed at the next iteration head. A
 * persisted pause ends the invocation with the paused shape; a pause whose
 * cursor could not be persisted keeps the invocation running to its own
 * completion instead of ending with a paused shape no resume can continue.
 */
class ReActAgentPauseResumeTest {
    private static final String SESSION_ID = "react-pause-flow-session";

    private static final String AGENT_ID = "react-pause-flow-agent";

    private ReActAgent agent;

    @BeforeEach
    void clearTenantContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @AfterEach
    void restoreTenantContextAndCheckpointer() {
        TenantContextHolder.clearCurrentTenant();
        PauseRequestRegistry.clearPauseRequest(SESSION_ID);
        if (agent != null) {
            agent.getAbilityManager().teardownTools();
            agent = null;
        }
        CheckpointerFactory.releaseDefaultCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    @Test
    @DisplayName("a persisted pause ends the invocation with the paused shape")
    void persistedPauseEndsTheInvocationAsPaused() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        ReActAgent reactAgent = newAgent();
        AtomicInteger calls = new AtomicInteger();
        reactAgent.setLlm(pauseInjectingModel(calls, "pause-flow-done"));

        AgentSession session = new AgentSession(SESSION_ID, null, reactAgent.getCard());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) reactAgent.invoke(
                Map.of("query", "pause me", "conversation_id", SESSION_ID), session);

        assertThat(result).containsEntry("result_type", "paused");
        StepCursor paused = checkpointer.lastPausedStep(session.getInner());
        assertThat(paused).isNotNull();
        assertThat(paused.getStatus()).isEqualTo(StepStatus.PAUSED);
        assertThat(paused.getStepName()).isEqualTo("user-pause");
        // The tool-call boundary of the first iteration persisted at seq 1,
        // so the pause cursor minted above the seeded counter lands at seq 2.
        assertThat(paused.getSeq()).isEqualTo(2L);
    }

    @Test
    @DisplayName("an unpersisted pause keeps the invocation running to completion")
    void unpersistedPauseKeepsTheInvocationRunning() {
        PauseRejectingCheckpointer checkpointer = new PauseRejectingCheckpointer(new InMemoryKVStore());
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        ReActAgent reactAgent = newAgent();
        AtomicInteger calls = new AtomicInteger();
        reactAgent.setLlm(pauseInjectingModel(calls, "pause-flow-done"));

        AgentSession session = new AgentSession(SESSION_ID, null, reactAgent.getCard());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) reactAgent.invoke(
                Map.of("query", "pause me", "conversation_id", SESSION_ID), session);

        // The pause intent was consumed but its cursor could not be persisted,
        // so the invocation must complete on its own answer shape.
        assertThat(result).containsEntry("result_type", "answer");
        assertThat(checkpointer.lastPausedStep(session.getInner())).isNull();
        // The tool-call boundary of the first iteration still persisted.
        StepCursor completed = checkpointer.lastCompletedStep(session.getInner());
        assertThat(completed).isNotNull();
        assertThat(completed.getSeq()).isEqualTo(1L);
    }

    private ReActAgent newAgent() {
        ReActAgent newAgent = new ReActAgent(probeAgentCard());
        newAgent.configure(ReActAgentConfig.builder()
                .maxIterations(5)
                .shouldFailTaskOnToolError(false)
                .stepCheckpointPolicy(StepCheckpointPolicy.STEP_ALWAYS)
                .build());
        newAgent.getAbilityManager().addAbility(probeToolCard("probe_a"), probeTool("probe_a"));
        this.agent = newAgent;
        return newAgent;
    }

    private static AgentCard probeAgentCard() {
        return new AgentCard(AGENT_ID, AGENT_ID, "react pause flow agent");
    }

    private static ToolCard probeToolCard(String name) {
        return ToolCard.builder()
                .id(name + "_id")
                .name(name)
                .description("probe tool " + name)
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()))
                .build();
    }

    private static Tool probeTool(String name) {
        return new LocalFunction(probeToolCard(name), inputs -> "ok-" + name);
    }

    /**
     * Scripted model that publishes the pause intent during its first call:
     * the request lands while iteration zero is inside the model call, so the
     * iteration-one head is the first boundary that can consume it.
     *
     * @param callCount receives the number of model invocations
     * @param finalAnswer text the later calls answer with
     * @return the model wired with a scripted client
     */
    private static Model pauseInjectingModel(AtomicInteger callCount, String finalAnswer) {
        return new Model((messages, modelConfig, clientConfig, options) -> {
            int call = callCount.getAndIncrement();
            if (call == 0) {
                PauseRequestRegistry.requestPause(SESSION_ID, Map.of("operator", "op-pause"));
                return CompletableFuture.completedFuture(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(toolCall("call-a", "probe_a")))
                        .build());
            }
            return CompletableFuture.completedFuture(new AssistantMessage(finalAnswer));
        });
    }

    private static ToolCall toolCall(String id, String name) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments("{}")
                .build();
    }

    /**
     * Persistence checkpointer whose pause evidence writes always fail: every
     * other save path behaves like the persistence backend, so only the
     * paused-cursor persistence is rejected.
     */
    private static final class PauseRejectingCheckpointer extends PersistenceCheckpointer {
        private PauseRejectingCheckpointer(InMemoryKVStore kvStore) {
            super(kvStore);
        }

        @Override
        public boolean stepPaused(BaseSession session, StepCursor cursor, Map<String, Object> pauseInfo) {
            return false;
        }
    }
}
