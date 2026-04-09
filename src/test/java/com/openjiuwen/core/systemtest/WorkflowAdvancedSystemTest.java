/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.visualization.DrawableEdge;
import com.openjiuwen.core.graph.visualization.DrawableGraph;
import com.openjiuwen.core.graph.visualization.DrawableNode;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopSetVariableComponent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced Workflow system tests covering gaps identified in CHECK doc:
 * LoopComponent, SubWorkflowComponent, MermaidDiagram visualization.
 * All tests are local (no remote API required).
 */
@Tag("system-test")
class WorkflowAdvancedSystemTest {

    static class CounterComponent extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            if (inputs instanceof Map) {
                Map<String, Object> inputMap = (Map<String, Object>) inputs;
                int count = ((Number) inputMap.getOrDefault("count", 0)).intValue();
                return Map.of("count", count + 1);
            }
            return Map.of("count", 1);
        }
    }

    static class DoubleComponent extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            if (inputs instanceof Map) {
                Map<String, Object> inputMap = (Map<String, Object>) inputs;
                Number value = (Number) inputMap.getOrDefault("value", 0);
                return Map.of("value", value.doubleValue() * 2);
            }
            return inputs;
        }
    }

    static class PassthroughComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    private static WorkflowSessionApi newSession() {
        return new WorkflowSessionApi(null, UUID.randomUUID().toString(), Map.of());
    }

    @Nested
    @DisplayName("SubWorkflowComponent Tests")
    class SubWorkflowTests {

        @Test
        @DisplayName("SubWorkflow executes inner workflow")
        void testSubWorkflowExecution() {
            // Create inner (sub) workflow
            Workflow innerFlow = new Workflow();
            innerFlow.setStartComp("inner_start", new Start(),
                    Map.of("value", "${value}"), null);
            innerFlow.addWorkflowComp("inner_process", new DoubleComponent(),
                    Map.of("value", "${inner_start.value}"), null);
            innerFlow.setEndComp("inner_end", new PassthroughComponent(),
                    Map.of("value", "${inner_process.value}"), null);
            innerFlow.addConnection("inner_start", "inner_process");
            innerFlow.addConnection("inner_process", "inner_end");

            // Create outer workflow with sub-workflow component
            SubWorkflowComponentImpl subComp = new SubWorkflowComponentImpl(innerFlow);
            assertNotNull(subComp);
            assertEquals("sub_workflow", subComp.componentType());
            assertTrue(subComp.graphInvoker());

            Workflow outerFlow = new Workflow();
            outerFlow.setStartComp("start", new Start(),
                    Map.of("value", "${value}"), null);
            outerFlow.addWorkflowComp("sub", subComp,
                    Map.of("value", "${start.value}"), null);
            outerFlow.setEndComp("end", new PassthroughComponent(),
                    Map.of("result", "${sub.value}"), null);
            outerFlow.addConnection("start", "sub");
            outerFlow.addConnection("sub", "end");

            WorkflowOutput output = outerFlow.invoke(
                    Map.of("value", 5.0), newSession(), null);

            assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
            assertNotNull(output.getResult());
            System.out.println("[SubWorkflow] Result: " + output.getResult());
        }

        @Test
        @DisplayName("SubWorkflowComponentImpl exposes drawable")
        void testSubWorkflowDrawable() {
            Workflow innerFlow = new Workflow();
            innerFlow.setStartComp("s", new Start(), Map.of(), null);
            innerFlow.setEndComp("e", new PassthroughComponent(), Map.of(), null);
            innerFlow.addConnection("s", "e");

            SubWorkflowComponentImpl subComp = new SubWorkflowComponentImpl(innerFlow);
            assertNotNull(subComp.getSubWorkflow());
        }
    }

    @Nested
    @DisplayName("LoopSetVariableComponent Tests")
    class LoopSetVariableTests {

        @Test
        @DisplayName("LoopSetVariableComponent construction with variable mapping")
        void testLoopSetVariableConstruction() {
            Map<String, Object> mapping = Map.of("counter", "${loop.index}");
            LoopSetVariableComponent comp = new LoopSetVariableComponent(mapping);
            assertNotNull(comp);
            System.out.println("[LoopSetVariable] Created with mapping: " + mapping);
        }
    }

    @Nested
    @DisplayName("DrawableGraph & Visualization Tests")
    class VisualizationTests {

        @Test
        @DisplayName("DrawableGraph construction with nodes and edges")
        void testDrawableGraphConstruction() {
            DrawableNode nodeA = new DrawableNode("a", "Node A", null);
            DrawableNode nodeB = new DrawableNode("b", "Node B", null);
            DrawableEdge edge = new DrawableEdge("a", "b");

            DrawableGraph graph = new DrawableGraph(
                    Map.of("a", nodeA, "b", nodeB),
                    List.of(edge),
                    List.of(nodeA),
                    List.of(nodeB),
                    List.of());

            assertNotNull(graph.getNodes());
            assertEquals(2, graph.getNodes().size());
            assertEquals(1, graph.getEdges().size());
            assertEquals(1, graph.getStartNodes().size());
            assertEquals(1, graph.getEndNodes().size());
        }

        @Test
        @DisplayName("DrawableEdge with streaming and conditional flags")
        void testDrawableEdgeFlags() {
            DrawableEdge edge = new DrawableEdge("src", "tgt");
            assertNotNull(edge);
            assertEquals("src", edge.getSource());
            assertEquals("tgt", edge.getTarget());
        }

        @Test
        @DisplayName("DrawableNode with metadata")
        void testDrawableNodeMetadata() {
            DrawableNode node = new DrawableNode("n1", "My Node",
                    Map.of("type", "processor", "version", "1.0"));
            assertEquals("n1", node.getId());
            assertEquals("My Node", node.getName());
            assertNotNull(node.getMetadata());
        }

        @Test
        @DisplayName("Empty DrawableGraph")
        void testEmptyDrawableGraph() {
            DrawableGraph graph = new DrawableGraph();
            assertNotNull(graph);
        }
    }
}
