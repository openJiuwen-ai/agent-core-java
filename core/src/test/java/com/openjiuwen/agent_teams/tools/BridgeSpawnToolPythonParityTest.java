/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.BridgeMailboxInjectMode;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.tools.TeamTools.SpawnMemberTool;
import com.openjiuwen.agent_teams.tools.locales.TeamToolLocales;
import com.openjiuwen.harness.tools.ToolOutput;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bridge-agent spawn tool parity tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/tools/test_bridge_spawn_tool.py}.</p>
 */
class BridgeSpawnToolPythonParityTest {

    @TempDir
    private Path tempDir;

    @Test
    void bridgeSpawnRejectedWhenDisabled() {
        TeamBackend backend = backend(false);
        SpawnMemberTool tool = tool(backend);

        ToolOutput output = tool.invoke(Map.of(
                "member_name", "codex",
                "display_name", "Codex",
                "desc", "reviewer",
                "role_type", "bridge_agent"
        )).toCompletableFuture().join();

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("Bridge capability is disabled");
    }

    @Test
    void bridgeSpawnRequiresDesc() {
        TeamBackend backend = backend(true);
        SpawnMemberTool tool = tool(backend);

        ToolOutput output = tool.invoke(Map.of(
                "member_name", "codex",
                "display_name", "Codex",
                "desc", "",
                "role_type", "bridge_agent"
        )).toCompletableFuture().join();

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).satisfiesAnyOf(
                error -> assertThat(error.toLowerCase()).contains("persona"),
                error -> assertThat(error.toLowerCase()).contains("desc")
        );
    }

    @Test
    void bridgeSpawnHappyPath() {
        TeamBackend backend = backend(true);
        SpawnMemberTool tool = tool(backend);

        ToolOutput output = tool.invoke(Map.of(
                "member_name", "codex",
                "display_name", "Codex",
                "desc", "senior python reviewer",
                "role_type", "bridge_agent",
                "mailbox_inject_mode", "rephrase",
                "protocol", "codex",
                "adapter_config", Map.of("endpoint", "stdio://codex"),
                "model_name", "gpt-4"
        )).toCompletableFuture().join();

        assertThat(output.isSuccess()).isTrue();
        Map<String, Object> data = dataMap(output);
        assertThat(data)
                .containsEntry("role_type", "bridge_agent")
                .containsEntry("mailbox_inject_mode", "rephrase")
                .containsEntry("protocol", "codex");
        assertThat(backend.isBridgeAgent("codex")).isTrue();
        assertThat(backend.getBridgeMemberSpec("codex")).isNotNull();
        assertThat(backend.getBridgeMemberSpec("codex").getMailboxInjectMode())
                .isEqualTo(BridgeMailboxInjectMode.REPHRASE);
        assertThat(backend.getBridgeMemberSpec("codex").getProtocol()).isEqualTo("codex");
        assertThat(backend.getBridgeMemberSpec("codex").getAdapterConfig())
                .isEqualTo(Map.of("endpoint", "stdio://codex"));
    }

    @Test
    void bridgeSpawnRejectsBadInjectMode() {
        TeamBackend backend = backend(true);
        SpawnMemberTool tool = tool(backend);

        ToolOutput output = tool.invoke(Map.of(
                "member_name", "codex",
                "display_name", "Codex",
                "desc", "x",
                "role_type", "bridge_agent",
                "mailbox_inject_mode", "summarize"
        )).toCompletableFuture().join();

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("mailbox_inject_mode");
    }

    @Test
    void bridgeSpawnRejectsNonMapAdapterConfig() {
        TeamBackend backend = backend(true);
        SpawnMemberTool tool = tool(backend);

        ToolOutput output = tool.invoke(Map.of(
                "member_name", "codex",
                "display_name", "Codex",
                "desc", "x",
                "role_type", "bridge_agent",
                "adapter_config", "not-a-dict"
        )).toCompletableFuture().join();

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("adapter_config");
    }

    @Test
    void invalidRoleTypeListsExpectedChoices() {
        TeamBackend backend = backend(true);
        SpawnMemberTool tool = tool(backend);

        ToolOutput output = tool.invoke(Map.of(
                "member_name", "x",
                "display_name", "X",
                "desc", "y",
                "role_type", "alien"
        )).toCompletableFuture().join();

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError())
                .contains("teammate")
                .contains("human_agent")
                .contains("bridge_agent");
    }

    private SpawnMemberTool tool(TeamBackend backend) {
        return new SpawnMemberTool(backend, TeamToolLocales.makeTranslator("cn"), null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataMap(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    private TeamBackend backend(boolean enableBridge) {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        TeamBackend backend = new TeamBackend(
                "bt",
                "team_leader",
                true,
                database,
                new NoopMessager(),
                MemberMode.BUILD_MODE,
                List.of(),
                null,
                null,
                true,
                enableBridge,
                List.of(),
                null,
                null,
                tempDir,
                "team plan",
                null
        );
        backend.buildTeam("bt", "goal", "L", "leader persona").toCompletableFuture().join();
        return backend;
    }

    private static final class NoopMessager implements Messager {
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
