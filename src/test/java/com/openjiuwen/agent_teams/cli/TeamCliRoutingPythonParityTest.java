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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/cli/test_routing.py}.
 */
class TeamCliRoutingPythonParityTest {

    @Test
    void routeTextSkipsBlankInput() {
        Harness harness = harness();
        AtomicInteger shellCalls = new AtomicInteger();
        AtomicInteger interactCalls = new AtomicInteger();

        TeamCliRouting.routeText(harness.context(), "   ",
                command -> {
                    shellCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("", ""));
                },
                (raw, team, session) -> {
                    interactCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(DeliverResult.success("unexpected"));
                })
                .toCompletableFuture().join();

        assertEquals(0, shellCalls.get());
        assertEquals(0, interactCalls.get());
        assertEquals("", harness.output());
    }

    @Test
    void routeTextDispatchesSlashToDispatchSlash() {
        Harness harness = harness();
        AtomicInteger shellCalls = new AtomicInteger();
        AtomicInteger interactCalls = new AtomicInteger();

        TeamCliRouting.routeText(harness.context(), "/session active",
                command -> {
                    shellCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("", ""));
                },
                (raw, team, session) -> {
                    interactCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(DeliverResult.success("unexpected"));
                })
                .toCompletableFuture().join();

        assertEquals(0, shellCalls.get());
        assertEquals(0, interactCalls.get());
        assertTrue(harness.output().contains("team=- session=-"));
    }

    @Test
    void routeTextRunsShellCommandViaSubprocessBoundary() {
        Harness harness = harness();
        AtomicReference<String> commandSeen = new AtomicReference<>();

        TeamCliRouting.routeText(harness.context(), "! echo hello",
                command -> {
                    commandSeen.set(command);
                    return CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("hello\n", ""));
                },
                (raw, team, session) -> CompletableFuture.completedFuture(DeliverResult.success("unexpected")))
                .toCompletableFuture().join();

        assertEquals("echo hello", commandSeen.get());
        assertTrue(harness.output().contains("hello"));
    }

    @Test
    void routeTextForwardsPlainTextToActiveTeam() {
        Harness harness = harness();
        harness.state().setActive("alpha", "s1");
        AtomicReference<String> callSeen = new AtomicReference<>();

        TeamCliRouting.routeText(harness.context(), "hello team",
                command -> CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("", "")),
                (raw, team, session) -> {
                    callSeen.set(raw + "|" + team + "|" + session);
                    return CompletableFuture.completedFuture(DeliverResult.success("msg-1"));
                })
                .toCompletableFuture().join();

        assertEquals("hello team|alpha|s1", callSeen.get());
    }

    @Test
    void routeTextWarnsWhenNoActiveTeam() {
        Harness harness = harness();
        AtomicInteger interactCalls = new AtomicInteger();

        TeamCliRouting.routeText(harness.context(), "plain text",
                command -> CompletableFuture.completedFuture(new TeamCliRouting.ShellResult("", "")),
                (raw, team, session) -> {
                    interactCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(DeliverResult.success("unexpected"));
                })
                .toCompletableFuture().join();

        assertEquals(0, interactCalls.get());
        assertTrue(harness.output().contains("尚未选定 active team"));
    }

    @Test
    void translateReasonKnownTokenReturnsChineseHint() {
        assertTrue(TeamCliCommands.translateReason("gate_closed").contains("gate closed"));
        assertTrue(TeamCliCommands.translateReason("not_active").contains("运行池"));
        assertTrue(TeamCliCommands.translateReason("missing_target").contains("选定"));
    }

    @Test
    void translateReasonPatternTokensExtractMember() {
        assertTrue(TeamCliCommands.translateReason("unknown_human_agent:bob").contains("human-agent: bob"));
        assertTrue(TeamCliCommands.translateReason("unknown_member:alice").contains("成员: alice"));
        assertTrue(TeamCliCommands.translateReason("send_failed:alice").contains("失败: alice"));
    }

    @Test
    void translateReasonUnknownTokenPassesThrough() {
        assertEquals("weird_token", TeamCliCommands.translateReason("weird_token"));
    }

    @Test
    void renderDeliverResultSuccessPrintsMessageId() {
        Harness harness = harness();

        TeamCliCommands.renderDeliverResult(harness.context(), "raw", DeliverResult.success("xyz"));

        assertTrue(harness.output().contains("xyz"));
    }

    @Test
    void renderDeliverResultFailurePrintsTranslatedReason() {
        Harness harness = harness();

        TeamCliCommands.renderDeliverResult(harness.context(), "raw", DeliverResult.failure("gate_closed"));

        assertTrue(harness.output().contains("gate_closed"));
        assertTrue(harness.output().contains("gate closed"));
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
