/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.StreamController.TeamOutputChunk;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.monitor.test_stream_logger} in
 * {@code tests/unit_tests/agent_teams/monitor/test_stream_logger.py}.</p>
 */
class TeamStreamLoggerPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_accumulates_consecutive_llm_output",
            "test_accumulates_consecutive_reasoning_at_debug",
            "test_interleaved_members_aggregate_per_source",
            "test_same_member_different_role_tracked_separately",
            "test_flush_emits_trailing_runs",
            "test_answer_deduped_after_llm_output",
            "test_answer_dedup_is_per_source",
            "test_answer_fallback_when_no_llm_output",
            "test_level_routing[llm_output-payload0-INFO]",
            "test_level_routing[answer-payload1-INFO]",
            "test_level_routing[llm_reasoning-payload2-DEBUG]",
            "test_level_routing[tool_call-payload3-DEBUG]",
            "test_level_routing[tool_result-payload4-DEBUG]",
            "test_level_routing[__interaction__-payload5-WARN]",
            "test_level_routing[controller_output-task failed-WARN]",
            "test_level_routing[message-payload7-INFO]",
            "test_level_routing[todo.updated-payload8-INFO]",
            "test_level_routing[mystery_type-payload9-INFO]",
            "test_runtime_ready_special_cased",
            "test_plain_message_vs_runtime_ready",
            "test_tool_result_truncated",
            "test_llm_output_never_truncated",
            "test_multiline_markdown_preserved",
            "test_plain_outputschema_chunk_is_skipped",
            "test_tool_call_empty_fields_falls_back_to_payload",
            "test_tool_update_extracts_nested_fields",
            "test_tool_result_empty_fields_falls_back_to_payload",
            "test_feed_never_raises",
            "test_feed_swallows_internal_exception",
            "test_flush_never_raises",
            "test_header_format_contract",
            "test_creates_parent_dirs",
            "test_flush_closes_file"
    );

    @TestFactory
    Collection<DynamicTest> pythonTeamStreamLoggerCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) throws Exception {
        switch (name) {
            case "test_accumulates_consecutive_llm_output" -> accumulatesConsecutiveLlmOutput();
            case "test_accumulates_consecutive_reasoning_at_debug" -> accumulatesConsecutiveReasoningAtDebug();
            case "test_interleaved_members_aggregate_per_source" -> interleavedMembersAggregatePerSource();
            case "test_same_member_different_role_tracked_separately" -> sameMemberDifferentRoleTrackedSeparately();
            case "test_flush_emits_trailing_runs" -> flushEmitsTrailingRuns();
            case "test_answer_deduped_after_llm_output" -> answerDedupedAfterLlmOutput();
            case "test_answer_dedup_is_per_source" -> answerDedupIsPerSource();
            case "test_answer_fallback_when_no_llm_output" -> answerFallbackWhenNoLlmOutput();
            case "test_level_routing[llm_output-payload0-INFO]" ->
                    levelRouting("llm_output", Map.of("content", "hi"), "INFO");
            case "test_level_routing[answer-payload1-INFO]" ->
                    levelRouting("answer", Map.of("content", "hi"), "INFO");
            case "test_level_routing[llm_reasoning-payload2-DEBUG]" ->
                    levelRouting("llm_reasoning", Map.of("content", "thinking"), "DEBUG");
            case "test_level_routing[tool_call-payload3-DEBUG]" ->
                    levelRouting("tool_call", Map.of("tool_name", "read", "tool_args", "{}"), "DEBUG");
            case "test_level_routing[tool_result-payload4-DEBUG]" ->
                    levelRouting("tool_result", Map.of("tool_name", "read", "tool_result", "ok"), "DEBUG");
            case "test_level_routing[__interaction__-payload5-WARN]" ->
                    levelRouting("__interaction__", Map.of("interaction_id", "c1"), "WARN");
            case "test_level_routing[controller_output-task failed-WARN]" ->
                    levelRouting("controller_output", "task failed", "WARN");
            case "test_level_routing[message-payload7-INFO]" ->
                    levelRouting("message", Map.of("content", "sys note"), "INFO");
            case "test_level_routing[todo.updated-payload8-INFO]" ->
                    levelRouting("todo.updated", Map.of("items", "[]"), "INFO");
            case "test_level_routing[mystery_type-payload9-INFO]" ->
                    levelRouting("mystery_type", Map.of("content", "x"), "INFO");
            case "test_runtime_ready_special_cased" -> runtimeReadySpecialCased();
            case "test_plain_message_vs_runtime_ready" -> plainMessageVsRuntimeReady();
            case "test_tool_result_truncated" -> toolResultTruncated();
            case "test_llm_output_never_truncated" -> llmOutputNeverTruncated();
            case "test_multiline_markdown_preserved" -> multilineMarkdownPreserved();
            case "test_plain_outputschema_chunk_is_skipped" -> plainOutputSchemaChunkIsSkipped();
            case "test_tool_call_empty_fields_falls_back_to_payload" -> toolCallEmptyFieldsFallsBackToPayload();
            case "test_tool_update_extracts_nested_fields" -> toolUpdateExtractsNestedFields();
            case "test_tool_result_empty_fields_falls_back_to_payload" -> toolResultEmptyFieldsFallsBackToPayload();
            case "test_feed_never_raises" -> feedNeverRaises();
            case "test_feed_swallows_internal_exception" -> feedSwallowsInternalException();
            case "test_flush_never_raises" -> flushNeverRaises();
            case "test_header_format_contract" -> headerFormatContract();
            case "test_creates_parent_dirs" -> createsParentDirs();
            case "test_flush_closes_file" -> flushClosesFile();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private void accumulatesConsecutiveLlmOutput() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "Hello ")));
        logger.feed(teamChunk("llm_output", Map.of("content", "world")));
        logger.feed(teamChunk("llm_output", Map.of("content", "!")));
        logger.feed(teamChunk("tool_call", Map.of("tool_name", "read", "tool_args", "{}")));
        logger.flush();

        List<Header> headers = headers(log);
        assertEquals(2, headers.size());
        assertEquals(new Header("INFO", "member=leader role=leader category=text"), headers.get(0));
        assertEquals("DEBUG", headers.get(1).level());
        assertTrue(headers.get(1).tail().contains("category=tool_call"));
        assertTrue(text(log).contains("  | Hello world!"));
    }

    private void accumulatesConsecutiveReasoningAtDebug() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_reasoning", Map.of("content", "step one ")));
        logger.feed(teamChunk("llm_reasoning", Map.of("content", "step two")));
        logger.flush();

        List<Header> headers = headers(log);
        assertEquals(1, headers.size());
        assertEquals("DEBUG", headers.get(0).level());
        assertTrue(headers.get(0).tail().contains("category=reasoning"));
        assertTrue(text(log).contains("  | step one step two"));
    }

    private void interleavedMembersAggregatePerSource() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "L1 "), "leader", TeamRole.LEADER));
        logger.feed(teamChunk("llm_reasoning", Map.of("content", "G1 "), "gamma", TeamRole.TEAMMATE));
        logger.feed(teamChunk("llm_output", Map.of("content", "L2 "), "leader", TeamRole.LEADER));
        logger.feed(teamChunk("llm_reasoning", Map.of("content", "G2"), "gamma", TeamRole.TEAMMATE));
        logger.feed(teamChunk("llm_output", Map.of("content", "L3"), "leader", TeamRole.LEADER));
        logger.flush();

        List<Header> headers = headers(log);
        assertEquals(2, headers.size());
        Map<String, Header> byMember = new LinkedHashMap<>();
        for (Header header : headers) {
            byMember.put(header.tail().split(" ")[0], header);
        }
        assertEquals("INFO", byMember.get("member=leader").level());
        assertEquals("DEBUG", byMember.get("member=gamma").level());
        assertTrue(text(log).contains("  | L1 L2 L3"));
        assertTrue(text(log).contains("  | G1 G2"));
    }

    private void sameMemberDifferentRoleTrackedSeparately() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "a"), "alex", TeamRole.LEADER));
        logger.feed(teamChunk("llm_output", Map.of("content", "b"), "alex", TeamRole.TEAMMATE));
        logger.flush();

        Set<String> tails = headers(log).stream().map(Header::tail).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                "member=alex role=leader category=text",
                "member=alex role=teammate category=text"
        ), tails);
    }

    private void flushEmitsTrailingRuns() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "tail ")));
        logger.feed(teamChunk("llm_output", Map.of("content", "content")));

        assertEquals(List.of(), headers(log));

        logger.flush();
        assertEquals(1, headers(log).size());
        assertTrue(text(log).contains("  | tail content"));
    }

    private void answerDedupedAfterLlmOutput() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "the answer")));
        logger.feed(teamChunk("answer", Map.of("content", "the answer")));
        logger.flush();

        assertEquals(1, headers(log).size());
    }

    private void answerDedupIsPerSource() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "leader text"), "leader", TeamRole.LEADER));
        logger.feed(teamChunk("answer", Map.of("content", "gamma answer"), "gamma", TeamRole.TEAMMATE));
        logger.flush();

        assertEquals(2, headers(log).size());
        assertTrue(text(log).contains("  | gamma answer"));
    }

    private void answerFallbackWhenNoLlmOutput() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("answer", Map.of("content", "fallback answer")));
        logger.flush();

        List<Header> headers = headers(log);
        assertEquals(1, headers.size());
        assertEquals("INFO", headers.get(0).level());
        assertTrue(text(log).contains("  | fallback answer"));
    }

    private void levelRouting(String type, Object payload, String expectedLevel) throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk(type, payload));
        logger.flush();

        List<Header> headers = headers(log);
        assertEquals(1, headers.size());
        assertEquals(expectedLevel, headers.get(0).level());
    }

    private void runtimeReadySpecialCased() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("message", Map.of(
                "event_type", "team.runtime_ready",
                "team_name", "spec_team",
                "session_id", "sess_1",
                "activation_kind", "create"
        )));
        logger.flush();

        List<Header> headers = headers(log);
        assertEquals(1, headers.size());
        assertEquals("INFO", headers.get(0).level());
        assertTrue(headers.get(0).tail().contains("category=runtime_ready"));
        String content = text(log);
        assertTrue(content.contains("team=spec_team"));
        assertTrue(content.contains("session=sess_1"));
        assertTrue(content.contains("activation=create"));
    }

    private void plainMessageVsRuntimeReady() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("message", Map.of("content", "just a status line")));
        logger.flush();

        List<Header> headers = headers(log);
        assertEquals(1, headers.size());
        assertTrue(headers.get(0).tail().contains("category=message"));
        assertTrue(text(log).contains("  | just a status line"));
    }

    private void toolResultTruncated() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("tool_result", Map.of("tool_name", "read", "tool_result", "x".repeat(5000))));
        logger.flush();

        String content = text(log);
        assertTrue(content.contains("(truncated)"));
        assertTrue(content.chars().filter(ch -> ch == 'x').count() < 5000);
    }

    private void llmOutputNeverTruncated() throws Exception {
        Path log = logPath();
        String big = "x".repeat(10_000);
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", big)));
        logger.flush();

        String content = text(log);
        assertTrue(content.contains(big));
        assertFalse(content.contains("(truncated)"));
    }

    private void multilineMarkdownPreserved() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "# Title\n\n- a\n- b")));
        logger.flush();

        String content = text(log);
        assertTrue(content.contains("  | # Title\n  | \n  | - a\n  | - b"));
        assertFalse(content.contains("\\n"));
    }

    private void plainOutputSchemaChunkIsSkipped() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(new OutputSchema("llm_output", 0, Map.of("content", "untagged text")));
        logger.feed(new OutputSchema("message", 0, Map.of("traceId", "abc", "invokeId", "def")));
        logger.flush();

        assertEquals(List.of(), headers(log));
        assertTrue(text(log).contains("stream end, 2 chunks"));
    }

    private void toolCallEmptyFieldsFallsBackToPayload() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("tool_call", Map.of("tool_update", Map.of(
                "name", "send_message",
                "status", "in_progress"
        ))));
        logger.flush();

        String content = text(log);
        assertEquals(1, headers(log).size());
        assertEquals("DEBUG", headers(log).get(0).level());
        assertFalse(content.contains("tool_name="));
        assertTrue(content.contains("tool_update"));
    }

    private void toolUpdateExtractsNestedFields() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("tool_update", Map.of("tool_update", Map.of(
                "tool_name", "send_message",
                "tool_call_id", "tool-abc",
                "arguments", "{\"content\": \"hello\"}",
                "status", "in_progress"
        ))));
        logger.flush();

        List<Header> headers = headers(log);
        assertEquals(1, headers.size());
        assertEquals("DEBUG", headers.get(0).level());
        assertTrue(headers.get(0).tail().contains("category=tool_update"));
        String content = text(log);
        assertTrue(content.contains("tool_name=send_message"));
        assertTrue(content.contains("status=in_progress"));
        assertTrue(content.contains("tool_call_id=tool-abc"));
    }

    private void toolResultEmptyFieldsFallsBackToPayload() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("tool_result", Map.of("tool_update", Map.of(
                "name", "send_message",
                "status", "finish"
        ))));
        logger.flush();

        List<Header> headers = headers(log);
        assertEquals(1, headers.size());
        assertEquals("DEBUG", headers.get(0).level());
        assertTrue(text(log).contains("tool_update"));
    }

    private void feedNeverRaises() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);

        logger.feed(new ExplodingChunk());
        logger.feed(null);
        logger.feed(teamChunk("llm_output", Map.of("content", "still works")));
        logger.flush();

        assertTrue(text(log).contains("  | still works"));
    }

    private void feedSwallowsInternalException() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);

        logger.feed(teamChunk("mystery_type", new ExplodingPayload()));
        logger.feed(teamChunk("llm_output", Map.of("content", "still works")));
        logger.flush();

        String content = text(log);
        assertTrue(content.contains("feed error"));
        assertTrue(content.contains("  | still works"));
    }

    @SuppressWarnings("unchecked")
    private void flushNeverRaises() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "x")));

        Field runs = TeamStreamLogger.class.getDeclaredField("runs");
        runs.setAccessible(true);
        Map<Object, TeamStreamLogger.Run> map = (Map<Object, TeamStreamLogger.Run>) runs.get(logger);
        TeamStreamLogger.Run run = map.values().iterator().next();
        map.put(null, run);

        logger.flush();

        assertTrue(text(log).contains("flush error"));
        assertTrue(logger.isClosed());
    }

    private void headerFormatContract() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("tool_call", Map.of("tool_name", "read_file", "tool_args", "path")));
        logger.flush();

        String content = text(log);
        String firstLine = content.split("\\R")[0];
        assertTrue(firstLine.endsWith("[DEBUG] member=leader role=leader category=tool_call"));
        assertTrue(content.contains("\n  | "));
    }

    private void createsParentDirs() throws Exception {
        Path log = Files.createTempDirectory("team-stream-logger").resolve("nested").resolve("dir")
                .resolve("stream.log");
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "x")));
        logger.flush();

        assertTrue(Files.exists(log));
        assertTrue(text(log).contains("  | x"));
    }

    private void flushClosesFile() throws Exception {
        Path log = logPath();
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "x")));
        logger.flush();

        assertTrue(logger.isClosed());
    }

    private static TeamOutputChunk teamChunk(String type, Object payload) {
        return teamChunk(type, payload, "leader", TeamRole.LEADER);
    }

    private static TeamOutputChunk teamChunk(String type, Object payload, String member, TeamRole role) {
        return new TeamOutputChunk(type, 0, payload, member, role);
    }

    private static Path logPath() throws IOException {
        return Files.createTempDirectory("team-stream-logger").resolve("stream.log");
    }

    private static String text(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static List<Header> headers(Path path) throws IOException {
        List<Header> result = new ArrayList<>();
        if (!Files.exists(path)) {
            return result;
        }
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            for (String level : List.of("INFO", "DEBUG", "WARN")) {
                String tag = "[" + level + "] member=";
                int index = line.indexOf(tag);
                if (index >= 0) {
                    result.add(new Header(level, line.substring(index + ("[" + level + "] ").length())));
                    break;
                }
            }
        }
        return result;
    }

    private record Header(String level, String tail) {
    }

    private static final class ExplodingChunk {
        public String getType() {
            throw new RuntimeException("boom");
        }
    }

    private static final class ExplodingPayload {
        @Override
        public String toString() {
            throw new RuntimeException("classifier boom");
        }
    }
}
