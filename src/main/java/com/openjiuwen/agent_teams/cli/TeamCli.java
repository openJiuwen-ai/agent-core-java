/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.interaction.HumanAgentInboundEvent;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.runner.Runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Interactive driver for Runner team lifecycle facades.
 *
 * <p>Mirrors Python's {@code TeamCli} in
 * {@code openjiuwen/agent_teams/cli/tui.py}.</p>
 */
public class TeamCli {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final PrintStream console;
    private final PrintStream terminal;
    private final TeamCliState state;
    private final Function<HumanAgentInboundEvent, CompletionStage<Void>> inboxCallback;
    private final RouteExecutor routeExecutor;
    private final WatchUnregister watchUnregister;
    private final TeamStopper teamStopper;
    private final StreamStopper streamStopper;
    private final BufferedReader promptReader;

    public TeamCli(SpecRegistry specRegistry) {
        this(specRegistry, System.out);
    }

    public TeamCli(SpecRegistry specRegistry, PrintStream console) {
        this(
                specRegistry,
                console,
                console,
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                TeamCliRouting::routeText,
                (teamName, sessionId, memberName) ->
                        Runner.registerHumanAgentInbound(teamName, sessionId, memberName, null),
                Runner::stopAgentTeam,
                StreamRenderer::stopStream
        );
    }

    TeamCli(
            SpecRegistry specRegistry,
            PrintStream console,
            PrintStream terminal,
            BufferedReader promptReader,
            RouteExecutor routeExecutor,
            WatchUnregister watchUnregister,
            TeamStopper teamStopper,
            StreamStopper streamStopper
    ) {
        this.console = Objects.requireNonNull(console, "console");
        this.terminal = terminal == null ? console : terminal;
        this.state = new TeamCliState(Objects.requireNonNull(specRegistry, "specRegistry"), this.console);
        this.inboxCallback = InboxSink.makeInboxCallback(this.console::println);
        this.promptReader = promptReader;
        this.routeExecutor = Objects.requireNonNull(routeExecutor, "routeExecutor");
        this.watchUnregister = Objects.requireNonNull(watchUnregister, "watchUnregister");
        this.teamStopper = Objects.requireNonNull(teamStopper, "teamStopper");
        this.streamStopper = Objects.requireNonNull(streamStopper, "streamStopper");
    }

    public TeamCliState getState() {
        return state;
    }

    public Function<HumanAgentInboundEvent, CompletionStage<Void>> getInboxCallback() {
        return inboxCallback;
    }

    public CompletionStage<Void> run() {
        return run(null);
    }

    public CompletionStage<Void> run(Iterable<String> inputIter) {
        if (inputIter == null) {
            return runPromptLoop();
        }
        return runIterLoop(inputIter);
    }

    String renderToolbar() {
        String active = state.getActiveTeamName() == null
                ? "no active team"
                : "team=" + state.getActiveTeamName() + " session=" + state.getActiveSessionId();
        int streams = state.getStreamHandles().size();
        int watching = state.getWatchBindings().size();
        return "[" + active + "] streams=" + streams + " watching=" + watching + "  (/help for commands)";
    }

    public CompletionStage<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            List<WatchBinding> bindings = new ArrayList<>(state.getWatchBindings().values());
            for (WatchBinding binding : bindings) {
                suppress(() -> watchUnregister.unregister(
                        binding.teamName(),
                        binding.sessionId(),
                        binding.memberName()
                ).toCompletableFuture().join());
            }
            state.getWatchBindings().clear();

            List<StreamHandle> handles = new ArrayList<>(state.getStreamHandles().values());
            for (StreamHandle handle : handles) {
                suppress(() -> teamStopper.stop(handle.getTeamName(), handle.getSessionId())
                        .toCompletableFuture().join());
                suppress(() -> streamStopper.stop(handle).toCompletableFuture().join());
                state.getStreamHandles().remove(handle.getTeamName());
            }
            TEAM_LOGGER.info("[cli] shutdown complete");
        });
    }

    private CompletionStage<Void> runPromptLoop() {
        return CompletableFuture.runAsync(() -> {
            console.println("Team CLI - `/help` for commands, `/exit` to quit.");
            if (promptReader == null) {
                return;
            }
            while (true) {
                String line;
                try {
                    console.print("team> ");
                    console.flush();
                    line = promptReader.readLine();
                } catch (IOException error) {
                    break;
                }
                if (line == null) {
                    break;
                }
                try {
                    route(line);
                } catch (ExitCli exitCli) {
                    break;
                }
            }
        });
    }

    private CompletionStage<Void> runIterLoop(Iterable<String> inputIter) {
        return CompletableFuture.runAsync(() -> {
            for (String line : inputIter) {
                try {
                    route(line);
                } catch (ExitCli exitCli) {
                    break;
                }
            }
        });
    }

    private void route(String line) {
        try {
            routeExecutor.route(context(), line).toCompletableFuture().join();
        } catch (CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof ExitCli exitCli) {
                throw exitCli;
            }
            throw error;
        }
    }

    private CommandContext context() {
        return new CommandContext(state, inboxCallback, terminal, console);
    }

    private static void suppress(CheckedRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception ignored) {
            // Python uses contextlib.suppress(Exception) while tearing down CLI resources.
        }
    }

    /**
     * Testable routing boundary used by TeamCli run loops.
     *
     * <p>Mirrors Python's {@code route_text} call from {@code TeamCli} in
     * {@code openjiuwen/agent_teams/cli/tui.py}.</p>
     */
    @FunctionalInterface
    interface RouteExecutor {
        CompletionStage<Void> route(CommandContext context, String line);
    }

    /**
     * Testable watch-unregister boundary used during shutdown.
     *
     * <p>Mirrors Python's {@code Runner.register_human_agent_inbound(..., callback=None)} call in
     * {@code openjiuwen/agent_teams/cli/tui.py}.</p>
     */
    @FunctionalInterface
    interface WatchUnregister {
        CompletionStage<Boolean> unregister(String teamName, String sessionId, String memberName);
    }

    /**
     * Testable team-stop boundary used during shutdown.
     *
     * <p>Mirrors Python's {@code Runner.stop_agent_team} call in
     * {@code openjiuwen/agent_teams/cli/tui.py}.</p>
     */
    @FunctionalInterface
    interface TeamStopper {
        CompletionStage<Boolean> stop(String teamName, String sessionId);
    }

    /**
     * Testable stream-stop boundary used during shutdown.
     *
     * <p>Mirrors Python's {@code stop_stream} call in
     * {@code openjiuwen/agent_teams/cli/tui.py}.</p>
     */
    @FunctionalInterface
    interface StreamStopper {
        CompletionStage<Void> stop(StreamHandle handle);
    }

    /**
     * Suppressed cleanup action boundary.
     *
     * <p>Mirrors Python's {@code contextlib.suppress(Exception)} cleanup blocks in
     * {@code openjiuwen/agent_teams/cli/tui.py}.</p>
     */
    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
