/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.interaction.HumanAgentInboundEvent;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TeamCli} behavior in
 * {@code openjiuwen/agent_teams/cli/tui.py}.
 */
class TeamCliTest {

    @Test
    void constructorInitializesStateAndInboxCallback() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream console = printStream(bytes);
        SpecRegistry registry = new SpecRegistry();

        TeamCli cli = new TeamCli(registry, console);

        assertSame(registry, cli.getState().getSpecRegistry());
        assertSame(console, cli.getState().getConsole());
        cli.getInboxCallback().apply(new HumanAgentInboundEvent(
                "member-a", "sender-a", "hello\nworld", false, "msg-1", 1L
        )).toCompletableFuture().join();
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("[inbox/member-a]"));
    }

    @Test
    void renderToolbarReportsActiveStreamAndWatchCounts() {
        TeamCli cli = harness().cli();

        assertEquals("[no active team] streams=0 watching=0  (/help for commands)", cli.renderToolbar());

        cli.getState().setActive("team-a", "session-a");
        cli.getState().getStreamHandles().put("team-a", new StreamHandle(
                "team-a",
                "session-a",
                new CompletableFuture<>(),
                CompletableFuture.completedFuture(null)
        ));
        WatchBinding binding = new WatchBinding("team-a", "session-a", "member-a");
        cli.getState().getWatchBindings().put(WatchBindingKey.from(binding), binding);

        assertEquals("[team=team-a session=session-a] streams=1 watching=1  (/help for commands)",
                cli.renderToolbar());
    }

    @Test
    void runIterLoopRoutesLinesUntilExitSentinel() {
        Harness harness = harness();

        harness.cli().run(List.of("hello", "/exit", "ignored")).toCompletableFuture().join();

        assertEquals(List.of("hello", "/exit"), harness.routedLines());
    }

    @Test
    void runPromptLoopReadsPromptLinesUntilExitSentinel() {
        Harness harness = harness("hello\n/exit\nignored\n");

        harness.cli().run().toCompletableFuture().join();

        assertEquals(List.of("hello", "/exit"), harness.routedLines());
        assertTrue(harness.output().contains("Team CLI"));
    }

    @Test
    void shutdownUnregistersWatchesStopsStreamsAndClearsState() {
        Harness harness = harness();
        TeamCli cli = harness.cli();
        WatchBinding binding = new WatchBinding("team-a", "session-a", "member-a");
        cli.getState().getWatchBindings().put(WatchBindingKey.from(binding), binding);
        StreamHandle handle = new StreamHandle(
                "team-a",
                "session-a",
                new CompletableFuture<>(),
                CompletableFuture.completedFuture(null)
        );
        cli.getState().getStreamHandles().put("team-a", handle);

        cli.shutdown().toCompletableFuture().join();

        assertTrue(cli.getState().getWatchBindings().isEmpty());
        assertTrue(cli.getState().getStreamHandles().isEmpty());
        assertEquals(List.of("team-a|session-a|member-a"), harness.unregistered());
        assertEquals(List.of("team-a|session-a"), harness.stoppedTeams());
        assertEquals(List.of("team-a|session-a"), harness.stoppedStreams());
    }

    private static Harness harness() {
        return harness("");
    }

    private static Harness harness(String promptInput) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream console = printStream(bytes);
        List<String> routed = new ArrayList<>();
        List<String> unregistered = new ArrayList<>();
        List<String> stoppedTeams = new ArrayList<>();
        List<String> stoppedStreams = new ArrayList<>();
        TeamCli cli = new TeamCli(
                new SpecRegistry(),
                console,
                console,
                new BufferedReader(new StringReader(promptInput)),
                (context, line) -> {
                    routed.add(line);
                    if ("/exit".equals(line)) {
                        throw new ExitCli();
                    }
                    return CompletableFuture.completedFuture(null);
                },
                (teamName, sessionId, memberName) -> {
                    unregistered.add(teamName + "|" + sessionId + "|" + memberName);
                    return CompletableFuture.completedFuture(true);
                },
                (teamName, sessionId) -> {
                    stoppedTeams.add(teamName + "|" + sessionId);
                    return CompletableFuture.completedFuture(true);
                },
                handle -> {
                    stoppedStreams.add(handle.getTeamName() + "|" + handle.getSessionId());
                    return CompletableFuture.completedFuture(null);
                }
        );
        return new Harness(cli, bytes, routed, unregistered, stoppedTeams, stoppedStreams);
    }

    private static PrintStream printStream(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }

    /**
     * Test harness for TeamCli boundary injection.
     *
     * <p>Mirrors Python's {@code TeamCli.run(input_iter=...)} testing hook in
     * {@code openjiuwen/agent_teams/cli/tui.py}.</p>
     */
    private record Harness(
            TeamCli cli,
            ByteArrayOutputStream bytes,
            List<String> routedLines,
            List<String> unregistered,
            List<String> stoppedTeams,
            List<String> stoppedStreams
    ) {
        private String output() {
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
