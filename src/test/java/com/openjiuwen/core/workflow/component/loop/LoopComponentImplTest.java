/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopComponentImplTest {

    @Test
    void rejectsFractionalLoopNumber() {
        BaseError error = assertThrows(BaseError.class, () -> invokeFlow(5.5, baseInputs()));
        assertLoopNumberValidationError(error);
    }

    @Test
    void rejectsNonNumericLoopNumberString() {
        BaseError error = assertThrows(BaseError.class, () -> invokeFlow("a", baseInputs()));
        assertLoopNumberValidationError(error);
    }

    @Test
    void rejectsMissingLoopNumberReference() {
        BaseError error = assertThrows(BaseError.class, () -> invokeFlow("${start.count}", baseInputs()));
        assertEquals(StatusCode.COMPONENT_LOOP_EXECUTION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("loop_number variable not found"));
    }

    private static void assertLoopNumberValidationError(BaseError error) {
        assertEquals(StatusCode.COMPONENT_LOOP_EXECUTION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("loop_number"));
    }

    private static void invokeFlow(Object loopNumber, Map<String, Object> inputs) {
        Workflow flow = buildFlow(loopNumber);
        flow.invoke(inputs, new WorkflowSessionApi(null, UUID.randomUUID().toString(), Map.of()), null);
    }

    private static Workflow buildFlow(Object loopNumber) {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("input_arr", "${array}", "input_num", "${num}"), null);
        flow.setEndComp("end", new End(), Map.of("end_out", "${loop}"), null, null, null, null);

        LoopGroup loopGroup = new LoopGroup();
        loopGroup.addWorkflowComp("loop_1", new AddTenNode(), true,
                Map.of("source", "${loop.index}"), null, null, null, null);
        loopGroup.addWorkflowComp("loop_2", new AddTenNode(), true,
                Map.of("source", "${loop.user_num}"), null, null, null, null);
        loopGroup.addWorkflowComp("loop_3",
                new LoopSetVariableComponent(Map.of("${loop.user_num}", "${loop_2.result}")),
                true, null, null, null, null, null);
        loopGroup.startNodes(List.of("loop_1"));
        loopGroup.endNodes(List.of("loop_3"));
        loopGroup.addConnection("loop_1", "loop_2");
        loopGroup.addConnection("loop_2", "loop_3");

        LoopComponentImpl loopComponent = new LoopComponentImpl(
                loopGroup,
                Map.of("l_out1", "${loop_1.result}", "l_out2", "${loop_2.result}"));
        flow.addWorkflowComp("loop", loopComponent, Map.of(
                "loop_type", "number",
                "loop_number", loopNumber,
                "intermediate_var", Map.of("user_num", "${start.input_num}")), null);

        flow.addConnection("start", "loop");
        flow.addConnection("loop", "end");
        return flow;
    }

    private static Map<String, Object> baseInputs() {
        return Map.of("array", List.of(4, 5, 6), "num", -3);
    }

    private static final class AddTenNode extends com.openjiuwen.core.workflow.WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            Object source = inputMap.get("source");
            if (source == null) {
                return Map.of("result", 10);
            }
            if (source instanceof Number number) {
                return Map.of("result", number.intValue() + 10);
            }
            return Map.of("result", Integer.parseInt(String.valueOf(source)) + 10);
        }
    }
}