/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop.callback;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Focused parity checks for loop callback slice T01168-T01170.
 *
 * <p>Mirrors Python's callback modules in
 * {@code openjiuwen/core/workflow/components/flow/loop/callback/loop_callback.py},
 * {@code openjiuwen/core/workflow/components/flow/loop/callback/intermediate_loop_var.py}, and
 * {@code openjiuwen/core/workflow/components/flow/loop/callback/output.py}.</p>
 */
class T01168T01170LoopCallbackTest {

    private static final String NODE_ID = "loopNode";

    public static void main(String[] args) {
        T01168T01170LoopCallbackTest test = new T01168T01170LoopCallbackTest();
        test.loopCallbackDispatchesUnknownStageToOutLoopAndStoresOutput();
        test.intermediateLoopVarWrapsInputsAndToleratesNullLoopTimes();
        test.intermediateLoopVarKeepsRootedNullWhenInputIsMissing();
        test.outputCallbackAccumulatesRoundsAndUsesLatestSelfReference();
        System.out.println("T01168T01170LoopCallbackTest passed");
    }

    @Test
    void loopCallbackDispatchesUnknownStageToOutLoopAndStoresOutput() {
        WorkflowRuntimeSession session = newSession(Map.of());
        RecordingLoopCallback callback = new RecordingLoopCallback();

        callback.call("unexpected_stage", session);

        assertEquals("out_loop", callback.lastStage);
        assertEquals(Map.of("stage", "out_loop"), session.state().getOutputs(NODE_ID));
    }

    @Test
    void intermediateLoopVarWrapsInputsAndToleratesNullLoopTimes() {
        WorkflowRuntimeSession session = newSession(Map.of("source", Map.of("answer", 42)));
        IntermediateLoopVarCallback callback = new IntermediateLoopVarCallback(
                Map.of("answer", "${source.answer}"),
                "locals");

        callback.call(LoopCallback.FIRST_LOOP, session);
        callback.call(LoopCallback.END_ROUND, session);

        assertEquals(Map.of("locals", Map.of("answer", 42)), session.state().getOutputs(NODE_ID));
    }

    @Test
    void intermediateLoopVarKeepsRootedNullWhenInputIsMissing() {
        WorkflowRuntimeSession session = newSession(Map.of());
        IntermediateLoopVarCallback callback = new IntermediateLoopVarCallback(
                Map.of("missing", "${source.missing}"),
                "locals");

        assertDoesNotThrow(() -> callback.call(LoopCallback.FIRST_LOOP, session));

        @SuppressWarnings("unchecked")
        Map<String, Object> outputs = (Map<String, Object>) session.state().getOutputs(NODE_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> locals = (Map<String, Object>) outputs.get("locals");
        assertNull(locals.get("missing"));
    }

    @Test
    void outputCallbackAccumulatesRoundsAndUsesLatestSelfReference() {
        Map<String, Object> outputsFormat = new LinkedHashMap<>();
        outputsFormat.put("final", "${" + NODE_ID + ".final}");
        outputsFormat.put("history", "${external.value}");
        OutputCallback callback = new OutputCallback(outputsFormat);
        WorkflowRuntimeSession session = newSession(Map.of("external", Map.of("value", "common")));

        callback.call(LoopCallback.FIRST_LOOP, session);
        putNodeOutput(session, Map.of("final", "first"));
        callback.call(LoopCallback.END_ROUND, session, 1);
        putNodeOutput(session, Map.of("final", "second"));
        callback.call(LoopCallback.END_ROUND, session, 2);
        callback.call(LoopCallback.OUT_LOOP, session);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("final", "second");
        expected.put("history", List.of("common", "common"));
        assertEquals(expected, session.state().getOutputs(NODE_ID));
    }

    private static WorkflowRuntimeSession newSession(Map<String, Object> ioState) {
        WorkflowRuntimeState state = WorkflowRuntimeState.from(
                InMemoryState.create(ioState, null, null, null, null)
        ).createNodeState(NODE_ID, "");
        return new WorkflowRuntimeSession("workflow", null, "session",
                state,
                null, "", "", NODE_ID, NODE_ID, 0);
    }

    private static void putNodeOutput(WorkflowRuntimeSession session, Map<String, Object> output) {
        session.state().setOutputs(output);
        session.state().commit();
    }

    private static final class RecordingLoopCallback extends LoopCallback {
        private String lastStage;

        @Override
        public Object firstInLoop(BaseSession session) {
            lastStage = "first_in_loop";
            return Map.of("stage", lastStage);
        }

        @Override
        public Object outLoop(BaseSession session) {
            lastStage = "out_loop";
            return Map.of("stage", lastStage);
        }

        @Override
        public Object startRound(BaseSession session) {
            lastStage = "start_round";
            return Map.of("stage", lastStage);
        }

        @Override
        public Object endRound(BaseSession session, Integer loopTimes) {
            lastStage = "end_round";
            return Map.of("stage", lastStage);
        }
    }
}
