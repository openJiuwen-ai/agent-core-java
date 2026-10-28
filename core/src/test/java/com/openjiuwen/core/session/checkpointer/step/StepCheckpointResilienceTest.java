/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.store.BaseKVStore;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resilience cases accepted from the step-checkpoint test-side review:
 * crash-boundary placement, restart reuse, empty-trail replay, multi-round
 * interrupt iterations, storage-failure degradation, post-resume exception
 * rounds, save-transaction atomicity under injected storage faults - all
 * through the real {@code ReActAgent.invoke} loop over a persistence-backed
 * checkpointer.
 */
class StepCheckpointResilienceTest {
    private static final String SESSION_ID = "step-resilience-session";

    private static final String AGENT_ID = "step-resilience-agent";

    private static final String AGENT_STATE_BLOB_KEY = SESSION_ID + ":agent:" + AGENT_ID + ":agent_state_blobs";

    private static final String AGENT_STATE_DUMP_TYPE_KEY = AGENT_STATE_BLOB_KEY + "_dump_type";

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
        StepCursor firstRound = checkpointer.lastCompletedStep(session.getInner());
        assertThat(resume).isNotNull();
        assertThat(resume.getStatus()).isEqualTo(StepStatus.COMPLETED);
        assertThat(resume.getSeq()).isEqualTo(firstRound.getSeq());
        // The failed iteration left its own evidence under the failed key.
        assertThat(checkpointer.lastFailedStep(resumedTurn.getInner())).isNotNull();
        assertThat(trail(SESSION_ID)).anyMatch(record -> record.getStatus() == StepStatus.FAILED);
    }

    @Test
    @DisplayName("3.16 crash mid-save leaves no half-written pair and the task reruns from scratch")
    void crashMidSaveLeavesNoHalfWrittenPair() {
        // The storage fault kills the boundary save transaction (the
        // two-write pipeline never commits), then the "process" restarts on
        // a healed store. The failed save must leave no half pair, and the
        // rerun starts from scratch because nothing was persisted.
        CrashInjectionKVStore crashStore = new CrashInjectionKVStore();
        PersistenceCheckpointer crashedCheckpointer = new PersistenceCheckpointer(crashStore);
        CheckpointerFactory.releaseDefaultCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(crashedCheckpointer);
        try {
            AtomicInteger calls = new AtomicInteger();
            ReActAgent reactAgent = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
            reactAgent.setLlm(scriptedModel(calls, null));
            AgentSession session = new AgentSession(SESSION_ID, null, reactAgent.getCard());
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) reactAgent.invoke(
                    Map.of("query", "crash mid save", "conversation_id", SESSION_ID), session);
            assertThat(result).containsEntry("result_type", "answer");

            // The fault killed the only boundary-save transaction; neither
            // of its two writes landed, so no half pair exists in the store.
            assertThat(crashStore.writePipelineFailures()).isEqualTo(1);
            assertThat(crashStore.writtenKeys())
                    .doesNotContain(AGENT_STATE_DUMP_TYPE_KEY, AGENT_STATE_BLOB_KEY);
            // The audit channel is independent of the state transaction, so
            // the boundary still left audit evidence behind.
            assertThat(trailOn(crashStore)).isNotEmpty();

            // "Restart" on the healed store: nothing was persisted, so the
            // resumed session has no cursor and the task must rerun fully.
            crashStore.markStorageHealed();
            AgentSession restarted = new AgentSession(SESSION_ID, null, reactAgent.getCard());
            restarted.preRun(Map.of());
            assertThat(crashedCheckpointer.lastCompletedStep(restarted.getInner())).isNull();

            ReActAgent rerunAgent = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
            rerunAgent.setLlm(scriptedModel(new AtomicInteger(), null));
            @SuppressWarnings("unchecked")
            Map<String, Object> rerun = (Map<String, Object>) rerunAgent.invoke(
                    Map.of("query", "rerun after crash", "conversation_id", SESSION_ID), restarted);
            assertThat(rerun).containsEntry("result_type", "answer");
            StepCursor rerunCursor = crashedCheckpointer.lastCompletedStep(restarted.getInner());
            assertThat(rerunCursor).isNotNull();
            assertThat(rerunCursor.getStatus()).isEqualTo(StepStatus.COMPLETED);
            assertThat(crashedCheckpointer.scanRunningSteps()).isEmpty();
        } finally {
            CheckpointerFactory.releaseDefaultCheckpointer();
            CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        }
    }

    @Test
    @DisplayName("3.16 a dump_type-only leftover is never consumed as a checkpoint")
    void dumpTypeOnlyLeftoverIsNeverResumed() {
        // Storage residue from a torn write: dump_type landed but the blob
        // never did. The recover path must treat the session as having no
        // state at all - a cursor cannot be restored from a missing blob.
        CrashInjectionKVStore crashStore = new CrashInjectionKVStore();
        crashStore.set(AGENT_STATE_DUMP_TYPE_KEY, "java").join();
        PersistenceCheckpointer crashedCheckpointer = new PersistenceCheckpointer(crashStore);
        CheckpointerFactory.releaseDefaultCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(crashedCheckpointer);
        try {
            // The session carries the same card as the crashed one so the
            // recover path reads the exact keys the residue was written to.
            AgentSession restarted = new AgentSession(SESSION_ID, null, probeAgentCard());
            restarted.preRun(Map.of());

            assertThat(crashedCheckpointer.lastCompletedStep(restarted.getInner())).isNull();
            assertThat(trailOn(crashStore)).isEmpty();
            assertThat(crashedCheckpointer.scanRunningSteps()).isEmpty();
        } finally {
            CheckpointerFactory.releaseDefaultCheckpointer();
            CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        }
    }

    @Test
    @DisplayName("3.17 crash before save restarts from the last persisted boundary")
    void crashBeforeSaveRestartsFromLastPersistedBoundary() {
        // Boundary seq=1 persists fine; the save for boundary seq=2 dies in
        // the write pipeline (step logic done, save transaction lost); the
        // failure-record flush also dies, then the invoke crashes. Restart
        // must resume from the seq=1 snapshot - the seq=2 step is treated as
        // not completed and will rerun.
        CrashInjectionKVStore crashStore = new CrashInjectionKVStore(1);
        PersistenceCheckpointer crashedCheckpointer = new PersistenceCheckpointer(crashStore);
        CheckpointerFactory.releaseDefaultCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(crashedCheckpointer);
        try {
            AtomicInteger calls = new AtomicInteger();
            ReActAgent firstTurn = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
            firstTurn.setLlm(twoToolRoundsThenBoomModel(calls, new RuntimeException("post-boundary boom")));
            AgentSession session = new AgentSession(SESSION_ID, null, firstTurn.getCard());
            assertThat(org.assertj.core.api.Assertions.catchRuntimeException(
                    () -> firstTurn.invoke(Map.of("query", "crash before save", "conversation_id", SESSION_ID),
                            session)))
                    .hasMessageContaining("post-boundary boom");

            // The seq=1 save committed before the fault armed; afterwards
            // every write pipeline (seq=2 save and the failure flush) fails,
            // leaving only the seq=1 snapshot behind - all or nothing.
            assertThat(crashStore.writePipelineFailures()).isEqualTo(2);
            assertThat(crashStore.writtenKeys()).contains(AGENT_STATE_DUMP_TYPE_KEY, AGENT_STATE_BLOB_KEY);

            // "Restart" on the healed store: the resumed session state must
            // carry the seq=1 cursor, because the seq=2 save never committed
            // and a cursor inside an uncommitted transaction is invisible.
            crashStore.markStorageHealed();
            AgentSession restarted = new AgentSession(SESSION_ID, null, firstTurn.getCard());
            restarted.preRun(Map.of());
            StepCursor resume = crashedCheckpointer.lastCompletedStep(restarted.getInner());
            assertThat(resume).isNotNull();
            assertThat(resume.getSeq()).isEqualTo(1L);
            assertThat(resume.getStepType()).isEqualTo("TOOL_CALL");
            assertThat(resume.getStatus()).isEqualTo(StepStatus.COMPLETED);
        } finally {
            CheckpointerFactory.releaseDefaultCheckpointer();
            CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        }
    }

    @Test
    @DisplayName("3.38 save-transaction-mid crash keeps the stale-cursor guard for the resumed round")
    void saveTransactionCrashKeepsStaleCursorGuard() {
        // The crash-timing matrix in one round: boundary seq=1 saves,
        // seq=2's save transaction dies mid-flight, and the invoke crashes.
        // The restarted round re-binds a fresh trigger whose seq restarts at
        // 1, so the seq=1 boundary of the resumed round must be rejected by
        // the stale-cursor guard (isAfter 1>1 is false) - no new cursor and
        // no save pipeline for it, while the audit journal still records it.
        CrashInjectionKVStore crashStore = new CrashInjectionKVStore(1);
        PersistenceCheckpointer crashedCheckpointer = new PersistenceCheckpointer(crashStore);
        CheckpointerFactory.releaseDefaultCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(crashedCheckpointer);
        try {
            AtomicInteger calls = new AtomicInteger();
            ReActAgent firstTurn = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
            firstTurn.setLlm(twoToolRoundsThenBoomModel(calls, new RuntimeException("matrix boom")));
            AgentSession session = new AgentSession(SESSION_ID, null, firstTurn.getCard());
            assertThat(org.assertj.core.api.Assertions.catchRuntimeException(
                    () -> firstTurn.invoke(Map.of("query", "matrix crash", "conversation_id", SESSION_ID), session)))
                    .hasMessageContaining("matrix boom");
            assertThat(crashStore.writePipelineFailures()).isEqualTo(2);

            // "Restart" on the healed store: the resumed round runs to
            // completion under the same checkpointer over the same store.
            crashStore.markStorageHealed();
            AgentSession restarted = new AgentSession(SESSION_ID, null, firstTurn.getCard());
            restarted.preRun(Map.of());
            ReActAgent resumedAgent = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
            resumedAgent.setLlm(scriptedModel(new AtomicInteger(), null));
            @SuppressWarnings("unchecked")
            Map<String, Object> resumedResult = (Map<String, Object>) resumedAgent.invoke(
                    Map.of("query", "resume after matrix crash", "conversation_id", SESSION_ID), restarted);
            assertThat(resumedResult).containsEntry("result_type", "answer");

            // The guard rejected the resumed round's seq=1 boundary: the
            // cursor stayed at the recovered seq=1 and no additional write
            // pipeline was issued for it.
            StepCursor resume = crashedCheckpointer.lastCompletedStep(restarted.getInner());
            assertThat(resume).isNotNull();
            assertThat(resume.getSeq()).isEqualTo(1L);
            assertThat(crashStore.writePipelineFailures()).isEqualTo(2);

            // The journal audited every boundary - including the ones whose
            // saves died - while the state side only ever advanced to the
            // last committed snapshot.
            List<StepRecord> trail = trailOn(crashStore);
            assertThat(trail).hasSize(3);
            assertThat(trail).anyMatch(record -> record.getStatus() == StepStatus.FAILED);
            assertThat(crashedCheckpointer.scanRunningSteps()).isEmpty();
        } finally {
            CheckpointerFactory.releaseDefaultCheckpointer();
            CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        }
    }

    private List<StepRecord> trail(String sessionId) {
        return new KvStepJournal(checkpointer.kvStore()).replay(sessionId);
    }

    private List<StepRecord> trailOn(BaseKVStore kvStore) {
        return new KvStepJournal(kvStore).replay(SESSION_ID);
    }

    /**
     * Scripted model driving two full tool rounds before dying: call 0 and
     * call 1 each request both probe tools, call 2 throws. Used to place a
     * boundary save between two completed boundaries so the crash lands
     * exactly at "step done, save transaction lost".
     *
     * @param callCount receives the number of model invocations
     * @param failure thrown once the two tool rounds are complete
     * @return the model wired with a scripted client
     */
    private static Model twoToolRoundsThenBoomModel(AtomicInteger callCount, RuntimeException failure) {
        return new Model((messages, modelConfig, clientConfig, options) -> {
            int call = callCount.getAndIncrement();
            if (call >= 2) {
                throw failure;
            }
            String round = call == 0 ? "a" : "b";
            return CompletableFuture.completedFuture(AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(
                            toolCall("call-" + round + "1", "probe_a"),
                            toolCall("call-" + round + "2", "probe_b")))
                    .build());
        });
    }

    private ReActAgent newAgent(StepCheckpointPolicy policy) {
        ReActAgent newAgent = new ReActAgent(probeAgentCard());
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

    private static AgentCard probeAgentCard() {
        return new AgentCard(AGENT_ID, AGENT_ID, "step checkpoint resilience agent");
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

    /**
     * KV store simulating a process crash at a controlled point of the save
     * transaction: write pipelines carrying at least one {@code set} fail as
     * a whole until the store is "healed" (the restart), while pure-read
     * pipelines and the direct {@code set} used by the audit journal keep
     * working throughout. This isolates the boundary-save transaction from
     * the audit channel and from post-restart traffic.
     *
     * <p>Two fault modes cover the crash-timing matrix:</p>
     * <ul>
     *   <li>default ({@code failFromWritePipelineCount == 0}): every write
     *   pipeline fails from the start - "crash before anything persisted";</li>
     *   <li>{@code failFromWritePipelineCount == N}: the first N write
     *   pipelines commit normally, the rest fail - "N saves committed, the
     *   next save's transaction dies mid-flight".</li>
     * </ul>
     */
    private static final class CrashInjectionKVStore extends InMemoryKVStore {
        private final int failFromWritePipelineCount;
        private final AtomicInteger writePipelines = new AtomicInteger();
        private final AtomicInteger writePipelineFailures = new AtomicInteger();
        private final Set<String> writtenKeys = ConcurrentHashMap.newKeySet();
        private volatile boolean isFaultArmed = true;

        /**
         * Create a store where every write pipeline fails (nothing ever
         * commits) until {@link #markStorageHealed()}.
         */
        CrashInjectionKVStore() {
            this(0);
        }

        /**
         * Create a store where the first {@code committedWritePipelines}
         * write pipelines succeed and every later one fails, until
         * {@link #markStorageHealed()}.
         *
         * @param committedWritePipelines number of write pipelines that
         *                                 commit before the fault arms
         */
        CrashInjectionKVStore(int committedWritePipelines) {
            this.failFromWritePipelineCount = committedWritePipelines;
        }

        @Override
        public CompletableFuture<Void> set(String key, Object value) {
            writtenKeys.add(key);
            return super.set(key, value);
        }

        @Override
        public BasedKVStorePipeline pipeline() {
            return new BasedKVStorePipeline(operations -> {
                if (isWritePipeline(operations)) {
                    int seen = writePipelines.incrementAndGet();
                    if (isFaultArmed && seen > failFromWritePipelineCount) {
                        writePipelineFailures.incrementAndGet();
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("simulated crash before commit"));
                    }
                    operations.stream()
                            .filter(operation -> "set".equals(operation.kind()))
                            .forEach(operation -> writtenKeys.add(operation.key()));
                }
                return replayOnDelegate(operations);
            });
        }

        /** Simulate the process restart: the storage fault clears. */
        void markStorageHealed() {
            isFaultArmed = false;
        }

        /**
         * Number of write pipelines that failed while the fault was armed.
         *
         * @return count of injected write-pipeline failures
         */
        int writePipelineFailures() {
            return writePipelineFailures.get();
        }

        /**
         * Keys touched by direct sets and by committed pipeline writes.
         *
         * @return snapshot of every key this store ever accepted a write for
         */
        Set<String> writtenKeys() {
            return Set.copyOf(writtenKeys);
        }

        private CompletableFuture<List<Object>> replayOnDelegate(
                List<BasedKVStorePipeline.PipelineOperation> operations) {
            BasedKVStorePipeline delegate = super.pipeline();
            for (BasedKVStorePipeline.PipelineOperation operation : operations) {
                switch (operation.kind()) {
                    case "set" -> delegate.set(operation.key(), operation.value(), operation.ttl()).join();
                    case "get" -> delegate.get(operation.key()).join();
                    case "exists" -> delegate.exists(operation.key()).join();
                    default -> throw new IllegalArgumentException("Unsupported pipeline op: " + operation.kind());
                }
            }
            return delegate.execute();
        }

        private static boolean isWritePipeline(List<BasedKVStorePipeline.PipelineOperation> operations) {
            return operations.stream().anyMatch(operation -> "set".equals(operation.kind()));
        }
    }
}
