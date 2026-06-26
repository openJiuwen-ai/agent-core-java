/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.pregel.Interrupt;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.InMemoryCommitState;
import com.openjiuwen.core.session.state.InMemoryStateLike;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests interaction interrupt and resume behavior.
 *
 * <p>Mirrors Python's {@code InteractionOutput}, {@code WorkflowInteraction},
 * {@code SimpleAgentInteraction}, and {@code AgentInteraction} in
 * {@code openjiuwen/core/session/interaction/interaction.py}.</p>
 */
class InteractionTest {

    @Test
    void interactionOutputStoresIdAndValue() {
        InteractionOutput output = new InteractionOutput("node-a", "question");

        assertEquals("node-a", output.getId());
        assertEquals("question", output.getValue());
    }

    @Test
    void workflowInteractionConsumesWorkflowStateInputAndClearsIt() {
        WorkflowCommitState state = workflowState();
        state.updateAndCommitWorkflowState(Map.of(Constant.INTERACTIVE_INPUT, "resume-input"));
        TestSession session = new TestSession(state, "node-a", null);

        WorkflowInteraction interaction = new WorkflowInteraction(session);

        assertNull(state.getWorkflowState(Constant.INTERACTIVE_INPUT));
        assertEquals("resume-input", interaction.waitUserInputs("question"));
    }

    @Test
    void workflowInteractionThrowsGraphInterruptWithInteractionOutput() {
        WorkflowInteraction interaction = new WorkflowInteraction(new TestSession(workflowState(), "node-a", null));

        GraphInterrupt interrupt = assertThrows(GraphInterrupt.class,
                () -> interaction.waitUserInputs("question"));

        OutputSchema schema = interruptSchema(interrupt);
        assertEquals(Constant.INTERACTION, schema.getType());
        assertEquals(0, schema.getIndex());
        InteractionOutput payload = assertInstanceOf(InteractionOutput.class, schema.getPayload());
        assertEquals("node-a", payload.getId());
        assertEquals("question", payload.getValue());
    }

    @Test
    void workflowLatestInputReturnsOnceThenInterrupts() {
        WorkflowCommitState state = workflowState();
        state.updateAndCommitWorkflowState(Map.of(Constant.INTERACTIVE_INPUT, "latest-input"));
        WorkflowInteraction interaction = new WorkflowInteraction(new TestSession(state, "node-a", null));

        assertEquals("latest-input", interaction.userLatestInput("question"));
        GraphInterrupt interrupt = assertThrows(GraphInterrupt.class,
                () -> interaction.userLatestInput("question"));

        OutputSchema schema = interruptSchema(interrupt);
        assertEquals(Constant.INTERACTION, schema.getType());
        assertEquals(Arrays.asList("node-a", null), schema.getPayload());
    }

    @Test
    void agentInteractionConsumesStoredInputBeforeInterrupt() {
        AgentStateCollection state = new AgentStateCollection();
        state.update(Map.of(Constant.INTERACTIVE_INPUT, List.of("stored-input")));
        CountingCheckpointer checkpointer = new CountingCheckpointer();
        AgentInteraction interaction = new AgentInteraction(new TestSession(state, "agent-a", checkpointer));

        assertEquals("stored-input", interaction.waitUserInputs("question"));
        assertEquals(0, checkpointer.interruptCalls);

        IllegalArgumentException interrupt = assertThrows(IllegalArgumentException.class,
                () -> interaction.waitUserInputs("question"));
        assertEquals("AgentInterrupt.__init__() missing 1 required positional argument: 'message'",
                interrupt.getMessage());
        assertEquals(1, checkpointer.interruptCalls);
    }

    @Test
    void simpleAgentInteractionInterruptsAndPreservesMessage() {
        CountingCheckpointer checkpointer = new CountingCheckpointer();
        TestSession session = new TestSession(new AgentStateCollection(), "agent-a", checkpointer);
        SimpleAgentInteraction interaction = new SimpleAgentInteraction(session);

        AgentInterrupt interrupt = assertThrows(AgentInterrupt.class,
                () -> interaction.waitUserInputs("need input"));

        assertEquals("need input", interrupt.message);
        assertEquals(1, checkpointer.interruptCalls);
        assertSame(session, checkpointer.lastSession);
    }

    private static OutputSchema interruptSchema(GraphInterrupt interrupt) {
        List<?> interrupts = assertInstanceOf(List.class, interrupt.getValue());
        Interrupt wrapped = assertInstanceOf(Interrupt.class, interrupts.get(0));
        return assertInstanceOf(OutputSchema.class, wrapped.getValue());
    }

    private static WorkflowCommitState workflowState() {
        return new WorkflowCommitState(
                new InMemoryCommitState(new InMemoryStateLike()),
                new InMemoryCommitState(new InMemoryStateLike()),
                new InMemoryCommitState(new InMemoryStateLike()),
                new InMemoryCommitState(new InMemoryStateLike()),
                new HashMap<>(),
                "",
                "node-a");
    }

    private static final class CountingCheckpointer extends Checkpointer {
        private int interruptCalls;
        private BaseSession lastSession;

        @Override
        public void interruptAgentExecute(BaseSession session) {
            interruptCalls++;
            lastSession = session;
        }
    }

    private static final class TestSession extends BaseSession {

        private final SessionStateAccess state;
        private final String executableId;
        private final Checkpointer checkpointer;

        private TestSession(SessionStateAccess state, String executableId, Checkpointer checkpointer) {
            this.state = state;
            this.executableId = executableId;
            this.checkpointer = checkpointer;
        }

        @Override
        public SessionStateAccess state() {
            return state;
        }

        @Override
        public Object checkpointer() {
            return checkpointer;
        }

        public String executableId() {
            return executableId;
        }
    }
}
