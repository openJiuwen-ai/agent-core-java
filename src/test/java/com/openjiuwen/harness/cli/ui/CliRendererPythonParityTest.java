/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_renderer} in
 * {@code tests/cli/unit/test_renderer.py}.
 */
class CliRendererPythonParityTest {

    @TestFactory
    Collection<DynamicTest> rendererPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "TestRenderStream::test_write_terminal_uses_stdout_encoding_with_os_write",
                this::writeTerminalUsesStdoutEncodingWithOsWrite);
        add(tests, "TestRenderStream::test_llm_output_accumulated", this::llmOutputAccumulated);
        add(tests, "TestRenderStream::test_reasoning_not_in_result", this::reasoningNotInResult);
        add(tests, "TestRenderStream::test_answer_chunk_fallback", this::answerChunkFallback);
        add(tests, "TestRenderStream::test_answer_not_duplicated", this::answerNotDuplicated);
        add(tests, "TestRenderStream::test_reasoning_hidden_by_default", this::reasoningHiddenByDefault);
        add(tests, "TestRenderStream::test_reasoning_shown_when_enabled", this::reasoningShownWhenEnabled);
        add(tests, "TestRenderStream::test_empty_stream_warning", this::emptyStreamWarning);
        add(tests, "TestRenderStream::test_invisible_stream_reports_chunk_types",
                this::invisibleStreamReportsChunkTypes);
        add(tests, "TestRenderStream::test_controller_task_failed_rendered",
                this::controllerTaskFailedRendered);
        add(tests, "TestRenderStream::test_message_chunk_rendered", this::messageChunkRendered);
        add(tests, "TestRenderStream::test_message_chunk_starts_on_new_line_after_llm_output",
                this::messageChunkStartsOnNewLineAfterLlmOutput);
        add(tests, "TestRenderStream::test_interaction_callback", this::interactionCallback);
        add(tests, "TestRenderStream::test_tool_call_rendered", this::toolCallRendered);
        add(tests, "TestRenderStream::test_tool_result_rendered", this::toolResultRendered);
        add(tests, "TestRenderStream::test_tool_result_rendered_uses_structured_line_count",
                this::toolResultRenderedUsesStructuredLineCount);
        add(tests, "TestRenderStream::test_todo_tool_renders_checkboxes", this::todoToolRendersCheckboxes);
        add(tests, "TestRenderStream::test_todo_modify_rerenders_cached_todos",
                this::todoModifyRerendersCachedTodos);
        add(tests, "TestRenderStream::test_green_bullet_on_llm_output", this::greenBulletOnLlmOutput);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void writeTerminalUsesStdoutEncodingWithOsWrite() {
        Rendered rendered = render(chunks(chunk("llm_output", payload("content", "中文"))));

        assertTrue(rendered.terminal().contains("中文"));
        assertEquals("中文", rendered.result().text());
    }

    private void llmOutputAccumulated() {
        Rendered rendered = render(chunks(
                chunk("llm_output", payload("content", "Hello ")),
                chunk("llm_output", payload("content", "World"))
        ));

        assertEquals("Hello World", rendered.result().text());
    }

    private void reasoningNotInResult() {
        Rendered rendered = render(chunks(
                chunk("llm_reasoning", payload("content", "thinking...")),
                chunk("llm_output", payload("content", "Answer"))
        ));

        assertEquals("Answer", rendered.result().text());
        assertFalse(rendered.result().text().contains("thinking"));
    }

    private void answerChunkFallback() {
        Rendered rendered = render(chunks(chunk("answer", payload("output", "Final answer"))));

        assertTrue(rendered.result().text().contains("Final answer"));
    }

    private void answerNotDuplicated() {
        Rendered rendered = render(chunks(
                chunk("llm_output", payload("content", "Hello")),
                chunk("answer", payload("output", "Hello"))
        ));

        assertEquals("Hello", rendered.result().text());
    }

    private void reasoningHiddenByDefault() {
        Rendered rendered = render(chunks(
                chunk("llm_reasoning", payload("content", "thinking...")),
                chunk("llm_output", payload("content", "Answer"))
        ));

        assertEquals("Answer", rendered.result().text());
        assertFalse(rendered.console().contains("thinking"));
    }

    private void reasoningShownWhenEnabled() {
        Rendered rendered = render(chunks(
                chunk("llm_reasoning", payload("content", "thinking...")),
                chunk("llm_output", payload("content", "Answer"))
        ), true);

        assertEquals("Answer", rendered.result().text());
        assertTrue(rendered.console().contains("thinking"));
    }

    private void emptyStreamWarning() {
        Rendered rendered = render(List.of().iterator());

        assertEquals("", rendered.result().text());
        assertTrue(rendered.console().contains("No output")
                || rendered.console().toLowerCase().contains("no output"));
    }

    private void invisibleStreamReportsChunkTypes() {
        Rendered rendered = render(chunks(chunk("workflow_final", payload("content", "internal only"))));

        assertEquals("", rendered.result().text());
        assertTrue(rendered.console().contains("No visible output received"));
        assertTrue(rendered.console().contains("workflow_final"));
    }

    private void controllerTaskFailedRendered() {
        Rendered rendered = render(chunks(chunk(
                "controller_output",
                "type=<EventType.TASK_FAILED: 'task_failed'> "
                        + "data=[TextDataFrame(type='text', text=\"model call failed: 401\")]"
        )));

        assertEquals("", rendered.result().text());
        assertTrue(rendered.console().contains("model call failed: 401"));
    }

    private void messageChunkRendered() {
        Rendered rendered = render(chunks(chunk("message", "Reading file...")));

        assertTrue(rendered.console().contains("Reading file"));
    }

    private void messageChunkStartsOnNewLineAfterLlmOutput() {
        Rendered rendered = render(chunks(
                chunk("llm_output", payload("content", "Line without newline")),
                chunk("message", payload("content", "CI gate check"))
        ));

        assertTrue(rendered.terminal().contains("Line without newline" + System.lineSeparator()));
        assertTrue(rendered.console().contains("CI gate check"));
    }

    private void interactionCallback() {
        List<String> calls = new ArrayList<>();
        Rendered rendered = render(
                chunks(chunk("__interaction__", new InteractionOutput("q1", "Yes?"))),
                false,
                (interactionId, question) -> calls.add(interactionId + ":" + question)
        );

        assertEquals(List.of("q1:Yes?"), calls);
        assertEquals(1, rendered.result().pendingInteractions().size());
        assertEquals("q1", rendered.result().pendingInteractions().getFirst().interactionId());
        assertEquals("Yes?", rendered.result().pendingInteractions().getFirst().request());
    }

    private void toolCallRendered() {
        Rendered rendered = render(chunks(chunk("tool_call", payload(
                "tool_name", "read_file",
                "tool_args", payload("file_path", "/src/main.py")
        ))));

        assertTrue(rendered.console().contains("Read"));
        assertTrue(rendered.console().contains("main.py"));
    }

    private void toolResultRendered() {
        Rendered rendered = render(chunks(chunk("tool_result", payload(
                "tool_name", "read_file",
                "tool_args", payload(),
                "tool_result", "line1\nline2\nline3\n"
        ))));

        assertTrue(rendered.console().contains("Read 3 lines"));
    }

    private void toolResultRenderedUsesStructuredLineCount() {
        Rendered rendered = render(chunks(chunk("tool_result", payload(
                "tool_name", "read_file",
                "tool_args", payload(),
                "tool_result", "line one\nline two\nline three\n",
                "line_count", 50
        ))));

        assertTrue(rendered.console().contains("Read 50 lines"));
    }

    private void todoToolRendersCheckboxes() {
        String rawDict = "{'message': 'Successfully created 2 task(s):\\n"
                + "  [>] task_id: abc , content: Task A\\n"
                + "  [ ] task_id: def , content: Task B'}";
        Rendered rendered = render(chunks(chunk("tool_result", payload(
                "tool_name", "todo_create",
                "tool_args", payload(),
                "tool_result", rawDict
        ))));

        assertTrue(rendered.console().contains("Task A"));
        assertTrue(rendered.console().contains("Task B"));
    }

    private void todoModifyRerendersCachedTodos() {
        String createResult = "{'message': 'Successfully created 2 task(s):\\n"
                + "  [>] task_id: a , content: Task A\\n"
                + "  [ ] task_id: b , content: Task B'}";
        String modifyResult = "{'message': 'Successfully updated 1 task(s)'}";
        Rendered rendered = render(chunks(
                chunk("tool_result", payload(
                        "tool_name", "todo_create",
                        "tool_args", payload(),
                        "tool_result", createResult
                )),
                chunk("tool_result", payload(
                        "tool_name", "todo_modify",
                        "tool_args", payload(
                                "action", "append",
                                "todos", List.of(payload(
                                        "id", "c",
                                        "content", "Task C",
                                        "activeForm", "Task C",
                                        "status", "pending"
                                ))
                        ),
                        "tool_result", modifyResult
                ))
        ));

        assertTrue(rendered.console().contains("Task A"));
        assertTrue(rendered.console().contains("Task B"));
        assertTrue(rendered.console().contains("Task C"));
    }

    private void greenBulletOnLlmOutput() {
        Rendered rendered = render(chunks(chunk("llm_output", payload("content", "Hello"))));

        assertEquals("Hello", rendered.result().text());
        assertTrue(rendered.terminal().startsWith("\033[92m"));
    }

    private static Rendered render(Iterator<?> chunks) {
        return render(chunks, false);
    }

    private static Rendered render(Iterator<?> chunks, boolean showReasoning) {
        return render(chunks, showReasoning, null);
    }

    private static Rendered render(
            Iterator<?> chunks,
            boolean showReasoning,
            CliRenderer.InteractionHandler interactionHandler
    ) {
        ByteArrayOutputStream terminalBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream consoleBuffer = new ByteArrayOutputStream();
        PrintStream terminal = new PrintStream(terminalBuffer, true, StandardCharsets.UTF_8);
        PrintStream console = new PrintStream(consoleBuffer, true, StandardCharsets.UTF_8);
        CliRenderer.RenderResult result = new CliRenderer()
                .renderStream(chunks, terminal, console, interactionHandler, showReasoning);
        return new Rendered(
                result,
                terminalBuffer.toString(StandardCharsets.UTF_8),
                consoleBuffer.toString(StandardCharsets.UTF_8)
        );
    }

    @SafeVarargs
    private static Iterator<Map<String, Object>> chunks(Map<String, Object>... chunks) {
        return List.of(chunks).iterator();
    }

    private static Map<String, Object> chunk(String type, Object payload) {
        return payload("type", type, "payload", payload);
    }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private record Rendered(CliRenderer.RenderResult result, String terminal, String console) {
    }

    private static final class InteractionOutput {
        private final String id;
        private final String value;

        private InteractionOutput(String id, String value) {
            this.id = id;
            this.value = value;
        }
    }
}
