/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.McpToolInfo;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.runner.callback.ToolCallEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's MCP base behavior in
 * {@code openjiuwen/core/foundation/tool/mcp/base.py}.
 */
class McpToolTest {

    @AfterEach
    void clearFramework() {
        Tool.clearCallbackFramework();
    }

    @Test
    void serverConfigDefaultsMatchPythonModel() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName("demo")
                .serverPath("http://127.0.0.1:8930/mcp")
                .build();

        assertThat(config.getServerId()).isNotBlank();
        assertThat(config.getServerId()).doesNotContain("-");
        assertThat(config.getClientType()).isEqualTo("sse");
        assertThat(config.getParams()).isEmpty();
        assertThat(config.getAuthHeaders()).isEmpty();
        assertThat(config.getAuthQueryParams()).isEmpty();
        assertThat(McpServerConfig.NO_TIMEOUT).isEqualTo(-1.0f);
    }

    @Test
    void toolCardBuildsMcpToolInfo() {
        McpToolCard card = McpToolCard.builder()
                .id("server.tool")
                .name("lookup")
                .description("Lookup")
                .inputParams(Map.of("type", "object"))
                .serverName("demo-server")
                .serverId("server-1")
                .build();

        McpToolInfo info = card.toolInfo();

        assertThat(info.getName()).isEqualTo("lookup");
        assertThat(info.getDescription()).isEqualTo("Lookup");
        assertThat(info.getParameters()).containsEntry("type", "object");
        assertThat(info.getServerName()).isEqualTo("demo-server");
        assertThat(card.getServerId()).isEqualTo("server-1");
    }

    @Test
    void extractMcpToolResultContentHandlesTextDataImageAndDump() {
        assertThat(McpBase.extractMcpToolResultContent(Map.of("content", List.of()))).isNull();
        assertThat(McpBase.extractMcpToolResultContent(Map.of(
                "content", List.of(Map.of("text", "hello"))
        ))).isEqualTo("hello");
        assertThat(McpBase.extractMcpToolResultContent(Map.of(
                "content", List.of(Map.of("mimeType", "image/png", "data", "abcd"))
        ))).isEqualTo("[image content: image/png, 4 base64 chars]");
        assertThat(McpBase.extractMcpToolResultContent(Map.of(
                "content", List.of(Map.of("mime_type", "application/json", "data", Map.of("ok", true)))
        ))).isEqualTo(Map.of("ok", true));
        assertThat(McpBase.extractMcpToolResultContent(Map.of(
                "content", List.of(new DumpableContent())
        ))).isEqualTo(Map.of("type", "custom"));
    }

    @Test
    void constructorRejectsNullClient() {
        McpToolCard card = basicCard();

        assertThatThrownBy(() -> new McpTool(null, card))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOL_MCP_CLIENT_NOT_SUPPORTED));
    }

    @Test
    void invokeFormatsArgumentsEmitsParseCallbacksAndCallsClient() throws Exception {
        RecordingFramework framework = new RecordingFramework();
        Tool.setCallbackFramework(framework);
        FakeMcpClient client = new FakeMcpClient("ok");
        McpTool tool = new McpTool(client, cardWithSchema());
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("path", "readme.md");
        inputs.put("optional", null);

        Object result = tool.invoke(inputs);

        assertThat(result).isEqualTo(Map.of("result", "ok"));
        assertThat(client.lastToolName).isEqualTo("lookup");
        assertThat(client.lastArguments).containsEntry("path", "readme.md").containsEntry("mode", "text");
        assertThat(client.lastArguments).doesNotContainKey("optional");
        assertThat(framework.triggered(ToolCallEvents.TOOL_PARSE_STARTED).kwargs())
                .containsEntry("tool_name", "lookup")
                .containsEntry("tool_id", "mcp.lookup");
        assertThat(framework.triggered(ToolCallEvents.TOOL_PARSE_FINISHED).kwargs().get("formatted_inputs"))
                .isEqualTo(client.lastArguments);
    }

    @Test
    void invokeCanPreserveNullArgumentsWhenSkipNoneValueFalse() throws Exception {
        FakeMcpClient client = new FakeMcpClient("ok");
        McpTool tool = new McpTool(client, cardWithSchema());
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("path", "readme.md");
        inputs.put("optional", null);

        Object result = tool.invoke(inputs, Map.of("skip_none_value", false));

        assertThat(result).isEqualTo(Map.of("result", "ok"));
        assertThat(client.lastArguments).containsEntry("optional", null);
    }

    @Test
    void invokeAwaitsCompletionStageResult() throws Exception {
        FakeMcpClient client = new FakeMcpClient(CompletableFuture.completedFuture("async-ok"));
        McpTool tool = new McpTool(client, basicCard());

        assertThat(tool.invoke(Map.of())).isEqualTo(Map.of("result", "async-ok"));
    }

    @Test
    void invokePassesConfiguredOperationTimeoutToThreeArgumentClient() throws Exception {
        TimeoutAwareMcpClient client = new TimeoutAwareMcpClient("ok");
        McpTool tool = new McpTool(client, basicCard(), 2.5F);

        assertThat(tool.invoke(Map.of())).isEqualTo(Map.of("result", "ok"));

        assertThat(client.lastToolName).isEqualTo("lookup");
        assertThat(client.lastTimeout).isEqualTo(2.5F);
    }

    @Test
    void invokeWrapsClientErrorsAsMcpExecutionError() {
        FakeMcpClient client = new FakeMcpClient(new IllegalStateException("boom"));
        McpTool tool = new McpTool(client, basicCard());

        assertThatThrownBy(() -> tool.invoke(Map.of()))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> {
                    BaseError baseError = (BaseError) error;
                    assertThat(baseError.getStatus()).isEqualTo(StatusCode.TOOL_MCP_EXECUTION_ERROR);
                    assertThat(baseError.getParams()).containsEntry("method", "invoke");
                });
    }

    @Test
    void streamIsNotSupported() {
        McpTool tool = new McpTool(new FakeMcpClient("unused"), basicCard());

        assertThatThrownBy(() -> tool.stream(Map.of()))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOL_STREAM_NOT_SUPPORTED));
    }

    private static McpToolCard basicCard() {
        return McpToolCard.builder()
                .id("mcp.lookup")
                .name("lookup")
                .description("Lookup")
                .serverName("demo-server")
                .inputParams(null)
                .build();
    }

    private static McpToolCard cardWithSchema() {
        return McpToolCard.builder()
                .id("mcp.lookup")
                .name("lookup")
                .description("Lookup")
                .serverName("demo-server")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string"),
                                "mode", Map.of("type", "string", "default", "text"),
                                "optional", Map.of("type", "string")
                        ),
                        "required", List.of("path")
                ))
                .build();
    }

    public static final class FakeMcpClient {
        private final Object response;
        private String lastToolName;
        private Map<String, Object> lastArguments;

        FakeMcpClient(Object response) {
            this.response = response;
        }

        public Object callTool(String toolName, Map<String, Object> arguments) {
            this.lastToolName = toolName;
            this.lastArguments = new LinkedHashMap<>(arguments);
            if (response instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return response;
        }
    }

    public static final class TimeoutAwareMcpClient {
        private final Object response;
        private String lastToolName;
        private float lastTimeout = McpServerConfig.NO_TIMEOUT;

        TimeoutAwareMcpClient(Object response) {
            this.response = response;
        }

        public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
            this.lastToolName = toolName;
            this.lastTimeout = timeout;
            return response;
        }
    }

    public static final class DumpableContent {
        public Map<String, Object> model_dump() {
            return Map.of("type", "custom", "data", "secret");
        }
    }

    private record RecordedCall(String event, Object[] args, Map<String, Object> kwargs) {
    }

    private static final class RecordingFramework implements DecoratorFramework {
        private final List<RecordedCall> calls = new ArrayList<>();

        @Override
        public CallbackInfo registerSync(String event, Function<Map<String, Object>, Object> callback, int priority,
                                         boolean once, String namespace, Set<String> tags, List<EventFilter> filters,
                                         Function<Map<String, Object>, Object> rollbackHandler,
                                         Function<Map<String, Object>, Object> errorHandler, int maxRetries,
                                         double retryDelay, Double timeout, String callbackType) {
            return null;
        }

        @Override
        public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
            calls.add(new RecordedCall(event, args, new LinkedHashMap<>(kwargs)));
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return Map.of();
        }

        private RecordedCall triggered(String event) {
            return calls.stream().filter(call -> call.event().equals(event)).findFirst().orElseThrow();
        }
    }
}
