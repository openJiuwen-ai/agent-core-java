/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.StreamController.TeamOutputChunk;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for stream logger chunk aggregation and file output.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/agent_teams/monitor/stream_logger.py}.</p>
 */
class TeamStreamLoggerTest {

    @TempDir
    Path tempDir;

    @Test
    void accumulatesConsecutiveLlmOutputAndFlushesOnDiscreteChunk() throws Exception {
        Path log = tempDir.resolve("stream.log");
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "Hello ")));
        logger.feed(teamChunk("llm_output", Map.of("content", "world")));
        logger.feed(teamChunk("llm_output", Map.of("content", "!")));
        logger.feed(teamChunk("tool_call", Map.of("tool_name", "read", "tool_args", "{}")));
        logger.flush();

        List<Header> headers = headers(log);
        assertThat(headers).hasSize(2);
        assertThat(headers.get(0)).isEqualTo(new Header("INFO", "member=leader role=leader category=text"));
        assertThat(headers.get(1).level()).isEqualTo("DEBUG");
        assertThat(headers.get(1).tail()).contains("category=tool_call");
        assertThat(text(log)).contains("  | Hello world!");
    }

    @Test
    void interleavedMembersAggregatePerSource() throws Exception {
        Path log = tempDir.resolve("stream.log");
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "L1 "), "leader", TeamRole.LEADER));
        logger.feed(teamChunk("llm_reasoning", Map.of("content", "G1 "), "gamma", TeamRole.TEAMMATE));
        logger.feed(teamChunk("llm_output", Map.of("content", "L2 "), "leader", TeamRole.LEADER));
        logger.feed(teamChunk("llm_reasoning", Map.of("content", "G2"), "gamma", TeamRole.TEAMMATE));
        logger.feed(teamChunk("llm_output", Map.of("content", "L3"), "leader", TeamRole.LEADER));
        logger.flush();

        assertThat(headers(log)).hasSize(2);
        assertThat(text(log)).contains("  | L1 L2 L3");
        assertThat(text(log)).contains("  | G1 G2");
        assertThat(text(log)).contains("member=leader role=leader category=text");
        assertThat(text(log)).contains("member=gamma role=teammate category=reasoning");
    }

    @Test
    void sameMemberDifferentRoleTrackedSeparately() throws Exception {
        Path log = tempDir.resolve("stream.log");
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "a"), "alex", TeamRole.LEADER));
        logger.feed(teamChunk("llm_output", Map.of("content", "b"), "alex", TeamRole.TEAMMATE));
        logger.flush();

        assertThat(headers(log)).extracting(Header::tail).containsExactlyInAnyOrder(
                "member=alex role=leader category=text",
                "member=alex role=teammate category=text"
        );
    }

    @Test
    void answerDedupIsPerSource() throws Exception {
        Path log = tempDir.resolve("stream.log");
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "leader text"), "leader", TeamRole.LEADER));
        logger.feed(teamChunk("answer", Map.of("content", "leader text"), "leader", TeamRole.LEADER));
        logger.feed(teamChunk("answer", Map.of("content", "gamma answer"), "gamma", TeamRole.TEAMMATE));
        logger.flush();

        assertThat(headers(log)).hasSize(2);
        assertThat(text(log)).contains("  | leader text");
        assertThat(text(log)).contains("  | gamma answer");
    }

    @Test
    void routesDiscreteLevelsAndRuntimeReady() throws Exception {
        Path log = tempDir.resolve("stream.log");
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_reasoning", Map.of("content", "thinking")));
        logger.feed(teamChunk("__interaction__", Map.of("interaction_id", "c1")));
        logger.feed(teamChunk("message", Map.of(
                "event_type", "team.runtime_ready",
                "team_name", "spec_team",
                "session_id", "sess_1",
                "activation_kind", "create"
        )));
        logger.flush();

        List<Header> headers = headers(log);
        assertThat(headers).extracting(Header::level).containsExactly("DEBUG", "WARN", "INFO");
        assertThat(headers.get(2).tail()).contains("category=runtime_ready");
        assertThat(text(log)).contains("team=spec_team session=sess_1 activation=create");
    }

    @Test
    void toolSummariesAndTruncationMatchPythonShape() throws Exception {
        Path log = tempDir.resolve("stream.log");
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("tool_call", Map.of("tool_update", Map.of("name", "send_message"))));
        logger.feed(teamChunk("tool_update", Map.of("tool_update", Map.of(
                "tool_name", "send_message",
                "tool_call_id", "tool-abc",
                "arguments", "{\"content\":\"hello\"}",
                "status", "in_progress"
        ))));
        logger.feed(teamChunk("tool_result", Map.of("tool_name", "read", "tool_result", "x".repeat(5000))));
        logger.flush();

        String content = text(log);
        assertThat(content).contains("tool_update");
        assertThat(content).contains("tool_name=send_message");
        assertThat(content).contains("status=in_progress");
        assertThat(content).contains("tool_call_id=tool-abc");
        assertThat(content).contains("\u9225?(truncated)");
        assertThat(content.chars().filter(ch -> ch == 'x').count()).isLessThan(5000);
    }

    @Test
    void llmOutputNeverTruncatedAndMarkdownLinesArePreserved() throws Exception {
        Path log = tempDir.resolve("stream.log");
        String big = "x".repeat(10_000);
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(teamChunk("llm_output", Map.of("content", "# Title\n\n- a\n- b" + big)));
        logger.flush();

        String content = text(log);
        assertThat(content).contains("  | # Title\n  | \n  | - a\n  | - b" + big);
        assertThat(content).doesNotContain("(truncated)");
    }

    @Test
    void plainOutputSchemaChunksAreSkippedButCounted() throws Exception {
        Path log = tempDir.resolve("stream.log");
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(new OutputSchema("llm_output", 0, Map.of("content", "untagged text")));
        logger.feed(new OutputSchema("message", 0, Map.of("traceId", "abc")));
        logger.flush();

        assertThat(headers(log)).isEmpty();
        assertThat(text(log)).contains("stream end, 2 chunks");
    }

    @Test
    void feedAndFlushNeverRaiseAndFileCloses() throws Exception {
        Path log = tempDir.resolve("nested").resolve("dir").resolve("stream.log");
        TeamStreamLogger logger = new TeamStreamLogger(log);
        logger.feed(new ExplodingChunk());
        logger.feed(null);
        logger.feed(teamChunk("llm_output", Map.of("content", "still works")));
        logger.flush();
        logger.flush();

        assertThat(log).exists();
        assertThat(text(log)).contains("  | still works");
        assertThat(logger.isClosed()).isTrue();
    }

    private static TeamOutputChunk teamChunk(String type, Object payload) {
        return teamChunk(type, payload, "leader", TeamRole.LEADER);
    }

    private static TeamOutputChunk teamChunk(String type, Object payload, String member, TeamRole role) {
        return new TeamOutputChunk(type, 0, payload, member, role);
    }

    private static String text(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static List<Header> headers(Path path) throws IOException {
        List<Header> result = new ArrayList<>();
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
}
