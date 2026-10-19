/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end step-checkpoint resumption through the real ReActAgent loop.
 *
 * <p>Unlike {@link StepCheckpointTest}, which exercises the cursor and
 * backend primitives in isolation, these tests drive the full pipeline:</p>
 * <ol>
 *   <li>a scripted model client answers through the real
 *       {@code ReActAgent.invoke} main loop, including real tool
 *       execution of a {@link LocalFunction};</li>
 *   <li>the tool-call boundary writes the cursor through the wired
 *       {@code StepCheckpointTrigger} and the
 *       {@link PersistenceCheckpointer} flushes cursor + state in the
 *       same save;</li>
 *   <li>a fresh session for the same session id recovers the persisted
 *       state and the last cursor - the resumption contract of issue
 *       #195.</li>
 * </ol>
 */
class StepCheckpointEndToEndTest {

    private static final String SESSION_ID = "step-e2e-session";

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
    @DisplayName("tool-call boundaries persist cursor+state; a fresh session resumes both")
    void toolCallBoundariesSurviveSessionRestart() {
        AtomicInteger turnOneCalls = new AtomicInteger();
        ReActAgent agent = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
        agent.setLlm(scriptedModel(turnOneCalls, null));

        AgentSession firstTurn = new AgentSession(SESSION_ID, null, agent.getCard());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) agent.invoke(
                Map.of("query", "run the tools", "conversation_id", SESSION_ID), firstTurn);

        assertThat(result).containsEntry("result_type", "answer");
        assertThat(turnOneCalls.get()).isEqualTo(2);

        // The tool-call boundary must have persisted the cursor with the state.
        assertThat(checkpointer.lastCompletedStep(firstTurn.getInner())).isNotNull();
        assertThat(checkpointer.lastCompletedStep(firstTurn.getInner()).getStepType())
                .isEqualTo("TOOL_CALL");
        assertThat(checkpointer.lastCompletedStep(firstTurn.getInner()).getStatus())
                .isEqualTo(StepStatus.COMPLETED);

        // A brand-new session for the same id resumes state and cursor together:
        // the restart path is preRun -> preAgentExecute -> snapshot recover.
        AgentSession resumedTurn = new AgentSession(SESSION_ID, null, agent.getCard());
        resumedTurn.preRun(Map.of());
        StepCursor persisted = checkpointer.lastCompletedStep(resumedTurn.getInner());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getStepName()).isEqualTo("probe_a,probe_b");
        assertThat(persisted.getSeq()).isGreaterThanOrEqualTo(1L);

        // The journal audited every boundary even across restarts.
        KvStepJournal journal = new KvStepJournal(checkpointer.kvStore());
        List<StepRecord> trail = journal.replay(SESSION_ID);
        assertThat(trail).isNotEmpty();
        assertThat(trail).allMatch(record -> record.getSessionId().equals(SESSION_ID));
    }

    @Test
    @DisplayName("STEP_OFF skips persistence while the audit journal still records boundaries")
    void policyOffKeepsStateCleanButAudits() {
        ReActAgent agent = newAgent(StepCheckpointPolicy.STEP_OFF);
        AtomicInteger calls = new AtomicInteger();
        agent.setLlm(scriptedModel(calls, null));

        AgentSession session = new AgentSession(SESSION_ID, null, agent.getCard());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) agent.invoke(
                Map.of("query", "run without steps", "conversation_id", SESSION_ID), session);

        assertThat(result).containsEntry("result_type", "answer");
        assertThat(checkpointer.lastCompletedStep(session.getInner())).isNull();

        KvStepJournal journal = new KvStepJournal(checkpointer.kvStore());
        assertThat(journal.replay(SESSION_ID)).isNotEmpty();
    }

    @Test
    @DisplayName("invoke-level override STEP_OFF beats agent-level STEP_ALWAYS")
    void invokeOverrideBeatsAgentDefault() {
        ReActAgent agent = newAgent(StepCheckpointPolicy.STEP_ALWAYS);
        AtomicInteger calls = new AtomicInteger();
        agent.setLlm(scriptedModel(calls, null));

        AgentSession session = new AgentSession(SESSION_ID, null, agent.getCard());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) agent.invoke(
                Map.of("query", "override to off",
                        "conversation_id", SESSION_ID,
                        "step_checkpoint_policy", "STEP_OFF"),
                session);

        assertThat(result).containsEntry("result_type", "answer");
        assertThat(checkpointer.lastCompletedStep(session.getInner())).isNull();

        // Boundaries are still audited even though nothing was persisted.
        KvStepJournal journal = new KvStepJournal(checkpointer.kvStore());
        assertThat(journal.replay(SESSION_ID)).isNotEmpty();
    }

    @Test
    @DisplayName("AUTO activates late after the completed-step threshold (D1 signal)")
    void autoPolicyActivatesAfterCompletedStepThreshold() {
        ReActAgent agent = newAgent(StepCheckpointPolicy.STEP_AUTO);
        AtomicInteger calls = new AtomicInteger();
        agent.setLlm(scriptedModel(calls, null));

        AgentSession session = new AgentSession(SESSION_ID, null, agent.getCard());
        agent.invoke(Map.of("query", "drive auto policy", "conversation_id", SESSION_ID), session);

        // Two tool-call boundaries are below the AUTO D1 threshold (3), so no
        // step cursor is persisted, but the boundaries were observed.
        assertThat(calls.get()).isEqualTo(2);
        assertThat(checkpointer.lastCompletedStep(session.getInner())).isNull();
    }

    @Test
    @DisplayName("scanRunningSteps finds a RUNNING cursor left behind and recovery marks it lost")
    void runningCursorIsScannedAndRecoverable() {
        // Persist a RUNNING cursor directly: the crash residue this pipeline exists for.
        AgentSession crashed = new AgentSession(SESSION_ID, null, null);
        checkpointer.preAgentExecute(crashed.getInner(), null);
        StepCursor leftover = new StepCursor(9L, "TOOL_CALL", "probe_a", StepStatus.RUNNING, 777L);
        checkpointer.stepCompleted(crashed.getInner(), leftover);

        List<StepCursor> running = checkpointer.scanRunningSteps();
        assertThat(running).hasSize(1);
        assertThat(running.get(0).getSeq()).isEqualTo(9L);

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        List<StepCheckpointRecoveryService.RecoveryAction> actions = service.recoverLeftovers();
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).type())
                .isEqualTo(StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST);

        // A second scan is idempotent within the same process.
        List<StepCheckpointRecoveryService.RecoveryAction> again = service.recoverLeftovers();
        assertThat(again).hasSize(1);
        assertThat(again.get(0).type())
                .isEqualTo(StepCheckpointRecoveryService.RecoveryActionType.SKIPPED_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("recovery hook takes priority over markLost for a scanned RUNNING cursor")
    void recoveryHookTakesPriorityOverMarkLost() {
        AgentSession crashed = new AgentSession(SESSION_ID, null, null);
        checkpointer.preAgentExecute(crashed.getInner(), null);
        checkpointer.stepCompleted(crashed.getInner(),
                new StepCursor(4L, "PHASE", "round-4", StepStatus.RUNNING, 555L));

        assertThat(checkpointer.scanRunningSteps()).hasSize(1);

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer,
                request -> StepRecoveryHook.RecoveryResult.recovered("requeued"));
        List<StepCheckpointRecoveryService.RecoveryAction> actions = service.recoverLeftovers();
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).type())
                .isEqualTo(StepCheckpointRecoveryService.RecoveryActionType.RECOVERED_BY_HOOK);
        assertThat(actions.get(0).detail()).isEqualTo("requeued");
    }

    private ReActAgent newAgent(StepCheckpointPolicy policy) {
        ReActAgent newAgent = new ReActAgent(new AgentCard("step-e2e-agent", "step-e2e-agent",
                "step checkpoint e2e agent"));
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
     * Scripted model: first call requests the two probe tools, later calls
     * end the loop with a text answer - the minimal multi-boundary loop.
     *
     * @param callCount receives the number of model invocations
     * @param resumeMarker when non-null and seen in a tool message, the
     *                     model finishes with a resume-specific answer
     * @return the model wired with a scripted client
     */
    private static Model scriptedModel(AtomicInteger callCount, String resumeMarker) {
        return new Model((messages, modelConfig, clientConfig, options) -> {
            int call = callCount.getAndIncrement();
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
            return CompletableFuture.completedFuture(new AssistantMessage("e2e-done"));
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
}
