/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity checks for T01180.
 *
 * <p>Mirrors Python's {@code ToolComponentConfig}, {@code ToolComponentInput},
 * {@code ToolComponentOutput}, {@code ToolExecutable}, and {@code ToolComponent} in
 * {@code openjiuwen/core/workflow/components/tool/tool_comp.py}.</p>
 */
class T01180ToolComponentTest {

    @Test
    void toolComponentInputPreservesArbitraryFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("prompt", "hello");
        fields.put("nested", Map.of("answer", 42));

        ToolComponentInput input = ToolComponentInput.fromMap(fields);

        assertEquals("hello", input.get("prompt"));
        assertEquals(Map.of("answer", 42), input.get("nested"));
        assertEquals(fields, input.toMap());
    }

    @Test
    void nonRestfulRawOutputIsWrappedAsData() {
        RecordingTool tool = new RecordingTool("raw", "tool-result");

        Map<?, ?> output = invokeWithTool(tool, Map.of("question", "life", "limit", 3));

        assertEquals("tool-result", output.get("data"));
        assertEquals(0, output.get("errCode"));
        assertEquals("", output.get("errMessage"));
        assertEquals(Map.of("question", "life", "limit", 3), tool.lastInputs);
        assertEquals(Boolean.FALSE, tool.lastKwargs.get("skip_inputs_validate"));
        assertEquals(Boolean.TRUE, tool.lastKwargs.get("skip_none_value"));
    }

    @Test
    void nonRestfulCodeDataMessageDictPassesThrough() {
        RecordingTool tool = new RecordingTool("dict", Map.of(
                "code", "7",
                "data", Map.of("value", "ok"),
                "message", "accepted"
        ));

        Map<?, ?> output = invokeWithTool(tool, Map.of());

        assertEquals(7, output.get("errCode"));
        assertEquals("accepted", output.get("errMessage"));
        assertEquals(Map.of("value", "ok"), output.get("data"));
    }

    @Test
    void baseErrorIsConvertedToOutputEnvelope() {
        BaseError failure = new BaseError(StatusCode.TOOL_EXECUTION_ERROR, "base failure", null, null);
        RecordingTool tool = new RecordingTool("base-error", failure);

        Map<?, ?> output = invokeWithTool(tool, Map.of());

        assertEquals(StatusCode.TOOL_EXECUTION_ERROR.getCode(), output.get("errCode"));
        assertEquals("base failure", output.get("errMessage"));
    }

    @Test
    void genericExceptionUsesToolExecutionStatus() {
        RecordingTool tool = new RecordingTool("generic-error", new IllegalStateException("boom"));

        Map<?, ?> output = invokeWithTool(tool, Map.of());

        assertEquals(StatusCode.TOOL_EXECUTION_ERROR.getCode(), output.get("errCode"));
        assertTrue(String.valueOf(output.get("errMessage")).contains("boom"));
    }

    @Test
    void restfulApiResultMapsHttpCodeAndData() {
        Map<String, Object> response = Map.of(
                "code", "201",
                "data", Map.of("id", 9),
                "message", "created"
        );
        RestfulStub tool = new RestfulStub(response);

        Map<?, ?> output = invokeWithTool(tool, Map.of("payload", true));

        assertEquals(StatusCode.SUCCESS.getCode(), output.get("errCode"));
        assertEquals("created", output.get("errMessage"));
        assertEquals(Map.of("id", 9), output.get("data"));
        assertEquals(Map.of("payload", true), tool.lastInputs);
    }

    @Test
    void restfulApiResultDefaultsMissingMessageToEmpty() {
        Map<String, Object> response = Map.of(
                "code", 200,
                "data", Map.of("id", 9)
        );
        RestfulStub tool = new RestfulStub(response);

        Map<?, ?> output = invokeWithTool(tool, Map.of());

        assertEquals(StatusCode.SUCCESS.getCode(), output.get("errCode"));
        assertEquals("", output.get("errMessage"));
        assertEquals(Map.of("id", 9), output.get("data"));
    }

    @Test
    void restfulApiResultPreservesNonSuccessMessage() {
        Map<String, Object> response = Map.of(
                "code", 503,
                "data", Map.of("retryable", true),
                "message", "service unavailable"
        );
        RestfulStub tool = new RestfulStub(response);

        Map<?, ?> output = invokeWithTool(tool, Map.of());

        assertEquals(StatusCode.TOOL_EXECUTION_ERROR.getCode(), output.get("errCode"));
        assertEquals("service unavailable", output.get("errMessage"));
        assertEquals(Map.of("retryable", true), output.get("data"));
    }

    @Test
    void restfulApiResultProjectsFormattedResponseDataAtNestedDepth() {
        Map<String, Object> weather = Map.of("location", "杭州", "condition", "晴");
        Map<String, Object> response = Map.of(
                "code", 200,
                "data", weather,
                "url", "http://localhost:8000/weather?location=%E6%9D%AD%E5%B7%9E",
                "headers", Map.of("content-type", "application/json"),
                "reason", "OK",
                "message", "success");
        RestfulStub tool = new RestfulStub(response);

        Map<?, ?> output = invokeWithTool(tool, Map.of("location", "杭州"), nestedWorkflowSession());

        assertEquals(StatusCode.SUCCESS.getCode(), output.get("errCode"));
        assertEquals("success", output.get("errMessage"));
        assertEquals(weather, output.get("data"));
    }

    @Test
    void toExecutableFailsWhenNoToolIsBound() {
        ToolComponent component = new ToolComponent(new ToolComponentConfig());

        BaseError error = assertThrows(BaseError.class, component::toExecutable);

        assertEquals(StatusCode.COMPONENT_TOOL_INIT_FAILED, error.getStatus());
    }

    @Test
    void bindToolReturnsExecutableWithBoundTool() {
        RecordingTool tool = new RecordingTool("bound", "ok");
        ToolComponent component = new ToolComponent(new ToolComponentConfig());

        ToolComponent returned = component.bindTool(tool);
        Executable<?, ?> executable = component.toExecutable();
        ToolExecutable toolExecutable = assertInstanceOf(ToolExecutable.class, executable);
        Map<?, ?> output = assertInstanceOf(Map.class, toolExecutable.invoke(Map.of(), null, null));

        assertSame(component, returned);
        assertEquals("ok", output.get("data"));
    }

    private static Map<?, ?> invokeWithTool(Tool tool, Map<String, Object> inputs) {
        return invokeWithTool(tool, inputs, null);
    }

    private static Map<?, ?> invokeWithTool(Tool tool, Map<String, Object> inputs, BaseSession session) {
        ToolExecutable executable = new ToolExecutable(new ToolComponentConfig()).setTool(tool);
        return assertInstanceOf(Map.class, executable.invoke(inputs, session, null));
    }

    private static BaseSession nestedWorkflowSession() {
        BaseSession inner = new BaseSession() {
            @Override
            public int workflowNestingDepth() {
                return 1;
            }
        };
        return new NodeSessionApi(inner);
    }

    private static ToolCard card(String id) {
        return ToolCard.builder()
                .id(id)
                .name(id)
                .description("test tool")
                .build();
    }

    private static final class RecordingTool extends Tool {
        private final Object response;
        private final Exception failure;
        private Map<String, Object> lastInputs;
        private Map<String, Object> lastKwargs;

        private RecordingTool(String id, Object response) {
            super(T01180ToolComponentTest.card(id));
            this.response = response;
            this.failure = response instanceof Exception exception ? exception : null;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            this.lastInputs = new LinkedHashMap<>(inputs);
            this.lastKwargs = new LinkedHashMap<>(kwargs);
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }

    private static final class RestfulStub extends RestfulApi {
        private final Object response;
        private Map<String, Object> lastInputs;

        private RestfulStub(Object response) {
            super(RestfulApiCard.builder()
                    .id("restful-stub")
                    .name("restful-stub")
                    .description("test restful tool")
                    .url("http://example.com")
                    .method("GET")
                    .timeout(1.0d)
                    .build());
            this.response = response;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            this.lastInputs = new LinkedHashMap<>(inputs);
            return response;
        }
    }
}
