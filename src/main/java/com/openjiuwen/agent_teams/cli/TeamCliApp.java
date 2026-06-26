/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.runner.Runner;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Public application entry point for running the Team CLI.
 *
 * <p>Mirrors Python's {@code run_team_cli} in
 * {@code openjiuwen/agent_teams/cli/app.py}.</p>
 */
public final class TeamCliApp {

    private static final RunnerLifecycle DEFAULT_RUNNER_LIFECYCLE = new DefaultRunnerLifecycle();
    private static final CliFactory DEFAULT_CLI_FACTORY = TeamCli::new;

    private TeamCliApp() {
    }

    public static CompletionStage<Void> runTeamCli() {
        return runTeamCli(null, null, null, true);
    }

    public static CompletionStage<Void> runTeamCli(
            Map<String, TeamAgentSpec> specs,
            Iterable<? extends Path> yamlPaths,
            Iterable<String> inputIter,
            boolean manageRunner
    ) {
        return runTeamCli(specs, yamlPaths, inputIter, manageRunner, DEFAULT_RUNNER_LIFECYCLE, DEFAULT_CLI_FACTORY);
    }

    public static CompletionStage<Void> runTeamCliFromStringPaths(
            Map<String, TeamAgentSpec> specs,
            Iterable<String> yamlPaths,
            Iterable<String> inputIter,
            boolean manageRunner
    ) {
        Iterable<Path> paths = yamlPaths == null ? null : () -> new PathIterator(yamlPaths.iterator());
        return runTeamCli(specs, paths, inputIter, manageRunner);
    }

    static CompletionStage<Void> runTeamCliFromStringPaths(
            Map<String, TeamAgentSpec> specs,
            Iterable<String> yamlPaths,
            Iterable<String> inputIter,
            boolean manageRunner,
            RunnerLifecycle runnerLifecycle,
            CliFactory cliFactory
    ) {
        Iterable<Path> paths = yamlPaths == null ? null : () -> new PathIterator(yamlPaths.iterator());
        return runTeamCli(specs, paths, inputIter, manageRunner, runnerLifecycle, cliFactory);
    }

    static CompletionStage<Void> runTeamCli(
            Map<String, TeamAgentSpec> specs,
            Iterable<? extends Path> yamlPaths,
            Iterable<String> inputIter,
            boolean manageRunner,
            RunnerLifecycle runnerLifecycle,
            CliFactory cliFactory
    ) {
        SpecRegistry registry = new SpecRegistry();
        if (yamlPaths != null) {
            registry.bulkLoadYaml(yamlPaths);
        }
        if (specs != null) {
            registry.bulkRegister(specs);
        }

        TeamCli cli = cliFactory.create(registry);
        CompletionStage<Void> ready = manageRunner
                ? runnerLifecycle.start().thenApply(ignored -> null)
                : CompletableFuture.completedFuture(null);
        return ready.thenCompose(ignored -> runWithCleanup(cli, inputIter, manageRunner, runnerLifecycle));
    }

    private static CompletionStage<Void> runWithCleanup(
            TeamCli cli,
            Iterable<String> inputIter,
            boolean manageRunner,
            RunnerLifecycle runnerLifecycle
    ) {
        return cli.run(inputIter)
                .handle((ignored, runFailure) -> runFailure)
                .thenCompose(runFailure -> cleanupAfterRun(cli, manageRunner, runnerLifecycle, runFailure));
    }

    private static CompletionStage<Void> cleanupAfterRun(
            TeamCli cli,
            boolean manageRunner,
            RunnerLifecycle runnerLifecycle,
            Throwable runFailure
    ) {
        return cli.shutdown()
                .thenCompose(ignored -> stopIfManaged(manageRunner, runnerLifecycle))
                .thenCompose(ignored -> {
                    if (runFailure == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return CompletableFuture.failedFuture(peel(runFailure));
                });
    }

    private static CompletionStage<Void> stopIfManaged(boolean manageRunner, RunnerLifecycle runnerLifecycle) {
        if (!manageRunner) {
            return CompletableFuture.completedFuture(null);
        }
        return runnerLifecycle.stop().thenApply(ignored -> null);
    }

    private static Throwable peel(Throwable failure) {
        if (failure instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return failure;
    }

    /**
     * Testable factory for constructing the CLI after registry setup.
     *
     * <p>Mirrors Python's {@code cli = TeamCli(registry)} construction in
     * {@code openjiuwen/agent_teams/cli/app.py}.</p>
     */
    @FunctionalInterface
    interface CliFactory {
        TeamCli create(SpecRegistry registry);
    }

    /**
     * Testable runner lifecycle boundary.
     *
     * <p>Mirrors Python's {@code Runner.start()} and {@code Runner.stop()} calls in
     * {@code openjiuwen/agent_teams/cli/app.py}.</p>
     */
    interface RunnerLifecycle {
        CompletionStage<Boolean> start();

        CompletionStage<Boolean> stop();
    }

    /**
     * Production runner lifecycle backed by the process-global Runner facade.
     *
     * <p>Mirrors Python's imported {@code Runner} in
     * {@code openjiuwen/agent_teams/cli/app.py}.</p>
     */
    private static final class DefaultRunnerLifecycle implements RunnerLifecycle {
        @Override
        public CompletionStage<Boolean> start() {
            return Runner.start();
        }

        @Override
        public CompletionStage<Boolean> stop() {
            return Runner.stop();
        }
    }

    /**
     * Path iterator adapter for Java callers that pass Python-like string paths.
     *
     * <p>Mirrors Python's {@code Iterable[str | Path]} accepted by
     * {@code openjiuwen/agent_teams/cli/app.py}.</p>
     */
    private static final class PathIterator implements java.util.Iterator<Path> {
        private final java.util.Iterator<String> delegate;

        private PathIterator(java.util.Iterator<String> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Path next() {
            return Path.of(delegate.next());
        }
    }
}
