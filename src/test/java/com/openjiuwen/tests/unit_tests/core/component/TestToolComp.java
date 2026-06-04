/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowSessions;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.tool.ToolComponent;
import com.openjiuwen.core.workflow.component.tool.ToolComponentConfig;
import com.openjiuwen.core.workflow.component.tool.ToolComponentOutput;
import com.openjiuwen.core.workflow.component.tool.ToolExecutable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/core/component/test_tool_comp.py}.
 */
class TestToolComp {

    @Test
    @DisplayName("local function tool applies default arguments")
    void testLocalFunctionAppliesDefaults() throws Exception {
        Tool tool = localFunctionTool(uniqueId("test_local_function"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.invoke(Map.of("a", "hello"));

        assertEquals("hello", result.get("res"));
        assertEquals(789, result.get("info"));
    }

    @Test
    @DisplayName("ToolExecutable invokes a RESTful API tool and reports success")
    void testToolCompInvoke() {
        withSsrfProtectionDisabled(() -> {
            ToolExecutable executable = new ToolExecutable(new ToolComponentConfig());
            executable.setTool(new FakeRestfulApi(Map.of()));

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) executable.invoke(
                    Map.of("location", "Beijing", "date", 15), null, null);

            assertEquals(0, result.get(ToolComponentOutput.PY_ERR_CODE));
            assertEquals(Map.of(), result.get(ToolComponentOutput.RESTFUL_DATA));
        });
    }

    @Test
    @DisplayName("ToolComponent resolves tool_id from Runner resource manager in workflow")
    void testToolCompInWorkflow() {
        String toolId = uniqueId("test_local_function");
        Runner.resourceMgr().addTool(localFunctionTool(toolId), null);

        ToolComponentConfig config = new ToolComponentConfig();
        config.setToolId(toolId);

        WorkflowOutput result = invokeToolWorkflow(new ToolComponent(config), "test_tool");

        assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
        String response = stringResponse(result);
        assertTrue(response.contains("你好"));
        assertTrue(response.contains("789"));
    }

    @Test
    @DisplayName("ToolComponent supports explicit bind_tool workflow path")
    void testInvokeWorkflowWithStartToolEndCompatibleInterface() {
        ToolComponent component = new ToolComponent(new ToolComponentConfig())
                .bindTool(localFunctionTool(uniqueId("test_local_function")));

        WorkflowOutput result = invokeToolWorkflow(component, "test_tool_2");

        assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
        String response = stringResponse(result);
        assertTrue(response.contains("你好"));
        assertTrue(response.contains("789"));
    }

    @Test
    @DisplayName("ToolComponent propagates mocked RESTful API data through workflow")
    void testInvokeWorkflowWithStartToolEndRestfulApi() {
        withSsrfProtectionDisabled(() -> {
            Map<String, Object> expectedWeather = new LinkedHashMap<>();
            expectedWeather.put("city", "Hangzhou");
            expectedWeather.put("country", "CN");
            expectedWeather.put("weather", "rainy");
            expectedWeather.put("temperature", 12.95);
            expectedWeather.put("feels_like", 12.42);
            expectedWeather.put("humidity", 81);
            expectedWeather.put("wind_speed", 1.62);

            ToolComponent component = new ToolComponent(new ToolComponentConfig())
                    .bindTool(new FakeRestfulApi(expectedWeather));

            Workflow flow = new Workflow(WorkflowCard.builder()
                    .name("tool")
                    .id("mock")
                    .version("1.0")
                    .build());
            flow.setStartComp("s", new Start(), Map.of("query", "${query}", "name", "${name}"));
            flow.addWorkflowComp("tool", component, Map.of("location", "${s.query}"));
            flow.setEndComp("e", new End(Map.of("responseTemplate", "{{output}}")),
                    Map.of("output", "${tool.data}"));
            flow.addConnection("s", "tool");
            flow.addConnection("tool", "e");

            WorkflowSessionApi session = WorkflowSessions.createWorkflowSession("test_tool_restful");
            ModelContext context = new ContextEngine().createContext("tool_workflow", null);
            WorkflowOutput result = flow.invoke(Map.of("query", "hangzhou"), session, context);

            assertNotNull(result);
            assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
            String response = stringResponse(result);
            assertTrue(response.contains("Hangzhou"));
            assertTrue(response.contains("temperature"));
            assertTrue(response.contains("rainy"));
        });
    }

    @Test
    @DisplayName("ToolComponent rejects missing tool binding")
    void testToolComponentRequiresBoundOrRegisteredTool() {
        ToolComponentConfig config = new ToolComponentConfig();
        config.setToolId(uniqueId("missing_tool"));

        ToolComponent component = new ToolComponent(config);

        assertThrows(RuntimeException.class, component::toExecutable);
    }

    private static WorkflowOutput invokeToolWorkflow(ToolComponent toolComponent, String sessionId) {
        Workflow flow = new Workflow(WorkflowCard.builder()
                .name("tool")
                .id("tool_workflow")
                .version("1.0")
                .build());
        flow.setStartComp("s", new Start(), Map.of("query", "${query}", "name", "${name}"));
        flow.addWorkflowComp("tool", toolComponent, Map.of("a", "${s.query}", "b", "${s.name}"));
        flow.setEndComp("e", new End(Map.of("responseTemplate", "{{output}}")),
                Map.of("output", "${tool.data}"));
        flow.addConnection("s", "tool");
        flow.addConnection("tool", "e");

        WorkflowSessionApi session = WorkflowSessions.createWorkflowSession(sessionId);
        ModelContext context = new ContextEngine().createContext("tool_workflow", null);
        return flow.invoke(Map.of("query", "你好"), session, context);
    }

    @SuppressWarnings("unchecked")
    private static String stringResponse(WorkflowOutput output) {
        Map<String, Object> result = (Map<String, Object>) output.getResult();
        return String.valueOf(result.get("response"));
    }

    private static Tool localFunctionTool(String id) {
        ToolCard card = ToolCard.builder()
                .id(id)
                .name("test_local_function")
                .description("test local function")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "a", Map.of("description", "param 1", "type", "string"),
                                "b", Map.of("description", "param 2", "type", "integer", "default", 789)),
                        "required", java.util.List.of("a")))
                .build();
        return new LocalFunction(card, inputs -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("res", inputs.get("a"));
            result.put("info", inputs.get("b"));
            return result;
        });
    }

    private static String uniqueId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static void withSsrfProtectionDisabled(Runnable action) {
        String previousSsrfProtectEnabled = System.getProperty("SSRF_PROTECT_ENABLED");
        System.setProperty("SSRF_PROTECT_ENABLED", "false");
        try {
            action.run();
        } finally {
            if (previousSsrfProtectEnabled == null) {
                System.clearProperty("SSRF_PROTECT_ENABLED");
            } else {
                System.setProperty("SSRF_PROTECT_ENABLED", previousSsrfProtectEnabled);
            }
        }
    }

    private static final class FakeRestfulApi extends RestfulApi {
        private final Map<String, Object> data;

        private FakeRestfulApi(Map<String, Object> data) {
            super(RestfulApiCard.builder()
                    .id(uniqueId("weather"))
                    .name("WeatherReporter")
                    .description("weather plugin")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "location", Map.of("type", "string"),
                                    "date", Map.of("type", "string")),
                            "required", java.util.List.of("location")))
                    .url("http://127.0.0.1:9000/weather")
                    .method("GET")
                    .headers(Map.of())
                    .build());
            this.data = data;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Map.of(
                    "code", 200,
                    "data", data,
                    "url", "http://127.0.0.1:9000/weather",
                    "headers", Map.of("content-type", "application/json"),
                    "reason", "OK",
                    "message", "success");
        }
    }
}
