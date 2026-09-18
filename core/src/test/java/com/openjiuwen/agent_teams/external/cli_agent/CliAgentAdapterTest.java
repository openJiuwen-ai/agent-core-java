/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external.cli_agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors focused adapter coverage from Python's external CLI tests.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.external.test_cli_adapter_injection} in
 * {@code tests/unit_tests/agent_teams/external/test_cli_adapter_injection.py}.</p>
 */
class CliAgentAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void testBuildAdapterClaudeStreamJson() throws Exception {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("claude");
        List<String> command = adapter.buildCommand();
        assertEquals("claude", command.get(0));
        assertTrue(command.contains("--dangerously-skip-permissions"));

        JsonNode framed = OBJECT_MAPPER.readTree(adapter.formatInput("hello"));
        assertEquals("user", framed.get("type").asText());
        assertEquals("hello", framed.get("message").get("content").asText());
    }

    @Test
    void testClaudeCompletionOnResultJson() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("claude");
        assertTrue(adapter.isTurnComplete("{\"type\":\"result\",\"subtype\":\"success\"}"));
        assertFalse(adapter.isTurnComplete("{\"type\":\"assistant\"}"));
        assertFalse(adapter.isTurnComplete("plain text"));
    }

    @Test
    void testGenericAdapterMarkerCompletion() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("generic");
        assertEquals("hi", adapter.formatInput("hi"));
        assertTrue(adapter.isTurnComplete("done <<END_OF_TURN>> now"));
        assertFalse(adapter.isTurnComplete("still working"));
    }

    @Test
    void testBuildAdapterCommandOverride() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("claude", List.of("/usr/local/bin/claude", "-x"));
        assertEquals(List.of("/usr/local/bin/claude", "-x"), adapter.buildCommand());
    }

    @Test
    void testBuildAdapterUnknownRaises() {
        assertThrows(BaseError.class, () -> CliAgentAdapter.buildAdapter("nope"));
    }

    @Test
    void testAvailableAdaptersIncludesKnownClis() {
        Set<String> names = Set.copyOf(CliAgentAdapter.availableAdapters());
        assertTrue(names.containsAll(Set.of("claude", "codex", "gemini", "openclaw", "hermes", "generic")));
    }

    @Test
    void testClaudeMcpLaunchArgsUseMcpConfigFlag() throws Exception {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("claude");
        List<String> args = adapter.mcpLaunchArgs("openjiuwen-team", List.of("openjiuwen-team-mcp"));
        assertEquals("--mcp-config", args.get(0));
        JsonNode config = OBJECT_MAPPER.readTree(args.get(1));
        JsonNode server = config.get("mcpServers").get("openjiuwen-team");
        assertEquals("openjiuwen-team-mcp", server.get("command").asText());
        assertEquals(0, server.get("args").size());
    }

    @Test
    void testCodexMcpLaunchArgsUseConfigOverride() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("codex");
        List<String> args = adapter.mcpLaunchArgs("openjiuwen-team", List.of("openjiuwen-team-mcp", "--flag"));
        assertEquals(List.of(
                "-c",
                "mcp_servers.openjiuwen_team.command=\"openjiuwen-team-mcp\"",
                "-c",
                "mcp_servers.openjiuwen_team.args=[\"--flag\"]"
        ), args);
    }

    @Test
    void testOneShotAdaptersHaveNoMcpLaunchInjection() {
        for (String name : List.of("openclaw", "hermes", "generic")) {
            CliAgentAdapter adapter = CliAgentAdapter.buildAdapter(name);
            assertEquals(List.of(), adapter.mcpLaunchArgs("t", List.of("openjiuwen-team-mcp")));
        }
    }

    @Test
    void testGeminiAdapterRegisteredAndBuildsHeadlessTurn() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("gemini");
        List<String> argv = adapter.buildTurnCommand("do it", "s", true);
        assertEquals(List.of("gemini", "-o", "stream-json", "-y"), argv.subList(0, 4));
        assertEquals(List.of("-p", "do it"), argv.subList(argv.size() - 2, argv.size()));
        assertFalse(adapter.supportsStdinInjection());
    }

    @Test
    void testSystemPromptArgsAndFlags() {
        CliAgentAdapter claude = CliAgentAdapter.buildAdapter("claude");
        assertTrue(claude.injectsSystemPromptViaArg());
        assertEquals(List.of("--append-system-prompt", "PERSONA"), claude.systemPromptArgs("PERSONA"));
        assertEquals(List.of(), claude.systemPromptArgs(""));

        CliAgentAdapter codex = CliAgentAdapter.buildAdapter("codex");
        assertEquals(List.of("-c", "developer_instructions=\"be terse\""), codex.systemPromptArgs("be terse"));

        for (String name : List.of("gemini", "openclaw", "hermes", "generic")) {
            CliAgentAdapter adapter = CliAgentAdapter.buildAdapter(name);
            assertFalse(adapter.injectsSystemPromptViaArg());
            assertEquals(List.of(), adapter.systemPromptArgs("PERSONA"));
        }
    }

    @Test
    void testCodexUsesJsonOutputAndTurnCompletedSentinel() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("codex");
        assertTrue(adapter.buildCommand().contains("--json"));
        assertTrue(adapter.isTurnComplete("{\"type\":\"turn.completed\"}"));
        assertFalse(adapter.isTurnComplete("{\"type\":\"item.completed\"}"));
    }

    @Test
    void testGeminiCrossTurnStartsThenResumes() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("gemini");
        List<String> first = adapter.buildTurnCommand("hi", "sid-1", true);
        assertTrue(first.contains("--session-id"));
        assertEquals("sid-1", first.get(first.indexOf("--session-id") + 1));
        assertFalse(first.contains("--resume"));

        List<String> later = adapter.buildTurnCommand("again", "sid-1", false);
        assertTrue(later.contains("--resume"));
        assertEquals("sid-1", later.get(later.indexOf("--resume") + 1));
        assertFalse(later.contains("--session-id"));
    }

    @Test
    void testRegisterCommands() {
        CliAgentAdapter gemini = CliAgentAdapter.buildAdapter("gemini");
        assertEquals(
                List.of("gemini", "mcp", "add", "openjiuwen-team", "openjiuwen-team-mcp"),
                gemini.mcpRegisterCommand("openjiuwen-team", List.of("openjiuwen-team-mcp"))
        );

        CliAgentAdapter hermes = CliAgentAdapter.buildAdapter("hermes");
        assertEquals(
                List.of("hermes", "mcp", "add", "openjiuwen-team", "--command", "openjiuwen-team-mcp"),
                hermes.mcpRegisterCommand("openjiuwen-team", List.of("openjiuwen-team-mcp"))
        );

        for (String name : List.of("claude", "codex")) {
            CliAgentAdapter adapter = CliAgentAdapter.buildAdapter(name);
            assertNull(adapter.mcpRegisterCommand("t", List.of("openjiuwen-team-mcp")));
            assertFalse(adapter.mcpLaunchArgs("t", List.of("openjiuwen-team-mcp")).isEmpty());
        }

        CliAgentAdapter openclaw = CliAgentAdapter.buildAdapter("openclaw");
        assertEquals(List.of(), openclaw.mcpLaunchArgs("t", List.of("openjiuwen-team-mcp")));
        assertNull(openclaw.mcpRegisterCommand("t", List.of("openjiuwen-team-mcp")));
    }

    @Test
    void testClaudeHasNoRegisterCommandAndUsesMcpLaunchArgs() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("claude");

        assertNull(adapter.mcpRegisterCommand("t", List.of("openjiuwen-team-mcp")));
        assertFalse(adapter.mcpLaunchArgs("t", List.of("openjiuwen-team-mcp")).isEmpty());
    }

    @Test
    void testCodexHasNoRegisterCommandAndUsesMcpLaunchArgs() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("codex");

        assertNull(adapter.mcpRegisterCommand("t", List.of("openjiuwen-team-mcp")));
        assertFalse(adapter.mcpLaunchArgs("t", List.of("openjiuwen-team-mcp")).isEmpty());
    }

    @Test
    void testSummarizeOutputLine() {
        CliAgentAdapter claude = CliAgentAdapter.buildAdapter("claude");
        String line = "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"hi there\"},{\"type\":\"tool_use\",\"name\":\"read_inbox\"}]}}";
        assertEquals("hi there → read_inbox", claude.summarizeOutputLine(line));
        assertNull(claude.summarizeOutputLine("{\"type\":\"result\",\"subtype\":\"success\"}"));
        assertNull(claude.summarizeOutputLine("not json"));

        CliAgentAdapter codex = CliAgentAdapter.buildAdapter("codex");
        assertEquals("done the work", codex.summarizeOutputLine("{\"type\":\"item.completed\",\"item\":{\"text\":\"done the work\"}}"));
        assertNull(codex.summarizeOutputLine("{\"type\":\"turn.completed\"}"));

        CliAgentAdapter openclaw = CliAgentAdapter.buildAdapter("openclaw");
        assertEquals("working on the task", openclaw.summarizeOutputLine("working on the task"));
        assertNull(openclaw.summarizeOutputLine("   "));
    }
}
