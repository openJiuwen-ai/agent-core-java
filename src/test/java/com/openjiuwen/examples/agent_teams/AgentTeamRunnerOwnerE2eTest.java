/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_teams;

import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTeamRunnerOwnerE2eTest {
    @Test
    void loadTeamSpecSeparatesRuntimeAndBuildsSpec() throws Exception {
        Path config = Files.createTempFile("owner-team", ".yaml");
        Files.writeString(config, """
                team_name: owner_team
                runtime:
                  session_id: owner_session
                  alt_team_name: owner_alt
                """, StandardCharsets.UTF_8);

        AgentTeamRunnerOwnerE2e.LoadedSpec loaded = AgentTeamRunnerOwnerE2e.loadTeamSpec(config);

        assertThat(loaded.baseSpec().getTeamName()).isEqualTo("owner_team");
        assertThat(loaded.runtimeConfig()).containsEntry("session_id", "owner_session");
    }

    @Test
    void initialSpecsCreatesAlternateTeamWithoutMutatingBase() {
        TeamAgentSpec base = new TeamAgentSpec();
        base.setTeamName("owner_team");

        Map<String, TeamAgentSpec> specs = AgentTeamRunnerOwnerE2e.initialSpecs(base, Map.of("alt_team_name", "owner_alt"));

        assertThat(specs).containsKeys("owner_team", "owner_alt");
        assertThat(specs.get("owner_alt").getTeamName()).isEqualTo("owner_alt");
        assertThat(base.getTeamName()).isEqualTo("owner_team");
    }

    @Test
    void startSessionCommitsAfterRuntimeReadyAck() {
        TeamAgentSpec base = new TeamAgentSpec();
        base.setTeamName("owner_team");
        FakeGateway gateway = new FakeGateway();
        AgentTeamRunnerOwnerE2e.TeamStreamCli cli = new AgentTeamRunnerOwnerE2e.TeamStreamCli(
                base,
                Map.of("owner_team", base),
                gateway,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );

        Map<String, Object> ack = cli.startSession("owner_team", "session_a", "hello");

        assertThat(ack).containsEntry("event_type", "team.runtime_ready");
        assertThat(cli.currentTeamName()).isEqualTo("owner_team");
        assertThat(cli.currentSessionId()).isEqualTo("session_a");
        assertThat(gateway.runs).containsExactly("owner_team/session_a/hello");
    }

    @Test
    void sameSessionRouteUsesInteractInsteadOfRestart() {
        TeamAgentSpec base = new TeamAgentSpec();
        base.setTeamName("owner_team");
        FakeGateway gateway = new FakeGateway();
        AgentTeamRunnerOwnerE2e.TeamStreamCli cli = new AgentTeamRunnerOwnerE2e.TeamStreamCli(
                base,
                Map.of("owner_team", base),
                gateway,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );
        cli.startSession("owner_team", "session_a", "hello");

        AgentTeamRunnerOwnerE2e.RouteResult result = cli.routeUserRequest("owner_team", "session_a", "follow-up");

        assertThat(result.action()).isEqualTo("interact");
        assertThat(gateway.interactions).containsExactly("owner_team/session_a/follow-up");
        assertThat(gateway.runs).hasSize(1);
    }

    @Test
    void changedSessionPausesOldStreamAndCommitsNewRuntime() {
        TeamAgentSpec base = new TeamAgentSpec();
        base.setTeamName("owner_team");
        FakeGateway gateway = new FakeGateway();
        AgentTeamRunnerOwnerE2e.TeamStreamCli cli = new AgentTeamRunnerOwnerE2e.TeamStreamCli(
                base,
                Map.of("owner_team", base),
                gateway,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );
        cli.startSession("owner_team", "session_a", "hello");

        AgentTeamRunnerOwnerE2e.RouteResult result = cli.routeUserRequest("owner_team", "session_b", "new query");

        assertThat(result.action()).isEqualTo("switch_committed");
        assertThat(cli.currentSessionId()).isEqualTo("session_b");
        assertThat(gateway.pauses).contains("owner_team/session_a");
        assertThat(gateway.runs).containsExactly("owner_team/session_a/hello", "owner_team/session_b/new query");
    }

    @Test
    void runnerAgentTeamStreamingEmitsRuntimeReadyBeforeAgentBody() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("runner_owner_ready_team");

        Iterator<Object> chunks = Runner.runAgentTeamStreaming(spec, Map.of("query", "hello"), "runner_owner_ready_session");

        Object first = chunks.next();
        assertThat(first).isInstanceOf(OutputSchema.class);
        Object payload = ((OutputSchema) first).getPayload();
        assertThat(payload).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadMap = (Map<String, Object>) payload;
        assertThat(payloadMap).containsEntry("event_type", "team.runtime_ready");
        assertThat(payloadMap).containsEntry("team_name", "runner_owner_ready_team");
        assertThat(payloadMap).containsEntry("session_id", "runner_owner_ready_session");
        assertThat(Runner.pauseAgentTeam("runner_owner_ready_team", "runner_owner_ready_session")).isTrue();
    }

    private static final class FakeGateway implements AgentTeamRunnerOwnerE2e.RuntimeGateway {
        private final List<String> runs = new ArrayList<>();
        private final List<String> interactions = new ArrayList<>();
        private final List<String> pauses = new ArrayList<>();

        @Override
        public Iterator<Object> runAgentTeamStreaming(TeamAgentSpec spec, Map<String, Object> inputs, String sessionId) {
            runs.add(spec.getTeamName() + "/" + sessionId + "/" + inputs.get("query"));
            return List.<Object>of(new OutputSchema("message", 0, Map.of(
                    "event_type", "team.runtime_ready",
                    "team_name", spec.getTeamName(),
                    "session_id", sessionId
            ))).iterator();
        }

        @Override
        public boolean interactAgentTeam(String userInput, String teamName, String sessionId) {
            interactions.add(teamName + "/" + sessionId + "/" + userInput);
            return true;
        }

        @Override
        public boolean pauseAgentTeam(String teamName, String sessionId) {
            pauses.add(teamName + "/" + sessionId);
            return true;
        }
    }
}
