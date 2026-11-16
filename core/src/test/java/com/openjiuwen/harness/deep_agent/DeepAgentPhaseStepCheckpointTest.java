/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer;
import com.openjiuwen.core.session.checkpointer.step.KvStepJournal;
import com.openjiuwen.core.session.checkpointer.step.StepCheckpointPolicy;
import com.openjiuwen.core.session.checkpointer.step.StepCursor;
import com.openjiuwen.core.session.checkpointer.step.StepRecord;
import com.openjiuwen.core.session.checkpointer.step.StepStatus;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * DeepAgent task-loop round boundaries must reach the checkpointer and the
 * audit journal. The loop session is a {@link DeepAgentSession}, so the
 * boundary path unwraps it to the internal session before persisting; one
 * invoke with several rounds must leave one PHASE boundary per persisted
 * round and a monotonic append-only journal.
 */
class DeepAgentPhaseStepCheckpointTest {
    private static final String SESSION_ID = "deep-phase-checkpoint-session";

    private PersistenceCheckpointer checkpointer;

    private DeepAgent agent;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void installPersistenceCheckpointer() {
        checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);
    }

    @AfterEach
    void releasePersistenceCheckpointer() {
        if (agent != null) {
            agent.shutdown();
            agent = null;
        }
        CheckpointerFactory.releaseDefaultCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    @Test
    @DisplayName("3.13 task-loop rounds persist PHASE boundaries and audit them in order")
    void taskLoopRoundsPersistPhaseBoundaries() {
        agent = HarnessFactory.createDeepAgent(
                uniqueCard("phase-checkpoint"),
                DeepAgentConfig.builder()
                        .workspacePath(tempDir.toString())
                        .enableTaskLoop(true)
                        .maxIterations(4)
                        .stepCheckpointPolicy(StepCheckpointPolicy.STEP_ALWAYS)
                        .build(),
                null);
        installEchoModel(agent);

        // The follow-up enqueued before the invoke drives a second round, so
        // one invoke produces two PHASE boundaries.
        agent.getLoopController().enqueueFollowUp("continue");

        Map<String, Object> result = agent.invoke(Map.of(
                "query", "Start task loop.",
                "conversation_id", SESSION_ID));

        assertThat(result).containsKey("output");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rounds = (List<Map<String, Object>>) result.get("rounds");
        assertThat(rounds).hasSize(2);

        List<StepRecord> trail = new KvStepJournal(checkpointer.kvStore()).replay(SESSION_ID);
        assertThat(trail).hasSize(2);
        assertThat(trail).allMatch(record -> "PHASE".equals(record.getStepType()));
        assertThat(trail).allMatch(record -> record.getStatus() == StepStatus.COMPLETED);
        // Audit is append-only with strictly increasing seq across rounds.
        assertThat(trail.get(1).getSeq()).isGreaterThan(trail.get(0).getSeq());

        // The cursor side: lastCompletedStep reflects the newest persisted
        // PHASE boundary for this session.
        DeepAgentSession probe = new DeepAgentSession(SESSION_ID, null, agent.getCard());
        probe.preRun(Map.of());
        StepCursor cursor = checkpointer.lastCompletedStep(probe.getInner());
        assertThat(cursor).isNotNull();
        assertThat(cursor.getStepType()).isEqualTo("PHASE");
        assertThat(cursor.getStatus()).isEqualTo(StepStatus.COMPLETED);
        assertThat(cursor.getSeq()).isEqualTo(trail.get(trail.size() - 1).getSeq());
    }

    @Test
    @DisplayName("3.13 a failed round records FAILED evidence without moving the resume point")
    void failedRoundRecordsFailedEvidence() {
        agent = HarnessFactory.createDeepAgent(
                uniqueCard("phase-failed"),
                DeepAgentConfig.builder()
                        .workspacePath(tempDir.toString())
                        .enableTaskLoop(true)
                        .maxIterations(4)
                        .stepCheckpointPolicy(StepCheckpointPolicy.STEP_ALWAYS)
                        .build(),
                null);
        installFailingModel(agent);

        Map<String, Object> result = agent.invoke(Map.of(
                "query", "Fail the loop.",
                "conversation_id", SESSION_ID));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rounds = (List<Map<String, Object>>) result.get("rounds");
        assertThat(rounds).hasSize(1);
        // The failed round resolves through handleTaskFailed, whose payload
        // carries the error key: the PHASE boundary must be recorded as
        // FAILED evidence, never a resume point.
        assertThat(rounds.get(0)).containsKey("error");
        List<StepRecord> trail = new KvStepJournal(checkpointer.kvStore()).replay(SESSION_ID);
        assertThat(trail).hasSize(1);
        assertThat(trail.get(0).getStepType()).isEqualTo("PHASE");
        assertThat(trail.get(0).getStatus()).isEqualTo(StepStatus.FAILED);

        DeepAgentSession probe = new DeepAgentSession(SESSION_ID, null, agent.getCard());
        probe.preRun(Map.of());
        StepCursor failed = checkpointer.lastFailedStep(probe.getInner());
        assertThat(failed).isNotNull();
        assertThat(failed.getStepType()).isEqualTo("PHASE");
        assertThat(failed.getStatus()).isEqualTo(StepStatus.FAILED);
        // FAILED evidence never moves the resume point.
        assertThat(checkpointer.lastCompletedStep(probe.getInner())).isNull();
    }

    private static AgentCard uniqueCard(String prefix) {
        String id = prefix + "-" + UUID.randomUUID().toString().replace("-", "");
        return AgentCard.builder().id(id).name(prefix).description("phase checkpoint test").build();
    }

    private static void installEchoModel(DeepAgent deepAgent) {
        Model model = Mockito.mock(Model.class);
        when(model.invoke(any(List.class), any(ModelInvokeOptions.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(
                        echoMessage("model:" + extractLastMessageText(invocation.getArgument(0)))));
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> echoMessage(
                        "model:" + extractLastMessageText(invocation.getArgument(0))));
        deepAgent.getAgent().setLlm(model);
    }

    private static void installFailingModel(DeepAgent deepAgent) {
        Model model = Mockito.mock(Model.class);
        when(model.invoke(any(List.class), any(ModelInvokeOptions.class)))
                .thenAnswer(invocation -> CompletableFuture.failedFuture(
                        new IllegalStateException("simulated phase failure")));
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    throw new IllegalStateException("simulated phase failure");
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
        if (rawMessages instanceof List<?> messages && !messages.isEmpty()) {
            Object last = messages.get(messages.size() - 1);
            if (last instanceof BaseMessage baseMessage && baseMessage.getContent() != null) {
                return String.valueOf(baseMessage.getContent());
            }
        }
        return String.valueOf(rawMessages);
    }
}
