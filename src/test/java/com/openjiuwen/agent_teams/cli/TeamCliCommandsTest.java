/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.runtime.TeamRuntimeManager;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.runner.Runner;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's slash-command helpers and completion behavior in
 * {@code openjiuwen/agent_teams/cli/commands.py}.
 *
 * <p>Also mirrors Python's {@code tests.unit_tests.agent_teams.cli.test_commands} in
 * {@code tests/unit_tests/agent_teams/cli/test_commands.py}.</p>
 */
class TeamCliCommandsTest {

    @Test
    void dispatchSlashUnknownCommandDoesNotRaise() {
        Harness harness = harness();

        TeamCliCommands.dispatchSlash(harness.context(), "/no-such-cmd").toCompletableFuture().join();

        assertTrue(harness.output().contains("unknown command"));
    }

    @Test
    void dispatchSlashUnknownSubactionPrintsSubhelp() {
        Harness harness = harness();

        TeamCliCommands.dispatchSlash(harness.context(), "/team xyz").toCompletableFuture().join();

        assertTrue(harness.output().contains("available actions"));
        assertTrue(harness.output().contains("list"));
    }

    @Test
    void splitArgsPreservesQuotedTextAndFallsBackForWhitespace() {
        assertEquals(List.of("team", "start", "alpha", "sid-1", "hello world"),
                TeamCliCommands.splitArgs("team start alpha sid-1 \"hello world\""));
        assertEquals(List.of("team", "start", "alpha", "sid-1", "hello"),
                TeamCliCommands.splitArgs(" team   start alpha sid-1 hello "));
    }

    @Test
    void commandTablesExposePythonSlashSurface() {
        assertEquals(List.of("/clear", "/exit", "/help", "/quit", "/session", "/spec", "/team"),
                TeamCliCommands.sortedSlashCommands());
        assertTrue(TeamCliCommands.TEAM_ACTIONS.keySet().containsAll(List.of(
                "list", "status", "monitor", "use", "start", "switch", "pause",
                "resume", "stop", "delete", "watch", "unwatch")));
        assertEquals(List.of("active", "list", "release", "switch"),
                TeamCliCommands.SESSION_ACTIONS.keySet().stream().sorted().toList());
        assertEquals(List.of("list", "load", "show"),
                TeamCliCommands.SPEC_ACTIONS.keySet().stream().sorted().toList());
    }

    @Test
    void teamListReportsActiveAndRegisteredTeams() {
        Harness harness = harness();
        TeamRuntimeManager.RuntimeEntryInfo info = new TeamRuntimeManager.RuntimeEntryInfo(
                "alpha", "s1", TeamRuntimeManager.RuntimeState.RUNNING, false);

        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(Runner::listActiveTeams).thenReturn(List.of(info));

            TeamCliCommands.dispatchSlash(harness.context(), "/team list").toCompletableFuture().join();
        }

        String output = harness.output();
        assertTrue(output.contains("alpha"));
        assertTrue(output.contains("s1"));
        assertTrue(output.contains("running"));
    }

    @Test
    void teamStatusWithNoArgsUsesActiveTeam() {
        Harness harness = harness();
        TeamRuntimeManager.RuntimeEntryInfo info = new TeamRuntimeManager.RuntimeEntryInfo(
                "alpha", "s1", TeamRuntimeManager.RuntimeState.PAUSED, true);

        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(Runner::listActiveTeams).thenReturn(List.of(info));

            TeamCliCommands.dispatchSlash(harness.context(), "/team status").toCompletableFuture().join();
        }

        String output = harness.output();
        assertTrue(output.contains("alpha"));
        assertTrue(output.contains("paused"));
        assertTrue(output.contains("closed"));
    }

    @Test
    void teamPauseCallsRunnerWithActiveTarget() {
        Harness harness = harness();

        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(() -> Runner.pauseAgentTeam("alpha", "s1"))
                    .thenReturn(CompletableFuture.completedFuture(true));

            TeamCliCommands.dispatchSlash(harness.context(), "/team pause").toCompletableFuture().join();

            runner.verify(() -> Runner.pauseAgentTeam("alpha", "s1"));
        }
    }

    @Test
    void teamStopClearsActiveWhenStoppingActiveTeam() {
        Harness harness = harness();

        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(() -> Runner.stopAgentTeam("alpha", "s1"))
                    .thenReturn(CompletableFuture.completedFuture(true));

            TeamCliCommands.dispatchSlash(harness.context(), "/team stop").toCompletableFuture().join();

            runner.verify(() -> Runner.stopAgentTeam("alpha", "s1"));
        }

        assertNull(harness.state().getActiveTeamName());
        assertNull(harness.state().getActiveSessionId());
    }

    @Test
    void teamDeleteCollectsHistorySessionIdsAndCallsRunner() {
        Harness harness = harness();
        harness.state().rememberSession("alpha", "s2");

        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(() -> Runner.deleteAgentTeam("alpha", List.of("s1", "s2"), true))
                    .thenReturn(CompletableFuture.completedFuture(true));

            TeamCliCommands.dispatchSlash(harness.context(), "/team delete alpha --force")
                    .toCompletableFuture().join();

            runner.verify(() -> Runner.deleteAgentTeam("alpha", List.of("s1", "s2"), true));
        }
    }

    @Test
    void teamUseRejectsTeamWithoutStreamHandle() {
        Harness harness = harness();

        TeamCliCommands.dispatchSlash(harness.context(), "/team use beta").toCompletableFuture().join();

        assertTrue(harness.output().contains("no active stream"));
    }

    @Test
    void teamWatchRegistersCallbackAndStoresBinding() {
        Object callback = new Object();
        Harness harness = harness(callback);

        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(() -> Runner.registerHumanAgentInbound("alpha", "s1", "human_agent", callback))
                    .thenReturn(CompletableFuture.completedFuture(true));

            TeamCliCommands.dispatchSlash(harness.context(), "/team watch human_agent")
                    .toCompletableFuture().join();

            runner.verify(() -> Runner.registerHumanAgentInbound("alpha", "s1", "human_agent", callback));
        }

        assertTrue(harness.state().getWatchBindings()
                .containsKey(new WatchBindingKey("alpha", "s1", "human_agent")));
    }

    @Test
    void teamUnwatchRemovesBinding() {
        Harness harness = harness();
        WatchBinding binding = new WatchBinding("alpha", "s1", "human_agent");
        harness.state().getWatchBindings().put(WatchBindingKey.from(binding), binding);

        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(() -> Runner.registerHumanAgentInbound("alpha", "s1", "human_agent", null))
                    .thenReturn(CompletableFuture.completedFuture(true));

            TeamCliCommands.dispatchSlash(harness.context(), "/team unwatch human_agent")
                    .toCompletableFuture().join();

            runner.verify(() -> Runner.registerHumanAgentInbound("alpha", "s1", "human_agent", null));
        }

        assertFalse(harness.state().getWatchBindings()
                .containsKey(new WatchBindingKey("alpha", "s1", "human_agent")));
    }

    @Test
    void specListRendersRegisteredSpecs() {
        Harness harness = harness();

        TeamCliCommands.dispatchSlash(harness.context(), "/spec list").toCompletableFuture().join();

        assertTrue(harness.output().contains("alpha"));
        assertTrue(harness.output().contains("in-memory"));
    }

    @Test
    void specShowDumpsSpecWhenPresent() {
        Harness harness = harness();

        TeamCliCommands.dispatchSlash(harness.context(), "/spec show alpha").toCompletableFuture().join();

        assertTrue(harness.output().contains("alpha"));
    }

    @Test
    void specShowWarnsWhenMissing() {
        Harness harness = harness();

        TeamCliCommands.dispatchSlash(harness.context(), "/spec show missing").toCompletableFuture().join();

        assertTrue(harness.output().contains("no spec registered"));
    }

    @Test
    void slashCompleterFirstWordCompletesTopLevelCommands() {
        SlashCompleter completer = new SlashCompleter();

        assertTrue(completer.complete("/te").stream().anyMatch(item -> "/team".equals(item.text())));
        assertFalse(completer.complete("/team").stream().anyMatch(item -> "/spec".equals(item.text())));
    }

    @Test
    void slashCompleterAfterSpaceCompletesSubactions() {
        SlashCompleter completer = new SlashCompleter();

        assertTrue(completer.complete("/team list").stream().anyMatch(item -> "list".equals(item.text())));
        assertFalse(completer.complete("/team list").stream().anyMatch(item -> "start".equals(item.text())));
    }

    @Test
    void sessionActivePrintsCurrentRoutingTarget() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TeamCliState state = new TeamCliState(new SpecRegistry(), new PrintStream(bytes));
        state.setActive("team-a", "session-a");

        TeamCliCommands.dispatchSlash(state, "/session active").toCompletableFuture().join();

        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("team=team-a session=session-a"));
    }

    @Test
    void teamUseSwitchesToExistingStreamHandle() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TeamCliState state = new TeamCliState(new SpecRegistry(), new PrintStream(bytes));
        StreamHandle handle = new StreamHandle("team-a", "session-a", new CompletableFuture<>(),
                CompletableFuture.completedFuture(null));
        state.getStreamHandles().put("team-a", handle);

        TeamCliCommands.dispatchSlash(state, "/team use team-a").toCompletableFuture().join();

        assertEquals("team-a", state.getActiveTeamName());
        assertEquals("session-a", state.getActiveSessionId());
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("active"));
    }

    @Test
    void exitCommandRaisesSentinel() {
        TeamCliState state = new TeamCliState(new SpecRegistry(), System.out);

        assertThrows(ExitCli.class, () -> TeamCliCommands.dispatchSlash(state, "/exit"));
    }

    @Test
    void renderDeliverResultTranslatesKnownFailureReason() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CommandContext context = new CommandContext(
                new TeamCliState(new SpecRegistry(), new PrintStream(bytes)),
                null,
                System.out,
                new PrintStream(bytes)
        );

        TeamCliCommands.renderDeliverResult(context, "hello",
                com.openjiuwen.agent_teams.interaction.DeliverResult.failure("gate_closed"));

        String text = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("gate_closed"));
        assertTrue(text.contains("团队当前轮次已结束"));
    }

    private static Harness harness() {
        return harness(new Object());
    }

    private static Harness harness(Object inboxCallback) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream console = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        SpecRegistry registry = new SpecRegistry();
        registry.addInmemory(spec("alpha"));
        TeamCliState state = new TeamCliState(registry, console);
        state.setActive("alpha", "s1");
        state.rememberSession("alpha", "s1");
        CommandContext context = new CommandContext(state, inboxCallback, console, console);
        return new Harness(state, context, bytes);
    }

    private static TeamAgentSpec spec(String teamName) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName(teamName);
        return spec;
    }

    private record Harness(TeamCliState state, CommandContext context, ByteArrayOutputStream bytes) {
        private String output() {
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
