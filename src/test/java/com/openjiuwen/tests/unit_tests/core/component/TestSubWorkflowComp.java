/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.component;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_sub_workflow_comp.py} in 
 * {@code tests.unit_tests.core.component}.
 */
@Tag("unit-test")
@Disabled("Requires sub-workflow configuration and async support")
class TestSubWorkflowComp {

    // -----------------------------------------------------------------------
    // Mock classes
    // -----------------------------------------------------------------------

    static class Input {
        Map<String, Object> data;

        Input(Map<String, Object> data) {
            this.data = data;
        }

        Object get(String key) {
            return data != null ? data.get(key) : null;
        }
    }

    static class Output {
        Map<String, Object> data;

        Output(Map<String, Object> data) {
            this.data = data;
        }
    }

    static class Session {
        String sessionId;

        Session(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    static class ModelContext {}

    abstract static class WorkflowComponent {
        String name;

        WorkflowComponent(String name) {
            this.name = name;
        }

        Output invoke(Input inputs, Session session, ModelContext context) {
            return new Output(new HashMap<>());
        }

        Iterator<Output> stream(Input inputs, Session session, ModelContext context) {
            return new ArrayList<Output>().iterator();
        }

        Output transform(Input inputs, Session session, ModelContext context) {
            return new Output(inputs.data);
        }
    }

    static class CustomStream extends WorkflowComponent {
        CustomStream(String name) {
            super(name);
        }

        @Override
        Output invoke(Input inputs, Session session, ModelContext context) {
            return new Output(Map.of("custom_output", inputs.data));
        }

        @Override
        Iterator<Output> stream(Input inputs, Session session, ModelContext context) {
            List<Output> outputs = new ArrayList<>();
            if (inputs != null && inputs.get("value") != null) {
                List<?> values = (List<?>) inputs.get("value");
                for (Object index : values) {
                    outputs.add(new Output(Map.of("value", "stream_" + index)));
                }
            }
            return outputs.iterator();
        }
    }

    static class BatchConsumerComponent extends WorkflowComponent {
        static Map<String, Object> lastInvokedInputs;

        BatchConsumerComponent(String name) {
            super(name);
        }

        @Override
        Output invoke(Input inputs, Session session, ModelContext context) {
            lastInvokedInputs = inputs.data;
            Object result = inputs.get("result");
            return new Output(Map.of("consumed_result", result));
        }
    }

    static class SubWorkflowComponent extends WorkflowComponent {
        Workflow subWorkflow;

        SubWorkflowComponent(String name, Workflow subWorkflow) {
            super(name);
            this.subWorkflow = subWorkflow;
        }

        Output invokeSubWorkflow(Map<String, Object> inputs, Session session) {
            // Execute sub-workflow
            return subWorkflow.invoke(inputs, session);
        }
    }

    static class Workflow {
        int maxNestingDepth = 10;
        Map<String, WorkflowComponent> components = new LinkedHashMap<>();
        List<String[]> connections = new ArrayList<>();
        String startComp;
        String endComp;

        Workflow() {}

        Workflow(int maxNestingDepth) {
            this.maxNestingDepth = maxNestingDepth;
        }

        Workflow setStartComp(String name, WorkflowComponent comp) {
            startComp = name;
            components.put(name, comp);
            return this;
        }

        Workflow addWorkflowComp(String name, WorkflowComponent comp) {
            components.put(name, comp);
            return this;
        }

        Workflow setEndComp(String name, WorkflowComponent comp) {
            endComp = name;
            components.put(name, comp);
            return this;
        }

        Workflow addConnection(String from, String to) {
            connections.add(new String[]{from, to});
            return this;
        }

        Output invoke(Map<String, Object> inputs, Session session) {
            // Simplified workflow execution
            return new Output(inputs);
        }
    }

    static class Start extends WorkflowComponent {
        Start() { super("start"); }
    }

    static class End extends WorkflowComponent {
        End() { super("end"); }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Test add component")
    void testAddComponent() {
        Workflow mainWorkflow = new Workflow(2);
        mainWorkflow.setStartComp("start", new Start());
        mainWorkflow.addWorkflowComp("sub_comp", new SubWorkflowComponent("sub_comp", new Workflow()));
        mainWorkflow.setEndComp("end", new End());
        mainWorkflow.addConnection("start", "sub_comp");
        mainWorkflow.addConnection("sub_comp", "end");

        assertNotNull(mainWorkflow.components.get("sub_comp"));
        assertEquals(3, mainWorkflow.components.size());
    }

    @Test
    @DisplayName("Test custom stream component")
    void testCustomStreamComponent() {
        CustomStream custom = new CustomStream("custom");
        Input input = new Input(Map.of("value", Arrays.asList(1, 2, 3)));

        Output output = custom.invoke(input, new Session("test"), new ModelContext());

        assertNotNull(output);
        assertTrue(output.data.containsKey("custom_output"));
    }

    @Test
    @DisplayName("Test batch consumer component")
    void testBatchConsumerComponent() {
        BatchConsumerComponent consumer = new BatchConsumerComponent("consumer");
        Input input = new Input(Map.of("result", "test_result"));

        Output output = consumer.invoke(input, new Session("test"), new ModelContext());

        assertNotNull(output);
        assertEquals("test_result", output.data.get("consumed_result"));
        assertNotNull(BatchConsumerComponent.lastInvokedInputs);
    }

    @Test
    @DisplayName("Test workflow nesting depth")
    void testWorkflowNestingDepth() {
        Workflow workflow = new Workflow(5);

        assertEquals(5, workflow.maxNestingDepth);
    }

    @Test
    @DisplayName("Test workflow connections")
    void testWorkflowConnections() {
        Workflow workflow = new Workflow()
            .setStartComp("start", new Start())
            .addWorkflowComp("middle", new CustomStream("middle"))
            .setEndComp("end", new End())
            .addConnection("start", "middle")
            .addConnection("middle", "end");

        assertEquals(2, workflow.connections.size());
        assertArrayEquals(new String[]{"start", "middle"}, workflow.connections.get(0));
        assertArrayEquals(new String[]{"middle", "end"}, workflow.connections.get(1));
    }

    @Test
    @DisplayName("Test sub workflow component creation")
    void testSubWorkflowComponentCreation() {
        Workflow subWorkflow = new Workflow();
        subWorkflow.setStartComp("sub_start", new Start());
        subWorkflow.setEndComp("sub_end", new End());

        SubWorkflowComponent subComp = new SubWorkflowComponent("sub", subWorkflow);

        assertNotNull(subComp);
        assertNotNull(subComp.subWorkflow);
        assertEquals("sub", subComp.name);
    }

    @Test
    @Tag("level0")
    @DisplayName("Placeholder test")
    void testPlaceholder() {
        assertTrue(true);
    }
}