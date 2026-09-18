/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link MemberFactory}.
 *
 * <p>Mirrors Python's {@code create_member_handle} in
 * {@code openjiuwen/agent_teams/agent/member_factory.py}.</p>
 */
class MemberFactoryTest {

    @Test
    void returnsNullWhenTeamBackendIsMissing() {
        TeamInfra infra = new TeamInfra();

        TeamMember handle = MemberFactory.createMemberHandle(
                "member1",
                blueprint("persona"),
                infra,
                new AgentCard("agent", "Agent", "description")
        );

        assertNull(handle);
    }

    @Test
    void createsTeamMemberFromBoundBackendAndBlueprintPersona() {
        TeamInfra infra = new TeamInfra();
        RecordingMessager messager = new RecordingMessager();
        RecordingStore store = new RecordingStore();
        infra.setMessager(messager);
        infra.setTeamBackend(new ConfiguredTeamBackend(
                "team-a",
                "leader",
                true,
                Map.of(),
                messager,
                "",
                List.of(),
                null,
                null,
                true,
                false,
                List.of(),
                null,
                null,
                "leader",
                store
        ));
        AgentCard card = new AgentCard("agent", "Agent", "description");

        TeamMember handle = MemberFactory.createMemberHandle("member1", blueprint("Build things"), infra, card);

        assertEquals("member1", handle.getMemberName());
        assertEquals("team-a", handle.getTeamName());
        assertEquals("member1", handle.getDisplayName());
        assertSame(card, handle.getAgentCard());
        assertSame(store, handle.getDb());
        assertSame(messager, handle.getMessager());
        assertNull(handle.getPrompt());
        assertEquals("Build things", handle.getDesc());
    }

    private static TeamAgentBlueprint blueprint(String persona) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setPersona(persona);
        return new TeamAgentBlueprint(
                new AgentCard("agent", "Agent", "description"),
                new TeamAgentSpec(),
                ctx,
                "",
                "en"
        );
    }

    private static final class RecordingStore implements TeamMember.MemberStore {
        @Override
        public CompletionStage<TeamMember.MemberSnapshot> getMember(String memberName, String teamName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletionStage<Boolean> updateMemberExecutionStatus(
                String memberName,
                String teamName,
                String status
        ) {
            return CompletableFuture.completedFuture(false);
        }
    }

    private static final class RecordingMessager implements Messager {
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
