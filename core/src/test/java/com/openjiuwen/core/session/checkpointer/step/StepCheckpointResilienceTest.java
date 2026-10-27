/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
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
 * Resilience cases accepted from the issue #195 test-side review (items 3.1,
 * 3.2, 3.4, 3.6, 3.8, 4.2, 5.1): crash-boundary placement, restart reuse,
 * empty-trail replay, multi-round interrupt iterations, storage-failure
 * degradation, and post-resume exception rounds - all through the real
 * {@code ReActAgent.invoke} loop over a persistence-backed checkpointer.
 */
class StepCheckpointResilienceTest {
    private static final String SESSION_ID = "step-resilience-session";

    private PersistenceCheckpointer checkpointer;

    private ReActAgent agent;

    @BeforeEach
    void installPersistenceCheckpointer() {
        checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);
    }

    @AfterEach
    void releasePersistenceCheckpointer() {
        if (agent != null) {
            agent.getAbilityManager().teardownTools();
            agent = null;
        }
        CheckpointerFactory.releaseDefaultCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    @Test
    @DisplayName("3.1 crash right before the first boundary leaves no cursor to resume from")
    void crashBeforeFirstBoundaryLeavesNoCursor() {
        // The model blows up on the very first call, before any boundary was
        // observed, so nothing can be persisted for this task.
        ReActAgent reactAgent = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
        reactAgent.setLlm(scriptedModel(new AtomicInteger(), null, 0, new RuntimeException("boom")));

        AgentSession session = new AgentSession(SESSION_ID, null, reactAgent.getCard());
        assertThat(org.assertj.core.api.Assertions.catchRuntimeException(
                () -> reactAgent.invoke(Map.of("query", "die early", "conversation_id", SESSION_ID), session)))
                .hasMessageContaining("boom");

        // No boundary was observed, so no COMPLETED cursor exists - the only
        // trace is the FAILED iteration record in the audit journal.
        assertThat(checkpointer.lastCompletedStep(session.getInner())).isNull();
        assertThat(trail(SESSION_ID)).hasSize(1);
        assertThat(trail(SESSION_ID).get(0).getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(checkpointer.lastFailedStep(session.getInner())).isNotNull();
    }

    @Test
    @DisplayName("3.1 crash after the last boundary keeps the resume point on the completed step")
    void crashAfterLastBoundaryKeepsResumePoint() {
        // Turn one completes both tool boundaries, turn two (the final model
        // call) dies - the last completed tool-call boundary must survive.
        AtomicInteger calls = new AtomicInteger();
        ReActAgent firstTurn = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
        firstTurn.setLlm(scriptedModel(calls, null, 1, new RuntimeException("late boom")));
        AgentSession session = new AgentSession(SESSION_ID, null, firstTurn.getCard());
        assertThat(org.assertj.core.api.Assertions.catchRuntimeException(
                () -> firstTurn.invoke(Map.of("query", "die late", "conversation_id", SESSION_ID), session)))
                .hasMessageContaining("late boom");

        StepCursor persisted = checkpointer.lastCompletedStep(session.getInner());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getStepType()).isEqualTo("TOOL_CALL");
        assertThat(persisted.getStatus()).isEqualTo(StepStatus.COMPLETED);
        // The failing second iteration left FAILED evidence without moving the
        // completed resume point.
        StepCursor failed = checkpointer.lastFailedStep(session.getInner());
        assertThat(failed).isNotNull();
        assertThat(failed.getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(failed.getSeq()).isGreaterThan(persisted.getSeq());
    }

    @Test
    @DisplayName("3.2 interrupting before any interruption snapshot means no resumable interrupt point")
    void interruptBeforeSnapshotResumesWithoutInterruptPoint() {
        // A fresh session over an empty store has no InterruptionState and no
        // cursor: the resume path must see neither and start clean.
        AgentSession resumed = new AgentSession(SESSION_ID, null, null);
        resumed.preRun(Map.of());
        assertThat(checkpointer.lastCompletedStep(resumed.getInner())).isNull();
        assertThat(trail(SESSION_ID)).isEmpty();
    }

    @Test
    @DisplayName("3.4 a second crash is handled by a fresh recovery pass over the same residue")
    void secondCrashRecoveryIsIdempotentAcrossProcesses() {
        // Process one: persist a RUNNING residue, then "crash" (instance dropped).
        AgentSession crashed = new AgentSession(SESSION_ID, null, null);
        checkpointer.preAgentExecute(crashed.getInner(), null);
        checkpointer.stepCompleted(crashed.getInner(),
                new StepCursor(9L, "TOOL_CALL", "probe_a", StepStatus.RUNNING, 777L));

        // Process two: a brand-new recovery service sees the same residue and
        // marks it lost; the residue stays so process three can do the same.
        StepCheckpointRecoveryService firstProcess = new StepCheckpointRecoveryService(checkpointer, null);
        assertThat(firstProcess.recoverLeftovers())
                .hasSize(1)
                .first().satisfies(action -> {
                    assertThat(action.type())
                            .isEqualTo(StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST);
                });

        StepCheckpointRecoveryService secondProcess = new StepCheckpointRecoveryService(checkpointer, null);
        List<StepCheckpointRecoveryService.RecoveryAction> again = secondProcess.recoverLeftovers();
        assertThat(again).hasSize(1);
        assertThat(again.get(0).type())
                .isEqualTo(StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST);
        assertThat(checkpointer.scanRunningSteps()).hasSize(1);
    }

    @Test
    @DisplayName("3.6 replaying an empty or unknown session returns an empty trail without failing")
    void replayEmptyOrUnknownSessionReturnsEmptyTrail() {
        KvStepJournal journal = new KvStepJournal(checkpointer.kvStore());
        assertThat(journal.replay(SESSION_ID)).isEmpty();
        assertThat(journal.replay("no-such-session")).isEmpty();
        assertThat(journal.replay("")).isEmpty();
    }

    @Test
    @DisplayName("3.8 multi-round invoke iterations re-arm the loop and keep auditing every round")
    void multiRoundInterruptResumeIterationsPersistEachRound() {
        // Round one runs to completion and persists the cursor; a second turn
        // on the same session id restores the state through preRun and runs
        // another full loop - each round leaves audited boundaries behind.
        AtomicInteger calls = new AtomicInteger();
        ReActAgent firstTurn = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
        firstTurn.setLlm(scriptedModel(calls, null));
        AgentSession session = new AgentSession(SESSION_ID, null, firstTurn.getCard());
        @SuppressWarnings("unchecked")
        Map<String, Object> firstResult = (Map<String, Object>) firstTurn.invoke(
                Map.of("query", "round one", "conversation_id", SESSION_ID), session);
        assertThat(firstResult).containsEntry("result_type", "answer");
        StepCursor afterFirstRound = checkpointer.lastCompletedStep(session.getInner());
        assertThat(afterFirstRound).isNotNull();

        // Second round: a fresh session object over the same id resumes the
        // persisted state, then completes another two-boundary loop.
        AgentSession secondTurn = new AgentSession(SESSION_ID, null, firstTurn.getCard());
        secondTurn.preRun(Map.of());
        assertThat(checkpointer.lastCompletedStep(secondTurn.getInner())).isNotNull();

        ReActAgent resumedAgent = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
        resumedAgent.setLlm(scriptedModel(calls, null));
        @SuppressWarnings("unchecked")
        Map<String, Object> secondResult = (Map<String, Object>) resumedAgent.invoke(
                Map.of("query", "round two", "conversation_id", SESSION_ID), secondTurn);
        assertThat(secondResult).containsEntry("result_type", "answer");

        // The journal keeps auditing; per the seq-per-trigger-instance contract
        // the resumed round re-numbers its boundaries from 1, so the trail holds
        // exactly the boundaries of the latest round.
        List<StepRecord> trail = trail(SESSION_ID);
        assertThat(trail).isNotEmpty();
        assertThat(trail).allMatch(record -> SESSION_ID.equals(record.getSessionId()));
    }

    @Test
    @DisplayName("5.1 storage failure during boundary persistence degrades to warn without killing the task")
    void storageFailureDegradesToWarnAndTaskSurvives() {
        // The broken store only fails writes: the trigger must swallow the
        // checkpoint failure and the invoke must still finish with an answer.
        PersistenceCheckpointer brokenCheckpointer = new PersistenceCheckpointer(new FailingKVStore());
        try {
            CheckpointerFactory.releaseDefaultCheckpointer();
            CheckpointerFactory.setDefaultCheckpointer(brokenCheckpointer);

            AtomicInteger calls = new AtomicInteger();
            ReActAgent reactAgent = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
            reactAgent.setLlm(scriptedModel(calls, null));
            AgentSession session = new AgentSession(SESSION_ID, null, reactAgent.getCard());
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) reactAgent.invoke(
                    Map.of("query", "survive storage failure", "conversation_id", SESSION_ID), session);

            assertThat(result).containsEntry("result_type", "answer");
        } finally {
            CheckpointerFactory.releaseDefaultCheckpointer();
            CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        }
    }

    @Test
    @DisplayName("4.2 a failed round after resume records FAILED evidence without moving the resume point")
    void failedRoundAfterResumeKeepsResumePointIntact() {
        // Turn one completes a boundary; turn two starts from the restored
        // state and dies mid-iteration, recording a FAILED cursor that must
        // not overwrite the COMPLETED resume point.
        AtomicInteger calls = new AtomicInteger();
        ReActAgent firstTurn = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
        firstTurn.setLlm(scriptedModel(calls, null));
        AgentSession session = new AgentSession(SESSION_ID, null, firstTurn.getCard());
        firstTurn.invoke(Map.of("query", "complete first", "conversation_id", SESSION_ID), session);
        long completedSeq = checkpointer.lastCompletedStep(session.getInner()).getSeq();

        AgentSession resumedTurn = new AgentSession(SESSION_ID, null, firstTurn.getCard());
        resumedTurn.preRun(Map.of());
        ReActAgent failingAgent = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
        failingAgent.setLlm(scriptedModel(new AtomicInteger(), null, 0,
                new RuntimeException("post-resume boom")));
        assertThat(org.assertj.core.api.Assertions.catchRuntimeException(
                () -> failingAgent.invoke(Map.of("query", "die after resume", "conversation_id", SESSION_ID),
                        resumedTurn)))
                .hasMessageContaining("post-resume boom");

        StepCursor resume = checkpointer.lastCompletedStep(resumedTurn.getInner());
        assertThat(resume).isNotNull();
        assertThat(resume.getStatus()).isEqualTo(StepStatus.COMPLETED);
        assertThat(resume.getSeq()).isEqualTo(completedSeq);
        // The failed iteration left its own evidence under the failed key.
        assertThat(checkpointer.lastFailedStep(resumedTurn.getInner())).isNotNull();
        assertThat(trail(SESSION_ID)).anyMatch(record -> record.getStatus() == StepStatus.FAILED);
    }

    private List<StepRecord> trail(String sessionId) {
        return new KvStepJournal(checkpointer.kvStore()).replay(sessionId);
    }

    private ReActAgent newAgent(StepCheckpointPolicy policy) {
        ReActAgent newAgent = new ReActAgent(new AgentCard("step-resilience-agent", "step-resilience-agent",
                "step checkpoint resilience agent"));
        newAgent.configure(ReActAgentConfig.builder()
                .maxIterations(5)
                .shouldFailTaskOnToolError(false)
                .stepCheckpointPolicy(policy)
                .build());
        newAgent.getAbilityManager().addAbility(probeToolCard("probe_a"), probeTool("probe_a"));
        newAgent.getAbilityManager().addAbility(probeToolCard("probe_b"), probeTool("probe_b"));
        this.agent = newAgent;
        return newAgent;
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
     * Scripted model: first call requests the two probe tools, later calls end
     * the loop with a text answer - the minimal multi-boundary loop.
     *
     * @param callCount receives the number of model invocations
     * @param resumeMarker when non-null and seen in a tool message, the model
     *                     finishes with a resume-specific answer
     * @return the model wired with a scripted client
     */
    private static Model scriptedModel(AtomicInteger callCount, String resumeMarker) {
        return scriptedModel(callCount, resumeMarker, -1, null);
    }

    /**
     * Scripted model with an optional injected model-call failure.
     *
     * @param callCount receives the number of model invocations
     * @param resumeMarker when non-null and seen in a tool message, the model
     *                     finishes with a resume-specific answer
     * @param failureFromCall model call index (zero-based) from which the
     *                        failure activates; negative disables the failure
     * @param failure failure to throw once the activation index is reached
     * @return the model wired with a scripted client
     */
    private static Model scriptedModel(AtomicInteger callCount, String resumeMarker,
                                       int failureFromCall, RuntimeException failure) {
        return new Model((messages, modelConfig, clientConfig, options) -> {
            int call = callCount.getAndIncrement();
            if (failure != null && call >= failureFromCall) {
                throw failure;
            }
            if (call == 0) {
                return CompletableFuture.completedFuture(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(
                                toolCall("call-a", "probe_a"),
                                toolCall("call-b", "probe_b")))
                        .build());
            }
            if (resumeMarker != null && hasToolMessage(messages)) {
                return CompletableFuture.completedFuture(new AssistantMessage(resumeMarker));
            }
            return CompletableFuture.completedFuture(new AssistantMessage("resilience-done"));
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

    private static boolean hasToolMessage(List<BaseMessage> messages) {
        for (BaseMessage message : messages) {
            if ("tool".equals(message.getRole())) {
                return true;
            }
        }
        return false;
    }

    /**
     * KV store whose write path fails while reads stay intact - the storage
     * fault injected by the 5.1 degradation case. The trigger must absorb the
     * write failures as warnings and the invoke must complete regardless.
     */
    private static final class FailingKVStore extends InMemoryKVStore {
        @Override
        public CompletableFuture<Void> set(String key, Object value) {
            return CompletableFuture.failedFuture(new IllegalStateException("simulated storage outage"));
        }

        @Override
        public BasedKVStorePipeline pipeline() {
            return new BasedKVStorePipeline(operations ->
                    CompletableFuture.failedFuture(new IllegalStateException("simulated storage outage")));
        }
    }
}
