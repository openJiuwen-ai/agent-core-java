/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused tests for the public Team CLI app entry point.
 *
 * <p>Mirrors Python's {@code run_team_cli} in
 * {@code openjiuwen/agent_teams/cli/app.py}.</p>
 */
class TeamCliAppTest {

    @TempDir
    Path tempDir;

    @Test
    void runTeamCliLoadsYamlAndMemorySpecsAroundManagedRunnerLifecycle() throws Exception {
        Path yaml = writeYaml("team.yaml", """
                team_name: yaml-team
                agents:
                  leader:
                    system_prompt: yaml
                """);
        RecordingRunnerLifecycle lifecycle = new RecordingRunnerLifecycle();
        RecordingCliFactory cliFactory = new RecordingCliFactory();
        TeamAgentSpec memory = spec("memory-team");

        TeamCliApp.runTeamCli(
                Map.of("declared", memory),
                List.of(yaml),
                List.of("/exit"),
                true,
                lifecycle,
                cliFactory
        ).toCompletableFuture().join();

        assertThat(lifecycle.events()).containsExactly("start", "stop");
        assertThat(cliFactory.created()).hasSize(1);
        RecordingTeamCli cli = cliFactory.created().get(0);
        assertThat(cli.registry().names()).containsExactly("yaml-team", "memory-team");
        assertThat(cli.events()).containsExactly("run:[/exit]", "shutdown");
    }

    @Test
    void runTeamCliFromStringPathsAcceptsPythonLikeStringYamlPaths() throws Exception {
        Path yaml = writeYaml("team.yaml", """
                team_name: string-path-team
                agents:
                  leader:
                    system_prompt: yaml
                """);
        RecordingRunnerLifecycle lifecycle = new RecordingRunnerLifecycle();
        RecordingCliFactory cliFactory = new RecordingCliFactory();

        TeamCliApp.runTeamCliFromStringPaths(
                null,
                List.of(yaml.toString()),
                List.of("/exit"),
                false,
                lifecycle,
                cliFactory
        ).toCompletableFuture().join();

        assertThat(lifecycle.events()).isEmpty();
        assertThat(cliFactory.created().get(0).registry().names()).containsExactly("string-path-team");
        assertThat(cliFactory.created().get(0).events()).containsExactly("run:[/exit]", "shutdown");
    }

    @Test
    void runFailureStillShutsDownStopsAndPropagatesOriginalFailure() {
        RecordingRunnerLifecycle lifecycle = new RecordingRunnerLifecycle();
        IllegalStateException failure = new IllegalStateException("run failed");
        RecordingCliFactory cliFactory = new RecordingCliFactory();
        cliFactory.failRunWith(failure);

        CompletionException thrown = assertThrows(CompletionException.class, () ->
                TeamCliApp.runTeamCli(
                        null,
                        null,
                        List.of("boom"),
                        true,
                        lifecycle,
                        cliFactory
                ).toCompletableFuture().join());

        assertSame(failure, thrown.getCause());
        assertThat(lifecycle.events()).containsExactly("start", "stop");
        assertThat(cliFactory.created().get(0).events()).containsExactly("run:[boom]", "shutdown");
    }

    @Test
    void startFailureSkipsRunShutdownAndStop() {
        RecordingRunnerLifecycle lifecycle = new RecordingRunnerLifecycle();
        IllegalStateException failure = new IllegalStateException("start failed");
        lifecycle.failStartWith(failure);
        RecordingCliFactory cliFactory = new RecordingCliFactory();

        CompletionException thrown = assertThrows(CompletionException.class, () ->
                TeamCliApp.runTeamCli(
                        null,
                        null,
                        List.of("ignored"),
                        true,
                        lifecycle,
                        cliFactory
                ).toCompletableFuture().join());

        assertSame(failure, thrown.getCause());
        assertThat(lifecycle.events()).containsExactly("start");
        assertThat(cliFactory.created()).hasSize(1);
        assertThat(cliFactory.created().get(0).events()).isEmpty();
    }

    @Test
    void shutdownFailurePreventsManagedStopAndOverridesRunFailure() {
        RecordingRunnerLifecycle lifecycle = new RecordingRunnerLifecycle();
        IllegalArgumentException runFailure = new IllegalArgumentException("run failed");
        IllegalStateException shutdownFailure = new IllegalStateException("shutdown failed");
        RecordingCliFactory cliFactory = new RecordingCliFactory();
        cliFactory.failRunWith(runFailure);
        cliFactory.failShutdownWith(shutdownFailure);

        CompletionException thrown = assertThrows(CompletionException.class, () ->
                TeamCliApp.runTeamCli(
                        null,
                        null,
                        List.of("boom"),
                        true,
                        lifecycle,
                        cliFactory
                ).toCompletableFuture().join());

        assertSame(shutdownFailure, thrown.getCause());
        assertThat(lifecycle.events()).containsExactly("start");
        assertThat(cliFactory.created().get(0).events()).containsExactly("run:[boom]", "shutdown");
    }

    private Path writeYaml(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private static TeamAgentSpec spec(String teamName) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName(teamName);
        AgentConfigurator.DeepAgentSpec leader = new AgentConfigurator.DeepAgentSpec();
        leader.setSystemPrompt("memory");
        spec.setAgents(Map.of("leader", leader));
        return spec;
    }

    /**
     * Test lifecycle recorder for the Team CLI app entry.
     *
     * <p>Mirrors Python's {@code Runner.start()} and {@code Runner.stop()} effects in
     * {@code openjiuwen/agent_teams/cli/app.py}.</p>
     */
    private static final class RecordingRunnerLifecycle implements TeamCliApp.RunnerLifecycle {
        private final List<String> events = new ArrayList<>();
        private Throwable startFailure;

        @Override
        public CompletionStage<Boolean> start() {
            events.add("start");
            if (startFailure != null) {
                return CompletableFuture.failedFuture(startFailure);
            }
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<Boolean> stop() {
            events.add("stop");
            return CompletableFuture.completedFuture(true);
        }

        private void failStartWith(Throwable failure) {
            startFailure = failure;
        }

        private List<String> events() {
            return events;
        }
    }

    /**
     * Test CLI factory for observing registry setup and CLI lifecycle calls.
     *
     * <p>Mirrors Python's {@code TeamCli(registry)} construction in
     * {@code openjiuwen/agent_teams/cli/app.py}.</p>
     */
    private static final class RecordingCliFactory implements TeamCliApp.CliFactory {
        private final List<RecordingTeamCli> created = new ArrayList<>();
        private Throwable runFailure;
        private Throwable shutdownFailure;

        @Override
        public TeamCli create(SpecRegistry registry) {
            RecordingTeamCli cli = new RecordingTeamCli(registry, runFailure, shutdownFailure);
            created.add(cli);
            return cli;
        }

        private void failRunWith(Throwable failure) {
            runFailure = failure;
        }

        private void failShutdownWith(Throwable failure) {
            shutdownFailure = failure;
        }

        private List<RecordingTeamCli> created() {
            return created;
        }
    }

    /**
     * Test CLI that records run and shutdown without using the interactive prompt.
     *
     * <p>Mirrors Python's {@code TeamCli.run(input_iter=...)} and {@code TeamCli.shutdown()} calls in
     * {@code openjiuwen/agent_teams/cli/app.py}.</p>
     */
    private static final class RecordingTeamCli extends TeamCli {
        private final SpecRegistry registry;
        private final Throwable runFailure;
        private final Throwable shutdownFailure;
        private final List<String> events = new ArrayList<>();

        private RecordingTeamCli(SpecRegistry registry, Throwable runFailure, Throwable shutdownFailure) {
            super(
                    registry,
                    new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                    new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                    new BufferedReader(new StringReader("")),
                    (context, line) -> CompletableFuture.completedFuture(null),
                    (teamName, sessionId, memberName) -> CompletableFuture.completedFuture(true),
                    (teamName, sessionId) -> CompletableFuture.completedFuture(true),
                    handle -> CompletableFuture.completedFuture(null)
            );
            this.registry = registry;
            this.runFailure = runFailure;
            this.shutdownFailure = shutdownFailure;
        }

        @Override
        public CompletionStage<Void> run(Iterable<String> inputIter) {
            events.add("run:" + toList(inputIter));
            if (runFailure != null) {
                return CompletableFuture.failedFuture(runFailure);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> shutdown() {
            events.add("shutdown");
            if (shutdownFailure != null) {
                return CompletableFuture.failedFuture(shutdownFailure);
            }
            return CompletableFuture.completedFuture(null);
        }

        private SpecRegistry registry() {
            return registry;
        }

        private List<String> events() {
            return events;
        }

        private static List<String> toList(Iterable<String> values) {
            if (values == null) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (String value : values) {
                result.add(value);
            }
            return result;
        }
    }
}
