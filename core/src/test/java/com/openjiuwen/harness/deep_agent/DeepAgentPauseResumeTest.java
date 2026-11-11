/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer;
import com.openjiuwen.core.session.checkpointer.step.PauseRequestRegistry;
import com.openjiuwen.core.session.checkpointer.step.StepCheckpointPolicy;
import com.openjiuwen.core.session.checkpointer.step.StepCursor;
import com.openjiuwen.core.session.checkpointer.step.StepStatus;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User-pause flow through the real DeepAgent task loop: a pause request
 * published while the first round runs is consumed at the round tail and the
 * loop ends with the paused round shape. A pause whose cursor could not be
 * persisted leaves the round result in its own shape and the loop continues
 * with the next round instead of claiming a pause no resume can continue.
 */
class DeepAgentPauseResumeTest {
    private static final String SESSION_ID = "deep-pause-flow-session";

    private PersistenceCheckpointer checkpointer;

    private DeepAgent agent;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void clearTenantContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @AfterEach
    void shutdownAgentAndCheckpointer() {
        TenantContextHolder.clearCurrentTenant();
        PauseRequestRegistry.clearPauseRequest(SESSION_ID);
        if (agent != null) {
            agent.shutdown();
            agent = null;
        }
        CheckpointerFactory.releaseDefaultCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    @Test
    @DisplayName("a persisted pause ends the task loop with the paused round shape")
    void persistedPauseEndsTheTaskLoopAsPaused() {
        installCheckpointer(new PersistenceCheckpointer(new InMemoryKVStore()));
        agent = newDeepAgent();
        agent.getLoopController().enqueueFollowUp("continue after pause");

        Map<String, Object> result = agent.invoke(
                Map.of("query", "pause me", "conversation_id", SESSION_ID));

        // The follow-up enqueued before the invoke would drive a second
        // round; the persisted pause must end the loop after round one.
        assertThat(roundsOf(result)).hasSize(1);
        assertThat(result).containsEntry("result_type", "paused")
                .containsEntry("output", "Paused by user request");

        StepCursor paused = pausedCursorOfProbe();
        assertThat(paused).isNotNull();
        assertThat(paused.getStatus()).isEqualTo(StepStatus.PAUSED);
        assertThat(paused.getStepName()).isEqualTo("user-pause");
        // Round one recorded its PHASE boundary at seq 1; the pause cursor
        // minted above the seeded counter lands at seq 2.
        assertThat(paused.getSeq()).isEqualTo(2L);
    }

    @Test
    @DisplayName("an unpersisted pause keeps the task loop running with the round's own shape")
    void unpersistedPauseKeepsTheTaskLoopRunning() {
        installCheckpointer(new PauseRejectingCheckpointer(new InMemoryKVStore()));
        agent = newDeepAgent();
        agent.getLoopController().enqueueFollowUp("continue after pause");

        Map<String, Object> result = agent.invoke(
                Map.of("query", "pause me", "conversation_id", SESSION_ID));

        // The pause intent was consumed but its cursor could not be persisted:
        // no paused round shape is claimed and the loop keeps running, so the
        // enqueued follow-up round still executes to its own answer.
        assertThat(roundsOf(result)).hasSize(2);
        assertThat(result).doesNotContainKey("pause_info");
        assertThat(result).containsEntry("result_type", "answer");
        assertThat(result).containsEntry("output", "model:continue after pause");

        assertThat(pausedCursorOfProbe()).isNull();
    }

    private void installCheckpointer(PersistenceCheckpointer target) {
        checkpointer = target;
        CheckpointerFactory.setDefaultCheckpointer(target);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> roundsOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("rounds");
    }

    private StepCursor pausedCursorOfProbe() {
        DeepAgentSession probe = new DeepAgentSession(SESSION_ID, null, agent.getCard());
        probe.preRun(Map.of());
        return checkpointer.lastPausedStep(probe.getInner());
    }

    private DeepAgent newDeepAgent() {
        DeepAgent newAgent = HarnessFactory.createDeepAgent(
                uniqueCard("pause-flow"),
                DeepAgentConfig.builder()
                        .workspacePath(tempDir.toString())
                        .enableTaskLoop(true)
                        .maxIterations(4)
                        .stepCheckpointPolicy(StepCheckpointPolicy.STEP_ALWAYS)
                        .build(),
                null);
        installPauseInjectingModel(newAgent);
        return newAgent;
    }

    private static AgentCard uniqueCard(String prefix) {
        String id = prefix + "-" + UUID.randomUUID().toString().replace("-", "");
        return AgentCard.builder().id(id).name(prefix).description("pause flow test").build();
    }

    /**
     * Scripted model that publishes the pause intent during its first call:
     * the request lands while round one is inside the model call, so the
     * round-one tail is the first boundary that can consume it (the inner
     * ReAct loop completes within its only iteration because the model answers
     * with plain text). Later calls answer the follow-up round with the last
     * message text.
     *
     * @param deepAgent agent whose inner ReAct model is replaced
     */
    private static void installPauseInjectingModel(DeepAgent deepAgent) {
        AtomicBoolean isPausePublished = new AtomicBoolean(false);
        Model model = new Model((messages, modelConfig, clientConfig, options) -> {
            if (isPausePublished.compareAndSet(false, true)) {
                PauseRequestRegistry.requestPause(SESSION_ID, Map.of("operator", "op-pause"));
            }
            return CompletableFuture.completedFuture(
                    echoMessage("model:" + extractLastMessageText(messages)));
        });
        deepAgent.getAgent().setLlm(model);
    }

    private static AssistantMessage echoMessage(String content) {
        return AssistantMessage.builder()
                .content(content)
                .usageMetadata(UsageMetadata.builder()
                        .inputTokens(3)
                        .outputTokens(5)
                        .totalTokens(8)
                        .build())
                .build();
    }

    private static String extractLastMessageText(Object rawMessages) {
        if (rawMessages instanceof List<?> messageList && !messageList.isEmpty()) {
            Object last = messageList.get(messageList.size() - 1);
            if (last instanceof BaseMessage baseMessage && baseMessage.getContent() != null) {
                return String.valueOf(baseMessage.getContent());
            }
        }
        return String.valueOf(rawMessages);
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
