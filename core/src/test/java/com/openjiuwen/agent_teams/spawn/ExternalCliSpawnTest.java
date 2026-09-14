/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentCustomizer;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.agent.MemberRuntime;
import com.openjiuwen.agent_teams.agent.SpawnManager;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.external.cli_agent.CliAgentSpawn;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link ExternalCliSpawn}.
 *
 * <p>Mirrors Python's {@code external_cli_spawn.py} behavior in
 * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
 */
class ExternalCliSpawnTest {

    @Test
    void defaultJoinPromptPrependsSystemPromptForGenericAdapterAndPassesStaticConfig() {
        TeamAgentSpec spec = minimalSpec();
        Map<String, Object> config = Map.of(
                "cli_agent", "generic",
                "cwd", "D:/workspace/team",
                "command", List.of("fake-cli", "--json"),
                "inject_mcp", false,
                "mcp_server_command", List.of("team-mcp", "--stdio"),
                "env", Map.of("P2J_SLOT", "05")
        );
        spec.setExternalCliAgents(List.of((Object) config));
        spec.setExposeHumanAgentsToTeammates(true);
        TeamAgent leader = leaderAgent(spec);
        TeamRuntimeContext ctx = memberContext("generic");
        RecordingRuntimeBuilder builder = new RecordingRuntimeBuilder();
        RecordingMemberRunner runner = new RecordingMemberRunner();

        SpawnManager.InProcessSpawnHandle handle = ExternalCliSpawn.externalCliSpawn(
                leader,
                ctx,
                null,
                "session-1",
                builder,
                runner,
                new FixedRosterLookup(List.of("bridge-z", "bridge-a"), List.of("human-b", "human-a"))
        ).toCompletableFuture().join();

        assertThat(handle).isInstanceOf(ExternalCliSpawn.SpawnedExternalCliHandle.class);
        assertThat(((ExternalCliSpawn.SpawnedExternalCliHandle) handle).getProcessId()).isEqualTo("extcli-dev");
        assertThat(runner.memberFlag.get()).isTrue();
        assertThat(runner.sessionId.get()).isEqualTo("session-1");
        assertThat(runner.teammate.get().getCard().getId()).isEqualTo("team-a_dev");
        assertThat(builder.options.get().cwd()).isEqualTo("D:/workspace/team");
        assertThat(builder.options.get().commandOverride()).containsExactly("fake-cli", "--json");
        assertThat(builder.options.get().injectMcp()).isFalse();
        assertThat(builder.options.get().mcpServerCommand()).containsExactly("team-mcp", "--stdio");
        assertThat(builder.options.get().extraEnv()).containsEntry("P2J_SLOT", "05");

        String systemPrompt = builder.options.get().systemPrompt();
        String query = runner.query();
        assertThat(systemPrompt).contains("human-a", "human-b", "bridge-a", "bridge-z");
        assertThat(query).startsWith(systemPrompt + "\n\n---\n\n");
        assertThat(query)
                .contains("read_inbox once")
                .contains("END YOUR TURN")
                .contains("do NOT wait, poll, or loop");
    }

    @Test
    void codexAdapterCarriesSystemPromptViaLaunchArgsWithoutPrependingFirstQuery() {
        TeamAgentSpec spec = minimalSpec();
        TeamAgent leader = leaderAgent(spec);
        TeamRuntimeContext ctx = memberContext("codex");
        RecordingRuntimeBuilder builder = new RecordingRuntimeBuilder();
        RecordingMemberRunner runner = new RecordingMemberRunner();

        ExternalCliSpawn.externalCliSpawn(
                leader,
                ctx,
                "please inspect inbox",
                "session-2",
                builder,
                runner,
                new FixedRosterLookup(List.of(), List.of())
        ).toCompletableFuture().join();

        assertThat(builder.options.get().systemPrompt()).isNotBlank();
        assertThat(runner.query()).isEqualTo("please inspect inbox");
    }

    @Test
    void handleTracksLivenessAndClosesRuntimeAfterMemberTaskCompletes() {
        TeamAgentSpec spec = minimalSpec();
        TeamAgent leader = leaderAgent(spec);
        TeamRuntimeContext ctx = memberContext("generic");
        RecordingRuntimeBuilder builder = new RecordingRuntimeBuilder();
        RecordingMemberRunner runner = new RecordingMemberRunner();

        ExternalCliSpawn.SpawnedExternalCliHandle handle = (ExternalCliSpawn.SpawnedExternalCliHandle)
                ExternalCliSpawn.externalCliSpawn(
                        leader,
                        ctx,
                        "work now",
                        null,
                        builder,
                        runner,
                        new FixedRosterLookup(List.of(), List.of())
                ).toCompletableFuture().join();

        assertThat(handle.isAlive()).isTrue();
        assertThat(builder.runtime.closed.get()).isFalse();

        runner.runFuture.complete(null);
        handle.getTask().join();

        assertThat(handle.isAlive()).isFalse();
        assertThat(builder.runtime.closed.get()).isTrue();
        assertThat(handle.getAgentRef()).isInstanceOf(TeamAgent.class);
    }

    @Test
    void forceKillCancelsHandleAndAbortsRuntime() {
        TeamAgentSpec spec = minimalSpec();
        TeamAgent leader = leaderAgent(spec);
        TeamRuntimeContext ctx = memberContext("generic");
        RecordingRuntimeBuilder builder = new RecordingRuntimeBuilder();
        RecordingMemberRunner runner = new RecordingMemberRunner();

        ExternalCliSpawn.SpawnedExternalCliHandle handle = (ExternalCliSpawn.SpawnedExternalCliHandle)
                ExternalCliSpawn.externalCliSpawn(
                        leader,
                        ctx,
                        "work now",
                        null,
                        builder,
                        runner,
                        new FixedRosterLookup(List.of(), List.of())
                ).toCompletableFuture().join();

        handle.forceKill().toCompletableFuture().join();

        assertThat(handle.isAlive()).isFalse();
        assertThat(builder.runtime.aborted.get()).isTrue();
        assertThat(builder.runtime.closed.get()).isTrue();
    }

    private static TeamAgentSpec minimalSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("team-a");
        spec.getAgents().put("leader", new DeepAgentSpec());
        spec.getAgents().put("teammate", new DeepAgentSpec());
        return spec;
    }

    private static TeamAgent leaderAgent(TeamAgentSpec spec) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.LEADER);
        ctx.setMemberName("leader");
        ctx.setTeamSpec(new TeamSpec("team-a", "Team A", "leader"));
        ctx.getTeamSpec().setLanguage("en");
        TeamAgent leader = new TeamAgent(new AgentCard("team-a_leader", "leader", "Leader"));
        leader.configure(spec, ctx, new FakeMemberRuntime());
        return leader;
    }

    private static TeamRuntimeContext memberContext(String cliAgent) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.TEAMMATE);
        ctx.setMemberName("dev");
        ctx.setPersona("write code");
        ctx.setTeamSpec(new TeamSpec("team-a", "Team A", "leader"));
        ctx.getTeamSpec().setLanguage("en");
        ctx.setCliAgent(cliAgent);
        return ctx;
    }

    /**
     * Records runtime build options for parity assertions.
     *
     * <p>Mirrors Python's {@code build_cli_runtime} dependency in
     * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
     */
    private static final class RecordingRuntimeBuilder implements ExternalCliSpawn.CliRuntimeBuilder {
        private final FakeMemberRuntime runtime = new FakeMemberRuntime();
        private final AtomicReference<CliAgentSpawn.BuildOptions> options = new AtomicReference<>();

        @Override
        public CompletionStage<MemberRuntime> build(TeamRuntimeContext ctx, CliAgentSpawn.BuildOptions options) {
            this.options.set(options);
            return CompletableFuture.completedFuture(runtime);
        }
    }

    /**
     * Captures the external CLI member invocation without running a real team loop.
     *
     * <p>Mirrors Python's {@code Runner.run_agent_team} call in
     * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
     */
    private static final class RecordingMemberRunner implements ExternalCliSpawn.MemberRunner {
        private final CompletableFuture<Void> runFuture = new CompletableFuture<>();
        private final AtomicReference<TeamAgent> teammate = new AtomicReference<>();
        private final AtomicReference<Map<String, Object>> inputs = new AtomicReference<>();
        private final AtomicBoolean memberFlag = new AtomicBoolean(false);
        private final AtomicReference<String> sessionId = new AtomicReference<>();

        @Override
        public CompletionStage<Void> run(
                TeamAgent teammate,
                Map<String, Object> inputs,
                boolean member,
                String sessionId
        ) {
            this.teammate.set(teammate);
            this.inputs.set(inputs);
            this.memberFlag.set(member);
            this.sessionId.set(sessionId);
            return runFuture;
        }

        private String query() {
            return String.valueOf(inputs.get().get("query"));
        }
    }

    /**
     * Supplies deterministic roster names for system prompt assertions.
     *
     * <p>Mirrors Python's backend roster inputs in
     * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
     */
    private record FixedRosterLookup(
            Collection<String> bridgeNames,
            List<String> humanNames
    ) implements ExternalCliSpawn.RosterLookup {
        @Override
        public Collection<String> bridgeAgentNames(AgentConfigurator.ConfiguredTeamBackend backend) {
            return bridgeNames;
        }

        @Override
        public CompletionStage<List<String>> humanAgentNames(AgentConfigurator.ConfiguredTeamBackend backend) {
            return CompletableFuture.completedFuture(humanNames);
        }
    }

    /**
     * Test runtime that tracks abort and close calls.
     *
     * <p>Mirrors Python's external CLI runtime cleanup contract in
     * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
     */
    private static class FakeMemberRuntime implements MemberRuntime {
        private final AtomicBoolean aborted = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
            return List.of().iterator();
        }

        @Override
        public CompletionStage<Void> steer(String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> followUp(String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> abort() {
            aborted.set(true);
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> aclose() {
            closed.set(true);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void initCwdForRound() {
        }

        @Override
        public boolean hasPendingInterrupt() {
            return false;
        }

        @Override
        public boolean isPendingInterruptResumeValid(Object userInput) {
            return false;
        }

        @Override
        public List<Object> findRails(Class<?> railType) {
            return List.of();
        }

        @Override
        public CompletionStage<Void> registerRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void registerMemberTools(Object memoryManager) {
        }

        @Override
        public CompletionStage<Void> injectMemberMemory(Object memoryManager, String query) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void runAgentCustomizer(AgentCustomizer customizer) {
        }

        @Override
        public Object workspace() {
            return null;
        }

        @Override
        public Object sysOperation() {
            return null;
        }
    }
}
