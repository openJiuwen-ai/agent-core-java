/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.interaction.DeliverResult;
import com.openjiuwen.core.runner.Runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Top-level dispatcher for raw Team CLI input lines.
 *
 * <p>Mirrors Python's {@code route_text} and routing helpers in
 * {@code openjiuwen/agent_teams/cli/routing.py}.</p>
 */
public final class TeamCliRouting {

    public static final String SLASH_PREFIX = "/";
    public static final String SHELL_PREFIX = "! ";

    private TeamCliRouting() {
    }

    public static CompletionStage<Void> routeText(TeamCliState state, String raw) {
        return routeText(new CommandContext(state), raw);
    }

    public static CompletionStage<Void> routeText(CommandContext context, String raw) {
        return routeText(context, raw, TeamCliRouting::runShell, Runner::interactAgentTeam);
    }

    static CompletionStage<Void> routeText(
            CommandContext context,
            String raw,
            ShellExecutor shellExecutor,
            InteractSender interactSender
    ) {
        String text = stripTrailingNewline(raw);
        if (text.trim().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        if (text.startsWith(SHELL_PREFIX)) {
            return handleShell(context, text.substring(SHELL_PREFIX.length()), shellExecutor);
        }
        if (text.startsWith(SLASH_PREFIX)) {
            try {
                return TeamCliCommands.dispatchSlash(context, text);
            } catch (ExitCli exitCli) {
                throw exitCli;
            }
        }
        return interactActive(context, text, interactSender);
    }

    private static CompletionStage<Void> handleShell(
            CommandContext context,
            String command,
            ShellExecutor shellExecutor
    ) {
        return shellExecutor.run(command).thenAccept(result -> {
            if (result.stdout() != null && !result.stdout().isBlank()) {
                context.console().println(trimRight(result.stdout()));
            }
            if (result.stderr() != null && !result.stderr().isBlank()) {
                context.console().println("[red]" + trimRight(result.stderr()) + "[/red]");
            }
        });
    }

    private static CompletionStage<Void> interactActive(
            CommandContext context,
            String raw,
            InteractSender interactSender
    ) {
        String teamName = context.state().getActiveTeamName();
        String sessionId = context.state().getActiveSessionId();
        if (teamName == null || sessionId == null) {
            context.console().println("[yellow]尚未选定 active team / session，先执行 `/team start` 或 `/team use`。[/yellow]");
            return CompletableFuture.completedFuture(null);
        }
        return interactSender.send(raw, teamName, sessionId)
                .thenAccept(result -> TeamCliCommands.renderDeliverResult(context, raw, result))
                .exceptionally(error -> {
                    Throwable cause = unwrap(error);
                    context.console().println("[red]interact 抛出异常: " + cause.getMessage() + "[/red]");
                    return null;
                });
    }

    private static CompletionStage<ShellResult> runShell(String command) {
        return CompletableFuture.supplyAsync(() -> {
            ProcessBuilder builder = isWindows()
                    ? new ProcessBuilder("cmd.exe", "/d", "/c", command)
                    : new ProcessBuilder("sh", "-c", command);
            try {
                Process process = builder.start();
                String stdout;
                String stderr;
                try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8));
                     BufferedReader stderrReader = new BufferedReader(new InputStreamReader(
                             process.getErrorStream(), StandardCharsets.UTF_8))) {
                    stdout = stdoutReader.lines().collect(Collectors.joining(System.lineSeparator()));
                    stderr = stderrReader.lines().collect(Collectors.joining(System.lineSeparator()));
                }
                process.waitFor();
                return new ShellResult(stdout, stderr);
            } catch (IOException error) {
                return new ShellResult("", error.getMessage());
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return new ShellResult("", interruptedException.getMessage());
            }
        });
    }

    private static String stripTrailingNewline(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.endsWith("\n") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private static String trimRight(String text) {
        return text == null ? "" : text.replaceAll("\\s+$", "");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    /**
     * Shell command result.
     *
     * <p>Mirrors Python's stdout/stderr tuple from {@code _handle_shell} in
     * {@code openjiuwen/agent_teams/cli/routing.py}.</p>
     */
    public record ShellResult(String stdout, String stderr) {
    }

    /**
     * Testable shell execution boundary.
     *
     * <p>Mirrors Python's {@code asyncio.create_subprocess_shell} use in
     * {@code openjiuwen/agent_teams/cli/routing.py}.</p>
     */
    @FunctionalInterface
    interface ShellExecutor {
        CompletionStage<ShellResult> run(String command);
    }

    /**
     * Testable active-team interaction boundary.
     *
     * <p>Mirrors Python's {@code Runner.interact_agent_team} call in
     * {@code openjiuwen/agent_teams/cli/routing.py}.</p>
     */
    @FunctionalInterface
    interface InteractSender {
        CompletionStage<DeliverResult> send(String raw, String teamName, String sessionId);
    }
}
