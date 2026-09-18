/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.interaction.BridgeProtocolAdapter;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.BridgeMailboxInjectMode;
import com.openjiuwen.agent_teams.schema.BridgeMemberSpec;
import com.openjiuwen.agent_teams.schema.MemberOpResult;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Missing-test parity coverage for {@link TeamBackend}'s bridge-agent surface.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.tools.test_bridge_backend} in
 * {@code tests/unit_tests/agent_teams/tools/test_bridge_backend.py}.</p>
 */
class TeamBackendBridgeBackendTest {

    private static final String TEAM_NAME = "bridge_team";
    private static final String LEADER_NAME = "team_leader";
    private static final String SESSION_ID = "bridge_session";

    @TempDir
    private Path tempDir;

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @Test
    void bridgeDisabledByDefault() {
        TeamBackend backend = new TeamBackend("t", LEADER_NAME, true, new InMemoryTeamDatabase(), new NoOpMessager());

        assertThat(backend.bridgeEnabled()).isFalse();
        assertThat(backend.bridgeAgentNames()).isEmpty();
    }

    @Test
    void predefinedBridgeIndexedAtInit() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        BridgeMemberSpec spec = bridgeSpec("codex", "Codex", "reviewer");
        spec.setProtocol("codex");

        TeamBackend backend = backend(database, new NoOpMessager(), true, List.of(spec));

        assertThat(backend.isBridgeAgent("codex")).isTrue();
        assertThat(backend.bridgeAgentNames()).containsExactly("codex");
        BridgeMemberSpec indexed = backend.getBridgeMemberSpec("codex");
        assertThat(indexed).isNotNull();
        assertThat(indexed.getProtocol()).isEqualTo("codex");
    }

    @Test
    void spawnBridgeAgentFailsWhenDisabled() {
        TeamBackend backend = backend(new InMemoryTeamDatabase(), new NoOpMessager(), false, List.of());

        MemberOpResult result = join(backend.spawnBridgeAgent(
                "codex",
                "Codex",
                "reviewer",
                null,
                null,
                null,
                "",
                Map.of()
        ));

        assertThat(result.isOk()).isFalse();
        assertThat(result.getReason()).contains("disabled");
    }

    @Test
    void spawnBridgeAgentRequiresPersona() {
        TeamBackend backend = backend(new InMemoryTeamDatabase(), new NoOpMessager(), true, List.of());

        MemberOpResult result = join(backend.spawnBridgeAgent(
                "codex",
                "Codex",
                "",
                null,
                null,
                null,
                "",
                Map.of()
        ));

        assertThat(result.isOk()).isFalse();
        assertThat(result.getReason()).contains("persona");
    }

    @Test
    void spawnBridgeAgentRegistersIndexAndDatabase() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        TeamBackend backend = backend(database, new NoOpMessager(), true, List.of());
        buildEmptyTeam(backend);

        MemberOpResult result = join(backend.spawnBridgeAgent(
                "codex",
                "Codex",
                "senior python reviewer",
                null,
                null,
                BridgeMailboxInjectMode.REPHRASE,
                "codex",
                Map.of("endpoint", "stdio://codex")
        ));

        assertThat(result.isOk()).isTrue();
        assertThat(backend.isBridgeAgent("codex")).isTrue();
        BridgeMemberSpec indexed = backend.getBridgeMemberSpec("codex");
        assertThat(indexed).isNotNull();
        assertThat(indexed.getMailboxInjectMode()).isEqualTo(BridgeMailboxInjectMode.REPHRASE);
        assertThat(indexed.getProtocol()).isEqualTo("codex");
        assertThat(indexed.getAdapterConfig()).containsEntry("endpoint", "stdio://codex");
        assertThat(database.getMember("codex", TEAM_NAME).join()).isPresent();
    }

    @Test
    void spawnBridgeAgentDuplicateFails() {
        TeamBackend backend = backend(new InMemoryTeamDatabase(), new NoOpMessager(), true, List.of());
        buildEmptyTeam(backend);

        MemberOpResult first = join(backend.spawnBridgeAgent(
                "codex",
                "Codex",
                "r",
                null,
                null,
                null,
                "",
                Map.of()
        ));
        MemberOpResult second = join(backend.spawnBridgeAgent(
                "codex",
                "Codex Again",
                "r2",
                null,
                null,
                null,
                "",
                Map.of()
        ));

        assertThat(first.isOk()).isTrue();
        assertThat(second.isOk()).isFalse();
        assertThat(second.getReason()).contains("already exists");
    }

    @Test
    void setBridgeAdapterUnknownMemberRaises() {
        TeamBackend backend = backend(new InMemoryTeamDatabase(), new NoOpMessager(), true, List.of());

        assertThatThrownBy(() -> backend.setBridgeAdapter("missing", new StubAdapter()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a registered bridge-agent member");
    }

    @Test
    void setBridgeAdapterRoundTrip() {
        TeamBackend backend = backend(new InMemoryTeamDatabase(), new NoOpMessager(), true, List.of());
        buildEmptyTeam(backend);
        join(backend.spawnBridgeAgent("codex", "Codex", "r", null, null, null, "", Map.of()));

        assertThat(backend.getBridgeAdapter("codex")).isNull();
        StubAdapter adapter = new StubAdapter();
        backend.setBridgeAdapter("codex", adapter);
        BridgeProtocolAdapter fetched = backend.getBridgeAdapter("codex");

        assertThat(fetched).isSameAs(adapter);
        assertThat(BridgeProtocolAdapter.isAdapter(fetched)).isTrue();
    }

    @Test
    void setBridgeAdapterNoneClears() {
        TeamBackend backend = backend(new InMemoryTeamDatabase(), new NoOpMessager(), true, List.of());
        buildEmptyTeam(backend);
        join(backend.spawnBridgeAgent("codex", "Codex", "r", null, null, null, "", Map.of()));

        backend.setBridgeAdapter("codex", new StubAdapter());
        backend.setBridgeAdapter("codex", null);

        assertThat(backend.getBridgeAdapter("codex")).isNull();
    }

    @Test
    void buildTeamWithPredefinedBridgePersists() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        BridgeMemberSpec spec = bridgeSpec("codex", "Codex", "r");
        spec.setProtocol("codex");
        TeamBackend backend = backend(database, new NoOpMessager(), true, List.of(spec));

        buildEmptyTeam(backend);

        assertThat(database.getMember("codex", TEAM_NAME).join()).isPresent();
        assertThat(backend.isBridgeAgent("codex")).isTrue();
    }

    @Test
    void buildTeamSkipsPredefinedBridgeWhenDisabled() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        TeamBackend backend = backend(database, new NoOpMessager(), true, List.of(bridgeSpec("codex", "Codex", "r")));

        join(backend.buildTeam(
                TEAM_NAME,
                "goal",
                "L",
                "leader persona",
                new TeamBackend.CapabilityOverrides(null, false)
        ));

        assertThat(database.getMember("codex", TEAM_NAME).join()).isEmpty();
        assertThat(backend.isBridgeAgent("codex")).isFalse();
        assertThat(backend.bridgeEnabled()).isFalse();
    }

    @Test
    void buildTeamEnableBridgeAboveCeilingRaises() {
        TeamBackend backend = backend(new InMemoryTeamDatabase(), new NoOpMessager(), false, List.of());

        assertThatThrownBy(() -> join(backend.buildTeam(
                TEAM_NAME,
                "goal",
                "L",
                "leader persona",
                new TeamBackend.CapabilityOverrides(null, true)
        ))).hasRootCauseMessage("build_team(enable_bridge=True) requires TeamAgentSpec.enable_bridge=True "
                + "(capability ceiling)");
    }

    private TeamBackend backend(
            InMemoryTeamDatabase database,
            Messager messager,
            boolean enableBridge,
            List<TeamMemberSpec> predefinedMembers) {
        AgentTeamsContext.setSessionId(SESSION_ID);
        return new TeamBackend(
                TEAM_NAME,
                LEADER_NAME,
                true,
                database,
                messager,
                MemberMode.BUILD_MODE,
                predefinedMembers,
                null,
                null,
                false,
                enableBridge,
                List.of(),
                null,
                null,
                tempDir,
                "bridge-plan",
                null
        );
    }

    private static void buildEmptyTeam(TeamBackend backend) {
        join(backend.buildTeam(TEAM_NAME, "goal", "L", "leader persona"));
    }

    private static BridgeMemberSpec bridgeSpec(String memberName, String displayName, String persona) {
        BridgeMemberSpec spec = new BridgeMemberSpec();
        spec.setMemberName(memberName);
        spec.setDisplayName(displayName);
        spec.setPersona(persona);
        return spec;
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    /**
     * Stub bridge protocol adapter used by adapter registration parity tests.
     *
     * <p>Mirrors Python's {@code _StubAdapter} in
     * {@code tests/unit_tests/agent_teams/tools/test_bridge_backend.py}.</p>
     */
    private static final class StubAdapter implements BridgeProtocolAdapter {

        @Override
        public CompletionStage<Void> connect(
                String memberName,
                Map<String, Object> adapterConfig,
                String bridgePersona,
                String teamOverview) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<String> relay(String memberName, String text) {
            return CompletableFuture.completedFuture("got: " + text);
        }

        @Override
        public CompletionStage<Void> close() {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * No-op messager fixture for backend unit tests.
     *
     * <p>Mirrors Python's {@code AsyncMock(spec=Messager)} fixture in
     * {@code tests/unit_tests/agent_teams/tools/test_bridge_backend.py}.</p>
     */
    private static final class NoOpMessager implements Messager {

        @Override
        public CompletionStage<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String topicId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe(String topicId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String agentId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterDirectMessageHandler() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
