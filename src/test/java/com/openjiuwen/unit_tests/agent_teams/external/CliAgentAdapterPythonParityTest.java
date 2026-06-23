/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.external;

import com.openjiuwen.agent_teams.external.cli_agent.CliAgentAdapter;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for external CLI adapter registration and injection wiring.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.external.test_cli_adapter_injection} in
 * {@code tests/unit_tests/agent_teams/external/test_cli_adapter_injection.py}.</p>
 */
class CliAgentAdapterPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/agent_teams/external/test_cli_adapter_injection.py";

    @TestFactory
    Collection<DynamicTest> pythonCliAdapterCases() {
        return pythonTestNodes()
                .map(nodeId -> dynamicTest(nodeId, () -> runPythonCase(nodeId)))
                .toList();
    }

    private static Stream<String> pythonTestNodes() {
        return Stream.of(
                SOURCE + "::test_gemini_adapter_registered_and_builds_headless_turn",
                SOURCE + "::test_claude_system_prompt_uses_append_flag",
                SOURCE + "::test_clis_without_system_prompt_flag_get_empty_args[gemini]",
                SOURCE + "::test_clis_without_system_prompt_flag_get_empty_args[openclaw]",
                SOURCE + "::test_clis_without_system_prompt_flag_get_empty_args[hermes]",
                SOURCE + "::test_clis_without_system_prompt_flag_get_empty_args[generic]",
                SOURCE + "::test_codex_system_prompt_uses_developer_instructions",
                SOURCE + "::test_codex_uses_json_output_and_turn_completed_sentinel",
                SOURCE + "::test_gemini_cross_turn_starts_then_resumes",
                SOURCE + "::test_gemini_registers_mcp_via_subcommand",
                SOURCE + "::test_hermes_registers_mcp_via_subcommand",
                SOURCE + "::test_launch_inject_clis_have_no_register_command[claude]",
                SOURCE + "::test_launch_inject_clis_have_no_register_command[codex]",
                SOURCE + "::test_openclaw_cannot_auto_inject_mcp",
                SOURCE + "::test_claude_summarize_extracts_text_and_tool_skips_lifecycle",
                SOURCE + "::test_codex_summarize_extracts_item_text_skips_turn_completed",
                SOURCE + "::test_plain_text_cli_surfaces_line_as_is"
        );
    }

    private static void runPythonCase(String nodeId) {
        switch (nodeId) {
            case SOURCE + "::test_gemini_adapter_registered_and_builds_headless_turn" -> testGeminiHeadlessTurn();
            case SOURCE + "::test_claude_system_prompt_uses_append_flag" -> testClaudeSystemPromptArgs();
            case SOURCE + "::test_clis_without_system_prompt_flag_get_empty_args[gemini]" ->
                    testNoSystemPromptFlag("gemini");
            case SOURCE + "::test_clis_without_system_prompt_flag_get_empty_args[openclaw]" ->
                    testNoSystemPromptFlag("openclaw");
            case SOURCE + "::test_clis_without_system_prompt_flag_get_empty_args[hermes]" ->
                    testNoSystemPromptFlag("hermes");
            case SOURCE + "::test_clis_without_system_prompt_flag_get_empty_args[generic]" ->
                    testNoSystemPromptFlag("generic");
            case SOURCE + "::test_codex_system_prompt_uses_developer_instructions" -> testCodexSystemPromptArgs();
            case SOURCE + "::test_codex_uses_json_output_and_turn_completed_sentinel" -> testCodexJsonCompletion();
            case SOURCE + "::test_gemini_cross_turn_starts_then_resumes" -> testGeminiCrossTurn();
            case SOURCE + "::test_gemini_registers_mcp_via_subcommand" -> testGeminiMcpRegister();
            case SOURCE + "::test_hermes_registers_mcp_via_subcommand" -> testHermesMcpRegister();
            case SOURCE + "::test_launch_inject_clis_have_no_register_command[claude]" ->
                    testLaunchInjectCli("claude");
            case SOURCE + "::test_launch_inject_clis_have_no_register_command[codex]" ->
                    testLaunchInjectCli("codex");
            case SOURCE + "::test_openclaw_cannot_auto_inject_mcp" -> testOpenclawCannotAutoInjectMcp();
            case SOURCE + "::test_claude_summarize_extracts_text_and_tool_skips_lifecycle" ->
                    testClaudeSummarizeOutputLine();
            case SOURCE + "::test_codex_summarize_extracts_item_text_skips_turn_completed" ->
                    testCodexSummarizeOutputLine();
            case SOURCE + "::test_plain_text_cli_surfaces_line_as_is" -> testPlainTextCliSurfacesLine();
            default -> throw new IllegalArgumentException("Unknown Python node: " + nodeId);
        }
    }

    private static void testGeminiHeadlessTurn() {
        assertThat(CliAgentAdapter.availableAdapters()).contains("gemini");
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("gemini");

        List<String> argv = adapter.buildTurnCommand("do it", "s", true);

        assertThat(argv.subList(0, 4)).containsExactly("gemini", "-o", "stream-json", "-y");
        assertThat(argv.subList(argv.size() - 2, argv.size())).containsExactly("-p", "do it");
        assertThat(adapter.supportsStdinInjection()).isFalse();
    }

    private static void testClaudeSystemPromptArgs() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("claude");

        assertThat(adapter.injectsSystemPromptViaArg()).isTrue();
        assertThat(adapter.systemPromptArgs("PERSONA"))
                .containsExactly("--append-system-prompt", "PERSONA");
        assertThat(adapter.systemPromptArgs("")).isEmpty();
    }

    private static void testNoSystemPromptFlag(String name) {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter(name);

        assertThat(adapter.injectsSystemPromptViaArg()).isFalse();
        assertThat(adapter.systemPromptArgs("PERSONA")).isEmpty();
    }

    private static void testCodexSystemPromptArgs() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("codex");

        assertThat(adapter.injectsSystemPromptViaArg()).isTrue();
        assertThat(adapter.systemPromptArgs("be terse"))
                .containsExactly("-c", "developer_instructions=\"be terse\"");
        assertThat(adapter.systemPromptArgs("")).isEmpty();
    }

    private static void testCodexJsonCompletion() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("codex");

        assertThat(adapter.buildCommand()).contains("--json");
        assertThat(adapter.isTurnComplete("{\"type\":\"turn.completed\"}")).isTrue();
        assertThat(adapter.isTurnComplete("{\"type\":\"item.completed\"}")).isFalse();
    }

    private static void testGeminiCrossTurn() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("gemini");

        List<String> first = adapter.buildTurnCommand("hi", "sid-1", true);
        List<String> later = adapter.buildTurnCommand("again", "sid-1", false);

        assertThat(first).contains("--session-id");
        assertThat(first.get(first.indexOf("--session-id") + 1)).isEqualTo("sid-1");
        assertThat(first).doesNotContain("--resume");
        assertThat(later).contains("--resume");
        assertThat(later.get(later.indexOf("--resume") + 1)).isEqualTo("sid-1");
        assertThat(later).doesNotContain("--session-id");
    }

    private static void testGeminiMcpRegister() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("gemini");

        assertThat(adapter.mcpRegisterCommand("openjiuwen-team", List.of("openjiuwen-team-mcp")))
                .containsExactly("gemini", "mcp", "add", "openjiuwen-team", "openjiuwen-team-mcp");
    }

    private static void testHermesMcpRegister() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("hermes");

        assertThat(adapter.mcpRegisterCommand("openjiuwen-team", List.of("openjiuwen-team-mcp")))
                .containsExactly("hermes", "mcp", "add", "openjiuwen-team", "--command", "openjiuwen-team-mcp");
    }

    private static void testLaunchInjectCli(String name) {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter(name);

        assertThat(adapter.mcpRegisterCommand("t", List.of("openjiuwen-team-mcp"))).isNull();
        assertThat(adapter.mcpLaunchArgs("t", List.of("openjiuwen-team-mcp"))).isNotEmpty();
    }

    private static void testOpenclawCannotAutoInjectMcp() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("openclaw");

        assertThat(adapter.mcpLaunchArgs("t", List.of("openjiuwen-team-mcp"))).isEmpty();
        assertThat(adapter.mcpRegisterCommand("t", List.of("openjiuwen-team-mcp"))).isNull();
    }

    private static void testClaudeSummarizeOutputLine() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("claude");
        String line = "{\"type\":\"assistant\",\"message\":{\"content\":["
                + "{\"type\":\"text\",\"text\":\"hi there\"},"
                + "{\"type\":\"tool_use\",\"name\":\"read_inbox\"}]}}";

        assertThat(adapter.structuredOutput()).isTrue();
        assertThat(adapter.summarizeOutputLine(line)).isEqualTo("hi there \u2192 read_inbox");
        assertThat(adapter.summarizeOutputLine("{\"type\":\"result\",\"subtype\":\"success\"}")).isNull();
        assertThat(adapter.summarizeOutputLine("not json")).isNull();
    }

    private static void testCodexSummarizeOutputLine() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("codex");

        assertThat(adapter.summarizeOutputLine("{\"type\":\"item.completed\",\"item\":{\"text\":\"done the work\"}}"))
                .isEqualTo("done the work");
        assertThat(adapter.summarizeOutputLine("{\"type\":\"turn.completed\"}")).isNull();
    }

    private static void testPlainTextCliSurfacesLine() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("openclaw");

        assertThat(adapter.structuredOutput()).isFalse();
        assertThat(adapter.summarizeOutputLine("working on the task")).isEqualTo("working on the task");
        assertThat(adapter.summarizeOutputLine("   ")).isNull();
    }
}
