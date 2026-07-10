/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.components.flow.EndComponent;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity checks for Python-visible loop terminal output behavior.
 */
public class LoopRuntimePythonParityTest {

    @Test
    void terminalLoopIndexUsesPostIterationCounterAndDoesNotLeakInternalLoopState() {
        Workflow workflow = new Workflow();
        workflow.setStartComp("start", new StartComponent(), Map.of("num", "${num}"), null);

        LoopGroup loopGroup = new LoopGroup();
        loopGroup.addWorkflowComp("loop_1", new AddTenComponent(), Map.of("source", "${loop.index}"));
        loopGroup.startNodes(List.of("loop_1"));
        loopGroup.endNodes(List.of("loop_1"));

        LoopComponentImpl loop = new LoopComponentImpl(loopGroup,
                Map.of("l_index", "${loop.index}", "l_out1", "${loop_1.result}"));
        workflow.addWorkflowComp("loop", loop,
                Map.of("loop_type", "number", "loop_number", "${start.num}"), null);
        workflow.setEndComp("end", new EndComponent(), Map.of("end_out", "${loop}"), null);
        workflow.addConnection("start", "loop");
        workflow.addConnection("loop", "end");

        WorkflowOutput output = workflow.invoke(Map.of("num", 3), new WorkflowSession(), null);

        Map<String, Object> endOut = toMap(toMap(toMap(output.getResult()).get("output")).get("end_out"));
        assertThat(endOut).containsEntry("index", 0);
        assertThat(endOut).containsEntry("l_index", 3);
        assertThat(endOut).containsEntry("l_out1", List.of(10, 11, 12));
        assertThat(endOut).doesNotContainKey("loop");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object value) {
        return new LinkedHashMap<>((Map<String, Object>) value);
    }

    private static final class AddTenComponent extends WorkflowComponent<Object, Object> {
        @Override
        public Object invoke(Object inputs, BaseSession session, ModelContext context) {
            Number source = (Number) toMap(inputs).get("source");
            return Map.of("result", source.intValue() + 10);
        }
    }
}
