/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's slash-command helpers and completion behavior in
 * {@code openjiuwen/agent_teams/cli/commands.py}.
 */
class TeamCliCommandsTest {

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
    void slashCompleterHandlesTopLevelAndSubActions() {
        SlashCompleter completer = new SlashCompleter();

        assertTrue(completer.complete("/te").stream().anyMatch(item -> "/team".equals(item.text())));
        assertTrue(completer.complete("/team st").stream().anyMatch(item -> "start".equals(item.text())));
        assertTrue(completer.complete("plain").isEmpty());
        assertTrue(completer.complete("/team start x").isEmpty());
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
}
