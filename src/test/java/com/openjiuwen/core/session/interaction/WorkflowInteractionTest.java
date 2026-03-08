/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WorkflowInteraction}.
 */
class WorkflowInteractionTest {

    @Test
    @DisplayName("workflow interaction consumes raw workflow input before interrupting")
    void testWaitUserInputsConsumesWorkflowInput() {
        WorkflowCommitState state = InMemoryState.create(null, null, null,
                Map.of(Constant.INTERACTIVE_INPUT, "resume-value"), null);
        WorkflowSession session = new WorkflowSession("workflow-1", null, "session-1", state, null);

        WorkflowInteraction interaction = new WorkflowInteraction(session);

        assertEquals("resume-value", interaction.waitUserInputs("ignored"));
        assertNull(((WorkflowStateCollection) session.state()).getWorkflow(Constant.INTERACTIVE_INPUT));
    }

    @Test
    @DisplayName("workflow interaction emits output and throws graph interrupt when input is absent")
    void testWaitUserInputsInterruptsAndWritesOutput() {
        WorkflowSession workflowSession = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        StreamWriterManager writerManager = new StreamWriterManager(new StreamEmitter());
        workflowSession.setStreamWriterManager(writerManager);
        NodeSession nodeSession = new NodeSession(workflowSession, "ask_user");

        WorkflowInteraction interaction = new WorkflowInteraction(nodeSession);

        WorkflowInteraction.GraphInterruptRuntimeWrapper error = assertThrows(
                WorkflowInteraction.GraphInterruptRuntimeWrapper.class,
                () -> interaction.waitUserInputs("Please enter a value"));

        Object emitted = writerManager.getStreamEmitter().getStreamQueue().receive(500);
        assertInstanceOf(OutputSchema.class, emitted);

        OutputSchema chunk = (OutputSchema) emitted;
        assertEquals(Constant.INTERACTION, chunk.getType());
        assertEquals(0, chunk.getIndex());
        assertInstanceOf(InteractionOutput.class, chunk.getPayload());

        InteractionOutput output = (InteractionOutput) chunk.getPayload();
        assertEquals("ask_user", output.getId());
        assertEquals("Please enter a value", output.getValue());

        GraphInterrupt graphInterrupt = error.getGraphInterrupt();
        @SuppressWarnings("unchecked")
        Map<String, Object> interruptPayload = (Map<String, Object>) graphInterrupt.getValue().getValue();
        assertEquals(Constant.INTERACTION, interruptPayload.get("type"));
        assertEquals(0, interruptPayload.get("index"));
        assertSame(output, interruptPayload.get("payload"));
    }
}
