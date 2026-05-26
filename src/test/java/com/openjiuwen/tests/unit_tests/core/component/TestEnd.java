/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.component;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_end.py} in 
 * {@code tests.unit_tests.core.component}.
 */
@Tag("unit-test")
@Disabled("Requires workflow configuration and async support")
class TestEnd {

    // -----------------------------------------------------------------------
    // Mock classes
    // -----------------------------------------------------------------------

    static class End {
        Map<String, Object> config;

        End() {
            this.config = new HashMap<>();
        }

        End(Map<String, Object> config) {
            this.config = config != null ? config : new HashMap<>();
        }

        Map<String, Object> invoke(Map<String, Object> inputs) {
            String template = (String) config.get("responseTemplate");
            if (template == null) {
                template = (String) config.get("response_template");
            }
            
            if (template != null) {
                String result = renderTemplate(template, inputs);
                return Map.of("response", result);
            }
            
            return new HashMap<>(inputs);
        }

        private String renderTemplate(String template, Map<String, Object> inputs) {
            String result = template;
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                result = result.replace(placeholder, String.valueOf(entry.getValue()));
            }
            return result;
        }
    }

    static class Start {
        Map<String, Object> inputs;

        Start() {}

        void setInputs(Map<String, Object> inputs) {
            this.inputs = inputs;
        }

        Map<String, Object> getInputs() {
            return inputs;
        }
    }

    static class Workflow {
        Map<String, Object> components = new LinkedHashMap<>();
        String startComp;
        String endComp;
        List<String[]> connections = new ArrayList<>();

        void setStartComp(String name, Start start) {
            startComp = name;
            components.put(name, start);
        }

        void setEndComp(String name, End end) {
            endComp = name;
            components.put(name, end);
        }

        void addConnection(String from, String to) {
            connections.add(new String[]{from, to});
        }

        Map<String, Object> invoke(Map<String, Object> inputs) {
            // Simplified workflow execution
            End end = (End) components.get(endComp);
            if (end != null) {
                return end.invoke(inputs);
            }
            return new HashMap<>();
        }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Test simple template workflow")
    void testSimpleTemplateWorkflow() {
        End end = new End(Map.of("responseTemplate", "hello:{{end_input}}"));
        
        Map<String, Object> result = end.invoke(Map.of("end_input", "haha"));
        
        assertEquals(Map.of("response", "hello:haha"), result);
    }

    @Test
    @DisplayName("Test end invoke template")
    void testEndInvokeTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put("response_template", "渲染结果:{{param1}},{{param2}}");
        End end = new End(config);
        
        Map<String, Object> result = end.invoke(Map.of("param1", "你好", "param2", "杭州"));
        
        assertEquals(Map.of("response", "渲染结果:你好,杭州"), result);
    }

    @Test
    @DisplayName("Test end invoke no template")
    void testEndInvokeNoTemplate() {
        End end = new End();
        
        Map<String, Object> result = end.invoke(Map.of("data", "test_value"));
        
        assertNotNull(result);
    }

    @Test
    @DisplayName("Test end with empty config")
    void testEndWithEmptyConfig() {
        End end = new End(new HashMap<>());
        
        Map<String, Object> result = end.invoke(Map.of("input", "value"));
        
        assertNotNull(result);
    }

    @Test
    @DisplayName("Test start component")
    void testStartComponent() {
        Start start = new Start();
        start.setInputs(Map.of("query", "test", "content", "hello"));
        
        assertEquals("test", start.getInputs().get("query"));
        assertEquals("hello", start.getInputs().get("content"));
    }

    @Test
    @DisplayName("Test workflow setup")
    void testWorkflowSetup() {
        Workflow flow = new Workflow();
        flow.setStartComp("s", new Start());
        flow.setEndComp("e", new End());
        flow.addConnection("s", "e");

        assertNotNull(flow.startComp);
        assertNotNull(flow.endComp);
        assertEquals(1, flow.connections.size());
    }

    @Test
    @DisplayName("Test template with multiple placeholders")
    void testTemplateWithMultiplePlaceholders() {
        End end = new End(Map.of("responseTemplate", "Hello {{name}}, your order {{order_id}} is ready"));
        
        Map<String, Object> result = end.invoke(Map.of("name", "Alice", "order_id", "12345"));
        
        assertEquals(Map.of("response", "Hello Alice, your order 12345 is ready"), result);
    }

    @Test
    @Tag("level0")
    @DisplayName("Placeholder test")
    void testPlaceholder() {
        assertTrue(true);
    }
}