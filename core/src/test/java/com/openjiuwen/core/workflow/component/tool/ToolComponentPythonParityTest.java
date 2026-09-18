/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.EndConfig;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.loop.LoopComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopGroup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Mirrors Python's tests in
 * {@code tests/unit_tests/core/component/test_tool_comp.py}.
 */
class ToolComponentPythonParityTest {

    private static final String LOCAL_TOOL_ID = "test_local_function";

    @BeforeEach
    void disableSsrfProtectionForMockedRestfulApi() {
        setUrlUtilsEnvReader(key -> "SSRF_PROTECT_ENABLED".equals(key) ? "false" : System.getenv(key));
    }

    @AfterEach
    void removeRegisteredTools() {
        removeToolIfPresent(LOCAL_TOOL_ID);
        removeToolIfPresent("weather_123");
        resetUrlUtilsEnvReader();
    }

    @Test
    void testToolCompInvoke() {
        ToolExecutable executable = new ToolExecutable(new ToolComponentConfig())
                .setTool(restfulTool("test", Map.of()));

        Map<?, ?> result = assertInstanceOf(Map.class, executable.invoke(
                linkedMap("location", "Beijing", "date", 15),
                (BaseSession) null,
                null));

        assertThat(result.get("error_code")).isEqualTo(0);
        assertThat(result.get("errMessage")).isEqualTo("success");
        assertThat(result.get("data")).isEqualTo(Map.of());
    }

    @Test
    void testToolCompInWorkflow() {
        Runner.resourceMgr().addTool(localFunctionTool(), null, true);
        Workflow flow = new Workflow();
        ToolComponentConfig toolConfig = new ToolComponentConfig();
        toolConfig.setToolId(LOCAL_TOOL_ID);

        flow.setStartComp("s", new Start(), Map.of());
        flow.setEndComp("e", new End(), Map.of("output", "${tool.data}"));
        flow.addWorkflowComp("tool", new ToolComponent(toolConfig), Map.of("a", "res", "b", 789));
        flow.addConnection("s", "tool");
        flow.addConnection("tool", "e");

        WorkflowOutput output = flow.invoke(Map.of(), workflowSession("test"), null);

        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(output.getResult()).isEqualTo(Map.of("output", Map.of("output", linkedMap("res", "res",
                "info", 789))));
    }

    @Test
    void testInvokeWorkflowWithStartToolEnd() {
        Runner.resourceMgr().addTool(localFunctionTool(), null, true);
        ToolComponentConfig config = new ToolComponentConfig();
        config.setToolId(LOCAL_TOOL_ID);

        WorkflowOutput output = invokeLocalToolWorkflow(new ToolComponent(config), "test_tool");

        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(output.getResult()).isEqualTo(Map.of("response", "{res=你好, info=789}"));
    }

    @Test
    void testInvokeWorkflowWithStartToolEndWithCompatibleInterface() {
        ToolComponent toolComponent = new ToolComponent(new ToolComponentConfig()).bindTool(localFunctionTool());

        WorkflowOutput output = invokeLocalToolWorkflow(toolComponent, "test_tool_2");

        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(output.getResult()).isEqualTo(Map.of("response", "{res=你好, info=789}"));
    }

    @Test
    void testInvokeWorkflowWithStartToolEndWithRestfulApi() {
        Map<String, Object> weatherData = linkedMap(
                "city", "Hangzhou",
                "country", "CN",
                "weather", "rainy",
                "temperature", 12.95,
                "feels_like", 12.42,
                "humidity", 81,
                "wind_speed", 1.62);
        RestfulApi weatherPlugin = restfulTool("weather_123", weatherData);
        ToolComponent toolComponent = new ToolComponent(new ToolComponentConfig()).bindTool(weatherPlugin);

        WorkflowOutput output = invokeRestfulWorkflow(toolComponent, "test_tool_restful");

        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(output.getResult()).isInstanceOf(Map.class);
        Object response = ((Map<?, ?>) output.getResult()).get("response");
        assertThat(String.valueOf(response)).contains("Hangzhou", "temperature", "rainy");
    }

    @Test
    void nestedLoopRestfulToolProjectsParsedBodyAndKeepsDirectEnvelope() throws Exception {
        Map<String, Object> weatherData = linkedMap(
                "location", "Hangzhou",
                "temperature", "18C - 26C",
                "condition", "sunny");
        RestfulApi weatherPlugin = restfulTool("weather_123", weatherData);

        Map<?, ?> directResponse = assertInstanceOf(Map.class,
                weatherPlugin.invoke(Map.of("location", "Hangzhou")));
        assertThat(new HashSet<>(directResponse.keySet())).isEqualTo(
                Set.of("code", "data", "url", "headers", "reason", "message"));
        assertThat(directResponse.get("data")).isEqualTo(weatherData);

        LoopGroup loopGroup = new LoopGroup();
        ToolComponent toolComponent = new ToolComponent(new ToolComponentConfig()).bindTool(weatherPlugin);
        loopGroup.addWorkflowComp("tool", toolComponent, Map.of("location", "${loop.item}"));
        loopGroup.startNodes(List.of("tool"));
        loopGroup.endNodes(List.of("tool"));

        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("items", "${items}"));
        flow.addWorkflowComp("loop", new LoopComponentImpl(loopGroup, Map.of("l_tool", "${tool}")),
                Map.of("loop_type", "array", "loop_array", Map.of("item", "${start.items}")));
        flow.setEndComp("end", new End(), Map.of("end_out", "${loop}"));
        flow.addConnection("start", "loop");
        flow.addConnection("loop", "end");

        BaseSession session = workflowSession("nested-rest-tool");
        WorkflowOutput output = flow.invoke(Map.of("items", List.of("Hangzhou")), session, workflowContext(session));

        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        Map<?, ?> result = assertInstanceOf(Map.class, output.getResult());
        Map<?, ?> outputMap = assertInstanceOf(Map.class, result.get("output"));
        Map<?, ?> endOut = assertInstanceOf(Map.class, outputMap.get("end_out"));
        List<?> loopTools = assertInstanceOf(List.class, endOut.get("l_tool"));
        Map<?, ?> firstTool = assertInstanceOf(Map.class, loopTools.get(0));
        assertThat(firstTool.get("errCode")).isEqualTo(0);
        assertThat(firstTool.get("errMessage")).isEqualTo("success");
        assertThat(firstTool.get("data")).isEqualTo(weatherData);
    }

    private static WorkflowOutput invokeLocalToolWorkflow(ToolComponent toolComponent, String sessionId) {
        Workflow flow = new Workflow(new WorkflowCard("tool_workflow", "tool", "", "1.0", null));
        flow.setStartComp("s", new Start(), Map.of("query", "${query}", "name", "${name}"));
        flow.setEndComp("e", new End(new EndConfig("{{output}}")), Map.of("output", "${tool.data}"));
        flow.addWorkflowComp("tool", toolComponent, Map.of("a", "${s.query}", "b", "${s.name}"));
        flow.addConnection("s", "tool");
        flow.addConnection("tool", "e");
        BaseSession session = workflowSession(sessionId);
        return flow.invoke(Map.of("query", "你好"), session, workflowContext(session));
    }

    private static WorkflowOutput invokeRestfulWorkflow(ToolComponent toolComponent, String sessionId) {
        Workflow flow = new Workflow(new WorkflowCard("mock", "tool", "", "1.0", null));
        flow.setStartComp("s", new Start(), Map.of("query", "${query}", "name", "${name}"));
        flow.setEndComp("e", new End(new EndConfig("{{output}}")), Map.of("output", "${tool.data}"));
        flow.addWorkflowComp("tool", toolComponent, Map.of("location", "${s.query}"));
        flow.addConnection("s", "tool");
        flow.addConnection("tool", "e");
        BaseSession session = workflowSession(sessionId);
        return flow.invoke(Map.of("query", "hangzhou"), session, workflowContext(session));
    }

    private static BaseSession workflowSession(String sessionId) {
        return AgentSession.createAgentSession(sessionId, null, null).getInner().createWorkflowSession();
    }

    private static ModelContext workflowContext(BaseSession session) {
        return new ContextEngine(new ContextEngineConfig()).createContext("tool_workflow", session);
    }

    private static void removeToolIfPresent(String toolId) {
        try {
            Runner.resourceMgr().removeTool(toolId);
        } catch (RuntimeException ignored) {
            // Missing tools are fine; this only isolates the process-global Runner registry between tests.
        }
    }

    private static void setUrlUtilsEnvReader(Function<String, String> reader) {
        try {
            Method method = UrlUtils.class.getDeclaredMethod("setEnvReaderForTests", Function.class);
            method.setAccessible(true);
            method.invoke(null, reader);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("failed to configure UrlUtils test env reader", exception);
        }
    }

    private static void resetUrlUtilsEnvReader() {
        try {
            Method method = UrlUtils.class.getDeclaredMethod("resetEnvReaderForTests");
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("failed to reset UrlUtils test env reader", exception);
        }
    }

    private static LocalFunctionTool localFunctionTool() {
        return new LocalFunctionTool();
    }

    private static RestfulStub restfulTool(String id, Object data) {
        Map<String, Object> response = linkedMap(
                "code", 200,
                "data", data,
                "url", "http://127.0.0.1:9000/weather",
                "headers", Map.of("content-type", "application/json"),
                "reason", "OK",
                "message", "success");
        return new RestfulStub(id, response);
    }

    private static ToolCard localFunctionCard() {
        return ToolCard.builder()
                .id(LOCAL_TOOL_ID)
                .name(LOCAL_TOOL_ID)
                .description("test local function")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "a", Map.of("description", "param1", "type", "string"),
                                "b", Map.of("description", "param2", "type", "integer", "default", 789)),
                        "required", java.util.List.of("a")))
                .build();
    }

    private static RestfulApiCard restfulCard(String id) {
        return RestfulApiCard.builder()
                .id(id)
                .name(id.equals("weather_123") ? "WeatherReporter" : "test")
                .description("test")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "location", Map.of("description", "location", "type", "string"),
                                "date", Map.of("description", "date", "type", "integer")),
                        "required", java.util.List.of("location")))
                .url(id.equals("weather_123")
                        ? "http://127.0.0.1:9000/weather"
                        : "http://127.0.0.1:8000")
                .headers(Map.of())
                .method("GET")
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> linkedMap(Object... items) {
        Map<K, V> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < items.length; i += 2) {
            values.put((K) items[i], (V) items[i + 1]);
        }
        return values;
    }

    private static final class LocalFunctionTool extends Tool {
        private LocalFunctionTool() {
            super(localFunctionCard());
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return linkedMap("res", inputs.get("a"), "info", inputs.getOrDefault("b", 789) == null
                    ? 789
                    : inputs.get("b"));
        }
    }

    private static final class RestfulStub extends RestfulApi {
        private final Object response;

        private RestfulStub(String id, Object response) {
            super(restfulCard(id));
            this.response = response;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return response;
        }
    }
}
