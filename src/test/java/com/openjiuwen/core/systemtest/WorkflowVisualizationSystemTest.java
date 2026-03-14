/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workflow visualization system tests aligned with Python's draw/to_mermaid behavior.
 * These tests are local-only and verify the production Mermaid generation path.
 */
@Tag("system-test")
class WorkflowVisualizationSystemTest {

    static class PassthroughComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    @Test
    @DisplayName("Workflow.draw returns Mermaid for a linear workflow")
    void testDrawLinearWorkflow() {
        String mermaid = withWorkflowDrawableEnabled(() -> {
            Workflow flow = new Workflow();
            flow.setStartComp("start", new Start(), Map.of("value", "${value}"), null);
            flow.addWorkflowComp("process", new PassthroughComponent(),
                    Map.of("value", "${start.value}"), null);
            flow.setEndComp("end", new PassthroughComponent(),
                    Map.of("result", "${process.value}"), null);
            flow.addConnection("start", "process");
            flow.addConnection("process", "end");
            return flow.draw("linear-workflow", "mermaid", false);
        });

        assertTrue(mermaid.contains("title: linear-workflow"));
        assertTrue(mermaid.contains("flowchart TD"));
        assertTrue(mermaid.contains("\"start\""));
        assertTrue(mermaid.contains("\"process\""));
        assertTrue(mermaid.contains("\"end\""));
        assertTrue(mermaid.contains("-->"));
    }

    @Test
    @DisplayName("Workflow.draw expands sub-workflow nodes when requested")
    void testDrawExpandedSubWorkflow() {
        String expanded = withWorkflowDrawableEnabled(() -> {
            Workflow innerFlow = new Workflow();
            innerFlow.setStartComp("inner_start", new Start(), Map.of("value", "${value}"), null);
            innerFlow.setEndComp("inner_end", new PassthroughComponent(),
                    Map.of("result", "${inner_start.value}"), null);
            innerFlow.addConnection("inner_start", "inner_end");

            Workflow outerFlow = new Workflow();
            outerFlow.setStartComp("start", new Start(), Map.of("value", "${value}"), null);
            outerFlow.addWorkflowComp("sub", new SubWorkflowComponentImpl(innerFlow),
                    Map.of("value", "${start.value}"), null);
            outerFlow.setEndComp("end", new PassthroughComponent(),
                    Map.of("result", "${sub.result}"), null);
            outerFlow.addConnection("start", "sub");
            outerFlow.addConnection("sub", "end");
            return outerFlow.draw("nested", "mermaid", true);
        });

        assertTrue(expanded.contains("subgraph"));
        assertTrue(expanded.contains("\"sub\""));
        assertTrue(expanded.contains("\"inner_start\""));
        assertTrue(expanded.contains("\"inner_end\""));
    }

    @Test
    @DisplayName("Workflow.draw includes conditional and streaming edges")
    void testDrawConditionalAndStreamingEdges() {
        String mermaid = withWorkflowDrawableEnabled(() -> {
            Workflow flow = new Workflow();
            flow.setStartComp("start", new Start(), Map.of("value", "${value}"), null);

            BranchComponent branch = new BranchComponent();
            branch.addBranch("${value} > 10", List.of("stream_node"), "high");
            branch.addBranch("${value} <= 10", List.of("fallback"), "low");

            flow.addWorkflowComp("switch", branch);
            flow.addWorkflowComp("stream_node", new PassthroughComponent());
            flow.addWorkflowComp("fallback", new PassthroughComponent());
            flow.setEndComp("end", new PassthroughComponent(), null, null);

            flow.addConnection("start", "switch");
            flow.addStreamConnection("stream_node", "end");
            flow.addConnection("fallback", "end");

            return flow.draw("conditional-stream", "mermaid", false);
        });

        assertTrue(mermaid.contains("-.->"), "Conditional edges should render as dotted Mermaid links");
        assertTrue(mermaid.contains("==>"), "Streaming edges should render as thick Mermaid links");
        assertTrue(mermaid.contains("${value} > 10"));
        assertTrue(mermaid.contains("${value} <= 10"));
        assertFalse(mermaid.isBlank());
    }

    @Test
    @DisplayName("Workflow.draw exposes animation metadata for streaming edges")
    void testDrawStreamingAnimation() {
        String mermaid = withWorkflowDrawableEnabled(() -> {
            Workflow flow = new Workflow();
            flow.setStartComp("start", new Start(), Map.of("value", "${value}"), null);
            flow.addWorkflowComp("stream_node", new PassthroughComponent());
            flow.setEndComp("end", new PassthroughComponent(), null, null);
            flow.addConnection("start", "stream_node");
            flow.addStreamConnection("stream_node", "end");
            return flow.draw("animated-stream", "mermaid", false, true);
        });

        assertTrue(mermaid.contains("animate: true"));
        assertTrue(mermaid.contains("link_"));
    }

    private <T> T withWorkflowDrawableEnabled(Supplier<T> action) {
        String previous = System.getProperty("WORKFLOW_DRAWABLE");
        System.setProperty("WORKFLOW_DRAWABLE", "true");
        try {
            return action.get();
        } finally {
            if (previous == null) {
                System.clearProperty("WORKFLOW_DRAWABLE");
            } else {
                System.setProperty("WORKFLOW_DRAWABLE", previous);
            }
        }
    }
}
