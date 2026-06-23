/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.cli;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.cli.CommandContext;
import com.openjiuwen.agent_teams.cli.ExitCli;
import com.openjiuwen.agent_teams.cli.SlashCompleter;
import com.openjiuwen.agent_teams.cli.SpecRegistry;
import com.openjiuwen.agent_teams.cli.TeamCliCommands;
import com.openjiuwen.agent_teams.cli.TeamCliState;
import com.openjiuwen.agent_teams.cli.WatchBinding;
import com.openjiuwen.agent_teams.cli.WatchBindingKey;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeManager;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.runner.Runner;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mockStatic;

/**
 * Supplemental parity tests for Team CLI slash-command handlers.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.cli.test_commands} in
 * {@code tests/unit_tests/agent_teams/cli/test_commands.py}.</p>
 */
class TeamCliCommandsPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/agent_teams/cli/test_commands.py";

    @TestFactory
    Collection<DynamicTest> pythonCommandCases() {
        return pythonTestNodes()
                .map(nodeId -> dynamicTest(nodeId, () -> runPythonCase(nodeId)))
                .toList();
    }

    private static Stream<String> pythonTestNodes() {
        return Stream.of(
                SOURCE + "::test_dispatch_slash_unknown_command_does_not_raise",
                SOURCE + "::test_dispatch_slash_unknown_subaction_prints_subhelp",
                SOURCE + "::test_team_list_reports_active_and_registered_teams",
                SOURCE + "::test_team_status_with_no_args_uses_active_team",
                SOURCE + "::test_team_pause_calls_runner_with_active_target",
                SOURCE + "::test_team_stop_clears_active_when_stopping_active_team",
                SOURCE + "::test_team_delete_collects_history_session_ids_and_calls_runner",
                SOURCE + "::test_team_use_rejects_team_without_stream_handle",
                SOURCE + "::test_team_watch_registers_callback_and_stores_binding",
                SOURCE + "::test_team_unwatch_removes_binding",
                SOURCE + "::test_spec_list_renders_registered_specs",
                SOURCE + "::test_spec_show_dumps_spec_when_present",
                SOURCE + "::test_spec_show_warns_when_missing",
                SOURCE + "::test_dispatch_slash_exit_raises_sentinel",
                SOURCE + "::test_slash_completer_first_word_completes_top_level_commands",
                SOURCE + "::test_slash_completer_after_space_completes_subactions",
                SOURCE + "::test_slash_commands_table_covers_all_top_level_commands"
        );
    }

    private static void runPythonCase(String nodeId) {
        switch (nodeId) {
            case SOURCE + "::test_dispatch_slash_unknown_command_does_not_raise" -> testUnknownCommand();
            case SOURCE + "::test_dispatch_slash_unknown_subaction_prints_subhelp" -> testUnknownSubaction();
            case SOURCE + "::test_team_list_reports_active_and_registered_teams" -> testTeamList();
            case SOURCE + "::test_team_status_with_no_args_uses_active_team" -> testTeamStatusWithActiveTarget();
            case SOURCE + "::test_team_pause_calls_runner_with_active_target" -> testTeamPause();
            case SOURCE + "::test_team_stop_clears_active_when_stopping_active_team" -> testTeamStop();
            case SOURCE + "::test_team_delete_collects_history_session_ids_and_calls_runner" -> testTeamDelete();
            case SOURCE + "::test_team_use_rejects_team_without_stream_handle" -> testTeamUseRejectsMissingStream();
            case SOURCE + "::test_team_watch_registers_callback_and_stores_binding" -> testTeamWatch();
            case SOURCE + "::test_team_unwatch_removes_binding" -> testTeamUnwatch();
            case SOURCE + "::test_spec_list_renders_registered_specs" -> testSpecList();
            case SOURCE + "::test_spec_show_dumps_spec_when_present" -> testSpecShowPresent();
            case SOURCE + "::test_spec_show_warns_when_missing" -> testSpecShowMissing();
            case SOURCE + "::test_dispatch_slash_exit_raises_sentinel" -> testExitRaisesSentinel();
            case SOURCE + "::test_slash_completer_first_word_completes_top_level_commands" ->
                    testCompleterFirstWord();
            case SOURCE + "::test_slash_completer_after_space_completes_subactions" -> testCompleterSubactions();
            case SOURCE + "::test_slash_commands_table_covers_all_top_level_commands" -> testSlashCommandsTable();
            default -> throw new IllegalArgumentException("Unknown Python node: " + nodeId);
        }
    }

    private static void testUnknownCommand() {
        Fixture fixture = fakeCli();

        run(fixture, "/no-such-cmd");

        assertThat(fixture.output()).contains("unknown command");
    }

    private static void testUnknownSubaction() {
        Fixture fixture = fakeCli();

        run(fixture, "/team xyz");

        assertThat(fixture.output()).contains("available actions").contains("list");
    }

    private static void testTeamList() {
        Fixture fixture = fakeCli();
        TeamRuntimeManager.RuntimeEntryInfo info = new TeamRuntimeManager.RuntimeEntryInfo(
                "alpha",
                "s1",
                TeamRuntimeManager.RuntimeState.RUNNING,
                false
        );

        try (MockedStatic<Runner> runner = mockStatic(Runner.class)) {
            runner.when(Runner::listActiveTeams).thenReturn(List.of(info));

            run(fixture, "/team list");
        }

        assertThat(fixture.output()).contains("alpha").contains("s1").contains("running");
    }

    private static void testTeamStatusWithActiveTarget() {
        Fixture fixture = fakeCli();
        TeamRuntimeManager.RuntimeEntryInfo info = new TeamRuntimeManager.RuntimeEntryInfo(
                "alpha",
                "s1",
                TeamRuntimeManager.RuntimeState.PAUSED,
                true
        );

        try (MockedStatic<Runner> runner = mockStatic(Runner.class)) {
            runner.when(Runner::listActiveTeams).thenReturn(List.of(info));

            run(fixture, "/team status");
        }

        assertThat(fixture.output()).contains("alpha").contains("paused").contains("closed");
    }

    private static void testTeamPause() {
        Fixture fixture = fakeCli();

        try (MockedStatic<Runner> runner = mockStatic(Runner.class)) {
            runner.when(() -> Runner.pauseAgentTeam("alpha", "s1"))
                    .thenReturn(CompletableFuture.completedFuture(true));

            run(fixture, "/team pause");

            runner.verify(() -> Runner.pauseAgentTeam("alpha", "s1"));
        }
    }

    private static void testTeamStop() {
        Fixture fixture = fakeCli();

        try (MockedStatic<Runner> runner = mockStatic(Runner.class)) {
            runner.when(() -> Runner.stopAgentTeam("alpha", "s1"))
                    .thenReturn(CompletableFuture.completedFuture(true));

            run(fixture, "/team stop");

            runner.verify(() -> Runner.stopAgentTeam("alpha", "s1"));
        }

        assertThat(fixture.state().getActiveTeamName()).isNull();
        assertThat(fixture.state().getActiveSessionId()).isNull();
    }

    private static void testTeamDelete() {
        Fixture fixture = fakeCli();
        fixture.state().rememberSession("alpha", "s2");

        try (MockedStatic<Runner> runner = mockStatic(Runner.class)) {
            runner.when(() -> Runner.deleteAgentTeam(eq("alpha"), any(), eq(true)))
                    .thenReturn(CompletableFuture.completedFuture(true));

            run(fixture, "/team delete alpha --force");

            runner.verify(() -> Runner.deleteAgentTeam("alpha", List.of("s1", "s2"), true));
        }
    }

    private static void testTeamUseRejectsMissingStream() {
        Fixture fixture = fakeCli();

        run(fixture, "/team use beta");

        assertThat(fixture.output()).contains("no active stream");
    }

    private static void testTeamWatch() {
        Object callback = new Object();
        Fixture fixture = fakeCli(callback);

        try (MockedStatic<Runner> runner = mockStatic(Runner.class)) {
            runner.when(() -> Runner.registerHumanAgentInbound(eq("alpha"), eq("s1"), eq("human_agent"), same(callback)))
                    .thenReturn(CompletableFuture.completedFuture(true));

            run(fixture, "/team watch human_agent");

            runner.verify(() -> Runner.registerHumanAgentInbound("alpha", "s1", "human_agent", callback));
        }

        assertThat(fixture.state().getWatchBindings())
                .containsKey(new WatchBindingKey("alpha", "s1", "human_agent"));
    }

    private static void testTeamUnwatch() {
        Fixture fixture = fakeCli();
        fixture.state().getWatchBindings().put(
                new WatchBindingKey("alpha", "s1", "human_agent"),
                new WatchBinding("alpha", "s1", "human_agent")
        );

        try (MockedStatic<Runner> runner = mockStatic(Runner.class)) {
            runner.when(() -> Runner.registerHumanAgentInbound(eq("alpha"), eq("s1"), eq("human_agent"), isNull()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            run(fixture, "/team unwatch human_agent");

            runner.verify(() -> Runner.registerHumanAgentInbound("alpha", "s1", "human_agent", null));
        }

        assertThat(fixture.state().getWatchBindings())
                .doesNotContainKey(new WatchBindingKey("alpha", "s1", "human_agent"));
    }

    private static void testSpecList() {
        Fixture fixture = fakeCli();

        run(fixture, "/spec list");

        assertThat(fixture.output()).contains("alpha").contains("in-memory");
    }

    private static void testSpecShowPresent() {
        Fixture fixture = fakeCli();

        run(fixture, "/spec show alpha");

        assertThat(fixture.output()).contains("alpha");
    }

    private static void testSpecShowMissing() {
        Fixture fixture = fakeCli();

        run(fixture, "/spec show missing");

        assertThat(fixture.output()).contains("no spec registered");
    }

    private static void testExitRaisesSentinel() {
        Fixture fixture = fakeCli();

        assertThatThrownBy(() -> TeamCliCommands.dispatchSlash(fixture.context(), "/exit"))
                .isInstanceOf(ExitCli.class);
    }

    private static void testCompleterFirstWord() {
        SlashCompleter completer = new SlashCompleter();
        List<String> labels = completer.complete("/team").stream().map(item -> item.text()).toList();

        assertThat(labels).contains("/team").doesNotContain("/spec");
    }

    private static void testCompleterSubactions() {
        SlashCompleter completer = new SlashCompleter();
        List<String> labels = completer.complete("/team list").stream().map(item -> item.text()).toList();

        assertThat(labels).contains("list").doesNotContain("start");
    }

    private static void testSlashCommandsTable() {
        assertThat(TeamCliCommands.SLASH_COMMANDS.keySet())
                .contains("/team", "/session", "/spec", "/help", "/clear", "/exit", "/quit");
    }

    private static Fixture fakeCli() {
        return fakeCli(new Object());
    }

    private static Fixture fakeCli(Object inboxCallback) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream console = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        SpecRegistry registry = new SpecRegistry();
        registry.addInmemory(makeSpec("alpha"));
        TeamCliState state = new TeamCliState(registry, console);
        state.setActive("alpha", "s1");
        state.rememberSession("alpha", "s1");
        CommandContext context = new CommandContext(state, inboxCallback, console, console);
        return new Fixture(state, context, bytes);
    }

    private static TeamAgentSpec makeSpec(String teamName) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName(teamName);
        Map<String, DeepAgentSpec> agents = new LinkedHashMap<>();
        agents.put("leader", new DeepAgentSpec());
        spec.setAgents(agents);
        return spec;
    }

    private static void run(Fixture fixture, String line) {
        TeamCliCommands.dispatchSlash(fixture.context(), line).toCompletableFuture().join();
    }

    private record Fixture(TeamCliState state, CommandContext context, ByteArrayOutputStream bytes) {
        private String output() {
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
