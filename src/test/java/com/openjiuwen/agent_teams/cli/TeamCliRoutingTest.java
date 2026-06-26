/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.interaction.DeliverResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's route-text behavior in
 * {@code openjiuwen/agent_teams/cli/routing.py}.
 */
class TeamCliRoutingTest {

    @Test
    void blankInputIsIgnored() {
        Harness harness = harness();

        TeamCliRouting.routeText(harness.context, "   ",
                command -> CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("bad", "")),
                (raw, team, session) -> CompletableFuture.completedFuture(DeliverResult.success("bad")))
                .toCompletableFuture().join();

        assertEquals("", harness.output());
    }

    @Test
    void shellPrefixRunsShellExecutorAndPrintsBothStreams() {
        Harness harness = harness();

        TeamCliRouting.routeText(harness.context, "! echo hi",
                command -> CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("out", "err")),
                (raw, team, session) -> CompletableFuture.completedFuture(DeliverResult.success("bad")))
                .toCompletableFuture().join();

        assertTrue(harness.output().contains("out"));
        assertTrue(harness.output().contains("err"));
    }

    @Test
    void slashPrefixDelegatesToSlashCommands() {
        Harness harness = harness();
        harness.state.setActive("team-a", "session-a");

        TeamCliRouting.routeText(harness.context, "/session active",
                command -> CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("", "")),
                (raw, team, session) -> CompletableFuture.completedFuture(DeliverResult.success("bad")))
                .toCompletableFuture().join();

        assertTrue(harness.output().contains("team=team-a session=session-a"));
    }

    @Test
    void slashExitPropagatesExitSentinel() {
        Harness harness = harness();

        assertThrows(ExitCli.class, () -> TeamCliRouting.routeText(harness.context, "/exit",
                command -> CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("", "")),
                (raw, team, session) -> CompletableFuture.completedFuture(DeliverResult.success("bad"))));
    }

    @Test
    void plainTextWithoutActiveTargetPrintsChineseHint() {
        Harness harness = harness();

        TeamCliRouting.routeText(harness.context, "hello",
                command -> CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("", "")),
                (raw, team, session) -> CompletableFuture.completedFuture(DeliverResult.success("bad")))
                .toCompletableFuture().join();

        assertTrue(harness.output().contains("尚未选定 active team"));
    }

    @Test
    void plainTextUsesActiveTeamAndRendersResult() {
        Harness harness = harness();
        harness.state.setActive("team-a", "session-a");
        AtomicReference<String> rawSeen = new AtomicReference<>();

        TeamCliRouting.routeText(harness.context, "hello",
                command -> CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("", "")),
                (raw, team, session) -> {
                    rawSeen.set(raw + "|" + team + "|" + session);
                    return CompletableFuture.completedFuture(DeliverResult.success("msg-1"));
                })
                .toCompletableFuture().join();

        assertEquals("hello|team-a|session-a", rawSeen.get());
        assertTrue(harness.output().contains("msg_id=msg-1"));
    }

    @Test
    void plainTextInteractionExceptionIsRendered() {
        Harness harness = harness();
        harness.state.setActive("team-a", "session-a");

        TeamCliRouting.routeText(harness.context, "hello",
                command -> CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("", "")),
                (raw, team, session) -> {
                    CompletableFuture<DeliverResult> failed = new CompletableFuture<>();
                    failed.completeExceptionally(new IllegalStateException("boom"));
                    return failed;
                })
                .toCompletableFuture().join();

        assertTrue(harness.output().contains("interact 抛出异常"));
        assertTrue(harness.output().contains("boom"));
    }

    private static Harness harness() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream console = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        TeamCliState state = new TeamCliState(new SpecRegistry(), console);
        CommandContext context = new CommandContext(state, null, console, console);
        return new Harness(state, context, bytes);
    }

    private record Harness(TeamCliState state, CommandContext context, ByteArrayOutputStream bytes) {
        private String output() {
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
