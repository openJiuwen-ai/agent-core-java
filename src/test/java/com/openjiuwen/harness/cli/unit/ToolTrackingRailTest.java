/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.ToolCallInputs;
import com.openjiuwen.harness.cli.rails.ToolTrackingRail;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestToolTrackingRail} coverage for {@code ToolTrackingRail} in
 * {@code openjiuwen/harness/cli/rails/tool_tracker.py}.
 *
 * <p>The Python test source is {@code tests/cli/unit/test_tool_tracker.py}.</p>
 */
class ToolTrackingRailTest {

    @Test
    void beforeToolCallWritesToolCallAndParsesJsonArgs() {
        RecordingSession session = new RecordingSession();
        ToolCallInputs inputs = toolInputs("bash", "{\"command\":\"pwd\"}", null);
        AgentCallbackContext context = context(session, inputs);

        new ToolTrackingRail().beforeToolCall(context).toCompletableFuture().join();

        OutputSchema output = session.onlyOutput();
        assertEquals("tool_call", output.getType());
        Map<String, Object> payload = payload(output);
        assertEquals("bash", payload.get("tool_name"));
        assertEquals(Map.of("command", "pwd"), payload.get("tool_args"));
    }

    @Test
    void afterToolCallReadFilePrefersContentAndLineCount() {
        RecordingSession session = new RecordingSession();
        ReadFileResult result = new ReadFileResult("     1\tline1\n     2\tline2", 2);
        ToolCallInputs inputs = toolInputs("read_file", Map.of("file_path", "/tmp/a.txt"), result);

        new ToolTrackingRail().afterToolCall(context(session, inputs)).toCompletableFuture().join();

        Map<String, Object> payload = payload(session.onlyOutput());
        assertEquals("     1\tline1\n     2\tline2", payload.get("tool_result"));
        assertEquals(2, payload.get("line_count"));
    }

    @Test
    void afterToolCallReadFileDecodesByteContent() {
        RecordingSession session = new RecordingSession();
        ReadFileResult result = new ReadFileResult("line".getBytes(StandardCharsets.UTF_8), "3");
        ToolCallInputs inputs = toolInputs("read_file", Map.of("file_path", "/tmp/a.txt"), result);

        new ToolTrackingRail().afterToolCall(context(session, inputs)).toCompletableFuture().join();

        Map<String, Object> payload = payload(session.onlyOutput());
        assertEquals("line", payload.get("tool_result"));
        assertEquals(3, payload.get("line_count"));
    }

    @Test
    void afterToolCallNonReadFileKeepsStringifiedResult() {
        RecordingSession session = new RecordingSession();
        ToolCallInputs inputs = toolInputs("bash", Map.of("command", "pwd"), new CommandResult("/tmp"));

        new ToolTrackingRail().afterToolCall(context(session, inputs)).toCompletableFuture().join();

        Map<String, Object> payload = payload(session.onlyOutput());
        assertTrue(String.valueOf(payload.get("tool_result")).contains("stdout='/tmp'"));
        assertFalse(payload.containsKey("line_count"));
    }

    @Test
    void invalidJsonToolArgsRemainString() {
        RecordingSession session = new RecordingSession();
        ToolCallInputs inputs = toolInputs("bash", "{not-json", null);

        new ToolTrackingRail().beforeToolCall(context(session, inputs)).toCompletableFuture().join();

        assertEquals("{not-json", payload(session.onlyOutput()).get("tool_args"));
    }

    @Test
    void nullSessionDoesNothing() {
        ToolCallInputs inputs = toolInputs("bash", Map.of("command", "pwd"), null);
        AgentCallbackContext context = context(null, inputs);

        new ToolTrackingRail().beforeToolCall(context).toCompletableFuture().join();
        new ToolTrackingRail().afterToolCall(context).toCompletableFuture().join();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(OutputSchema output) {
        return (Map<String, Object>) assertInstanceOf(Map.class, output.getPayload());
    }

    private static AgentCallbackContext context(AgentSessionApi session, Object inputs) {
        AgentCallbackContext context = new AgentCallbackContext();
        context.setSession(session);
        context.setInputs(inputs);
        return context;
    }

    private static ToolCallInputs toolInputs(String toolName, Object args, Object result) {
        ToolCallInputs inputs = new ToolCallInputs();
        inputs.setToolName(toolName);
        inputs.setToolArgs(args);
        inputs.setToolResult(result);
        return inputs;
    }

    private static final class ReadFileResult {
        private final Map<String, Object> data = new LinkedHashMap<>();

        private ReadFileResult(Object content, Object lineCount) {
            data.put("content", content);
            data.put("line_count", lineCount);
        }

        public Map<String, Object> getData() {
            return data;
        }

        @Override
        public String toString() {
            return "success=True data={'content': 'line1\\nline2'}";
        }
    }

    private record CommandResult(String stdout) {
        @Override
        public String toString() {
            return "namespace(stdout='" + stdout + "')";
        }
    }

    private static final class RecordingSession implements AgentSessionApi {
        private final List<Object> outputs = new ArrayList<>();

        private OutputSchema onlyOutput() {
            assertEquals(1, outputs.size());
            return (OutputSchema) assertInstanceOf(OutputSchema.class, outputs.get(0));
        }

        @Override
        public String getSessionId() {
            return "session-1";
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> data) {
        }

        @Override
        public void writeStream(Object data) {
            outputs.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return outputs.iterator();
        }
    }
}
