/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies nested loop inputs and aggregate preservation across pause and resume.
 *
 * @since 2026-10-20
 */
class NestedLoopResumeRegressionTest {
    @Test
    void loopReadsEnclosingNodeInputsAcrossPauseAndResume() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("question", "${question}"), null);
        LoopGroup group = new LoopGroup();
        AtomicInteger executions = new AtomicInteger();
        group.addWorkflowComp("before", new CountRound(executions), true, Map.of(), null, null, null, null);
        group.addWorkflowComp("ask", new Ask(), true, Map.of("round", "${start.question}"),
                null, null, null, null);
        group.startNodes(List.of("before"));
        group.endNodes(List.of("ask"));
        group.addConnection("before", "ask");
        flow.addWorkflowComp("loop", new LoopComponentImpl(group, Map.of("answers", "${ask}")),
                Map.of("loop_type", "number", "loop_number", 2), null);
        flow.setEndComp("end", new End(), Map.of("rounds", "${loop}"), null);
        flow.addConnection("start", "loop");
        flow.addConnection("loop", "end");
        String id = UUID.randomUUID().toString();
        WorkflowOutput first = flow.invoke(Map.of("question", "outer prompt"), session(id), null);
        assertLoopQuestion(first);
        assertEquals(1, executions.get());
        InteractiveInput input = new InteractiveInput();
        input.update("loop.ask", "first answer");
        assertLoopQuestion(flow.invoke(input, session(id), null));
        assertEquals(2, executions.get());
        InteractiveInput second = new InteractiveInput();
        second.update("loop.ask", "second answer");
        WorkflowOutput completed = flow.invoke(second, session(id), null);
        assertEquals(WorkflowExecutionState.COMPLETED, completed.getState());
        assertEquals(2, executions.get());
        Map<?, ?> envelope = assertInstanceOf(Map.class, completed.getResult());
        Map<?, ?> output = assertInstanceOf(Map.class, envelope.get("output"));
        Map<?, ?> rounds = assertInstanceOf(Map.class, output.get("rounds"));
        assertEquals(List.of(Map.of("answer", "first answer"), Map.of("answer", "second answer")),
                rounds.get("answers"));
    }

    private static void assertLoopQuestion(WorkflowOutput output) {
        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, output.getState());
        List<?> frames = assertInstanceOf(List.class, output.getResult());
        OutputSchema frame = assertInstanceOf(OutputSchema.class, frames.get(0));
        InteractionOutput pending = assertInstanceOf(InteractionOutput.class, frame.getPayload());
        assertEquals("loop.ask", pending.getId());
        assertEquals("outer prompt", pending.getValue());
    }

    @Test
    void restoresSessionWithInputsRoundsAndSameNamedAggregates() {
        Workflow flow = outerFlow();
        String sessionId = UUID.randomUUID().toString();
        WorkflowOutput result = flow.invoke(Map.of(), session(sessionId), null);
        assertPending(result, 0);
        result = flow.invoke(resume("first"), session(sessionId), null);
        assertPending(result, 0);
        result = flow.invoke(resume("second"), session(sessionId), null);
        assertPending(result, 1);
        result = flow.invoke(resume("third"), session(sessionId), null);
        assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
        Map<?, ?> envelope = assertInstanceOf(Map.class, result.getResult());
        Map<?, ?> output = assertInstanceOf(Map.class, envelope.get("output"));
        Map<?, ?> rounds = assertInstanceOf(Map.class, output.get("rounds"));
        List<?> members = assertInstanceOf(List.class, rounds.get("items"));
        assertEquals(3, members.size());
        Map<?, ?> last = assertInstanceOf(Map.class, members.get(2));
        Map<?, ?> inner = assertInstanceOf(Map.class, last.get("result"));
        assertEquals(List.of(Map.of("answer", "second"), Map.of("answer", "third")), inner.get("ask"));
    }

    private static WorkflowSession session(String id) {
        return new WorkflowSession(id);
    }

    private static InteractiveInput resume(String answer) {
        InteractiveInput input = new InteractiveInput();
        input.update("outer.child.inner.ask", answer);
        return input;
    }

    private static void assertPending(WorkflowOutput output, int expectedRound) {
        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, output.getState());
        List<?> frames = assertInstanceOf(List.class, output.getResult());
        assertEquals(1, frames.size());
        OutputSchema frame = assertInstanceOf(OutputSchema.class, frames.get(0));
        InteractionOutput pending = assertInstanceOf(InteractionOutput.class, frame.getPayload());
        assertEquals("outer.child.inner.ask", pending.getId());
        assertEquals(expectedRound, pending.getValue());
    }

    private static Workflow outerFlow() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of(), null);
        LoopGroup group = new LoopGroup();
        group.addWorkflowComp("child", new SubWorkflowComponentImpl(innerFlow()), true,
                Map.of("count", "${outer.index}"), null, null, null, null);
        group.startNodes(List.of("child"));
        group.endNodes(List.of("child"));
        flow.addWorkflowComp("outer", new LoopComponentImpl(group, Map.of("items", "${child.output}")),
                Map.of("loop_type", "number", "loop_number", 3), null);
        flow.setEndComp("end", new End(), Map.of("rounds", "${outer}"), null);
        flow.addConnection("start", "outer");
        flow.addConnection("outer", "end");
        return flow;
    }

    private static Workflow innerFlow() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("count", "${count}"), null);
        LoopGroup group = new LoopGroup();
        group.addWorkflowComp("ask", new Ask(), true, Map.of("round", "${inner.index}"),
                null, null, null, null);
        group.startNodes(List.of("ask"));
        group.endNodes(List.of("ask"));
        flow.addWorkflowComp("inner", new LoopComponentImpl(group, Map.of("ask", "${ask}")),
                Map.of("loop_type", "number", "loop_number", "${start.count}"), null);
        flow.setEndComp("end", new End(), Map.of("result", "${inner}"), null);
        flow.addConnection("start", "inner");
        flow.addConnection("inner", "end");
        return flow;
    }

    private static final class Ask extends WorkflowComponent {
        @Override
        public Object invoke(Object input, NodeSessionApi session, ModelContext context) {
            Map<?, ?> values = assertInstanceOf(Map.class, input);
            return Map.of("answer", session.interact(values.get("round")));
        }
    }

    private static final class CountRound extends WorkflowComponent {
        private final AtomicInteger executions;

        private CountRound(AtomicInteger executions) {
            this.executions = executions;
        }

        @Override
        public Object invoke(Object input, NodeSessionApi session, ModelContext context) {
            return Map.of("round", executions.incrementAndGet());
        }
    }
}
