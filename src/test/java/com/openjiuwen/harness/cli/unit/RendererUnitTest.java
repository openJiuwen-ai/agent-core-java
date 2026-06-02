/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.ui.CliRenderer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CLI stream renderer.
 * <p>
 * Mirrors Python's {@code test_renderer} in
 * {@code tests.cli.unit.test_renderer}.
 */
class RendererUnitTest {

    @Test
    void writeTerminalUsesStdoutEncodingWithOsWrite() {
        RenderCapture capture = render(List.of(
                chunk("llm_output", 0, Map.of("content", "中文"))));

        assertTrue(capture.terminal().contains("中文"));
    }

    @Test
    void llmOutputAccumulated() {
        RenderCapture capture = render(List.of(
                chunk("llm_output", 0, Map.of("content", "Hello ")),
                chunk("llm_output", 1, Map.of("content", "World"))));

        assertEquals("Hello World", capture.result().text());
    }

    @Test
    void reasoningNotInResult() {
        RenderCapture capture = render(List.of(
                chunk("llm_reasoning", 0, Map.of("content", "thinking...")),
                chunk("llm_output", 1, Map.of("content", "Answer"))));

        assertEquals("Answer", capture.result().text());
        assertFalse(capture.result().text().contains("thinking"));
    }

    @Test
    void answerChunkFallback() {
        RenderCapture capture = render(List.of(
                chunk("answer", 0, Map.of("output", "Final answer"))));

        assertTrue(capture.result().text().contains("Final answer"));
    }

    @Test
    void answerNotDuplicated() {
        RenderCapture capture = render(List.of(
                chunk("llm_output", 0, Map.of("content", "Hello")),
                chunk("answer", 1, Map.of("output", "Hello"))));

        assertEquals("Hello", capture.result().text());
    }

    @Test
    void reasoningHiddenByDefault() {
        RenderCapture capture = render(List.of(
                chunk("llm_reasoning", 0, Map.of("content", "thinking...")),
                chunk("llm_output", 1, Map.of("content", "Answer"))));

        assertEquals("Answer", capture.result().text());
        assertFalse(capture.console().contains("thinking"));
    }

    @Test
    void reasoningShownWhenEnabled() {
        RenderCapture capture = render(
                List.of(
                        chunk("llm_reasoning", 0, Map.of("content", "thinking...")),
                        chunk("llm_output", 1, Map.of("content", "Answer"))),
                true);

        assertEquals("Answer", capture.result().text());
        assertTrue(capture.console().contains("thinking"));
    }

    @Test
    void emptyStreamWarning() {
        RenderCapture capture = render(List.of());

        assertEquals("", capture.result().text());
        assertTrue(capture.console().toLowerCase().contains("no output"));
    }

    @Test
    void invisibleStreamReportsChunkTypes() {
        RenderCapture capture = render(List.of(
                chunk("workflow_final", 0, Map.of("content", "internal only"))));

        assertEquals("", capture.result().text());
        assertTrue(capture.console().contains("No visible output received"));
        assertTrue(capture.console().contains("workflow_final"));
    }

    @Test
    void controllerTaskFailedRendered() {
        RenderCapture capture = render(List.of(
                chunk("controller_output", 0,
                        "type=<EventType.TASK_FAILED: 'task_failed'> "
                                + "data=[TextDataFrame(type='text', text=\"model call failed: 401\")]")));

        assertEquals("", capture.result().text());
        assertTrue(capture.console().contains("model call failed: 401"));
    }

    @Test
    void messageChunkRendered() {
        RenderCapture capture = render(List.of(
                chunk("message", 0, Map.of("content", "Reading file..."))));

        assertTrue(capture.console().contains("Reading file"));
    }

    @Test
    void messageChunkStartsOnNewLineAfterLlmOutput() {
        RenderCapture capture = render(List.of(
                chunk("llm_output", 0, Map.of("content", "Line without newline")),
                chunk("message", 1, Map.of("content", "CI gate check"))));

        assertTrue(capture.terminal().contains("Line without newline"));
        assertTrue(capture.terminal().endsWith(System.lineSeparator()));
        assertTrue(capture.console().contains("CI gate check"));
    }

    @Test
    void interactionCallback() {
        AtomicReference<String> callbackCapture = new AtomicReference<>("");
        CliRenderer.InteractionHandler handler = (id, value) ->
                callbackCapture.set(id + ":" + value);

        RenderCapture capture = render(
                List.of(chunk("__interaction__", 0, new InteractionPayload("q1", "Yes?"))),
                false,
                handler);

        assertEquals("q1:Yes?", callbackCapture.get());
        assertEquals(1, capture.result().pendingInteractions().size());
    }

    @Test
    void toolCallRendered() {
        RenderCapture capture = render(List.of(
                chunk("tool_call", 0, Map.of(
                        "tool_name", "read_file",
                        "tool_args", Map.of("file_path", "/src/main.py")))));

        assertTrue(capture.console().contains("Read"));
        assertTrue(capture.console().contains("main.py"));
    }

    @Test
    void toolResultRendered() {
        RenderCapture capture = render(List.of(
                chunk("tool_result", 0, Map.of(
                        "tool_name", "read_file",
                        "tool_args", Map.of(),
                        "tool_result", "line1\nline2\nline3\n"))));

        assertTrue(capture.console().contains("Read 3 lines"));
    }

    @Test
    void toolResultRenderedUsesStructuredLineCount() {
        RenderCapture capture = render(List.of(
                chunk("tool_result", 0, Map.of(
                        "tool_name", "read_file",
                        "tool_args", Map.of(),
                        "tool_result", "line one\nline two\nline three\n",
                        "line_count", 50))));

        assertTrue(capture.console().contains("Read 50 lines"));
    }

    @Test
    void todoToolRendersCheckboxes() {
        RenderCapture capture = render(List.of(
                chunk("tool_result", 0, Map.of(
                        "tool_name", "todo_create",
                        "tool_args", Map.of(),
                        "tool_result", "{'message': 'Successfully created 2 task(s):\\n"
                                + "  [>] task_id: abc , content: Task A\\n"
                                + "  [ ] task_id: def , content: Task B'}"))));

        assertTrue(capture.console().contains("Task A"));
        assertTrue(capture.console().contains("Task B"));
    }

    @Test
    void todoModifyRerendersCachedTodos() {
        RenderCapture capture = render(List.of(
                chunk("tool_result", 0, Map.of(
                        "tool_name", "todo_create",
                        "tool_args", Map.of(),
                        "tool_result", "{'message': 'Successfully created 2 task(s):\\n"
                                + "  [>] task_id: a , content: Task A\\n"
                                + "  [ ] task_id: b , content: Task B'}")),
                chunk("tool_result", 1, Map.of(
                        "tool_name", "todo_modify",
                        "tool_args", Map.of(
                                "action", "append",
                                "todos", List.of(
                                        Map.of(
                                                "id", "c",
                                                "content", "Task C",
                                                "activeForm", "Task C",
                                                "status", "pending"))),
                        "tool_result", "{'message': 'Successfully updated 1 task(s)'}"))));

        assertTrue(capture.console().contains("Task A"));
        assertTrue(capture.console().contains("Task B"));
        assertTrue(capture.console().contains("Task C"));
    }

    @Test
    void greenBulletOnLlmOutput() {
        RenderCapture capture = render(List.of(
                chunk("llm_output", 0, Map.of("content", "Hello"))));

        assertEquals("Hello", capture.result().text());
        assertTrue(capture.terminal().contains("\033[92m● \033[0m"));
    }

    private static RenderCapture render(List<FakeChunk> chunks) {
        return render(chunks, false, null);
    }

    private static RenderCapture render(List<FakeChunk> chunks, boolean showReasoning) {
        return render(chunks, showReasoning, null);
    }

    private static RenderCapture render(
            List<FakeChunk> chunks,
            boolean showReasoning,
            CliRenderer.InteractionHandler handler) {
        CliRenderer renderer = new CliRenderer();
        ByteArrayOutputStream terminalOut = new ByteArrayOutputStream();
        ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
        PrintStream terminal = new PrintStream(terminalOut, true, StandardCharsets.UTF_8);
        PrintStream console = new PrintStream(consoleOut, true, StandardCharsets.UTF_8);
        CliRenderer.RenderResult result = renderer.renderStream(
                chunks.iterator(),
                terminal,
                console,
                handler,
                showReasoning);
        terminal.flush();
        console.flush();
        return new RenderCapture(
                result,
                terminalOut.toString(StandardCharsets.UTF_8),
                consoleOut.toString(StandardCharsets.UTF_8));
    }

    private static FakeChunk chunk(String type, int index, Object payload) {
        return new FakeChunk(type, index, payload);
    }

    private record FakeChunk(String type, int index, Object payload) {
    }

    private record InteractionPayload(String id, Object value) {
    }

    private record RenderCapture(
            CliRenderer.RenderResult result,
            String terminal,
            String console) {
    }
}
