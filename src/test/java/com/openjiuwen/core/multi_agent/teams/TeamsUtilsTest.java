/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams;

import com.openjiuwen.core.multi_agent.schema.TeamCard;
import com.openjiuwen.core.multi_agent.team_runtime.RuntimeConfig;
import com.openjiuwen.core.multi_agent.team_runtime.TeamRuntime;
import com.openjiuwen.core.session.AgentTeamSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for standalone team session lifecycle helpers.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/core/multi_agent/teams/utils.py}.</p>
 */
class TeamsUtilsTest {

    @Test
    void makeTeamSessionReusesConversationIdFromMessage() {
        TeamCard card = new TeamCard("team-a", "Team A", "");

        AgentTeamSession session = TeamsUtils.makeTeamSession(
                card,
                Map.of("conversation_id", "conversation-1", "input", "hello")
        );

        assertThat(session.getSessionId()).isEqualTo("conversation-1");
        assertThat(session.getTeamId()).isEqualTo("team-a");
    }

    @Test
    void makeTeamSessionFallsBackToUuidWhenNoConversationId() {
        AgentTeamSession session = TeamsUtils.makeTeamSession(new TeamCard("team-a", "Team A", ""), "hello");

        assertThat(session.getSessionId()).isNotBlank();
        assertThat(session.getTeamId()).isEqualTo("team-a");
    }

    @Test
    void standaloneInvokeContextOwnsLifecycleWhenSessionIsMissing() {
        TeamRuntime runtime = new TeamRuntime(new RuntimeConfig());
        TeamCard card = new TeamCard("team-a", "Team A", "");

        String sessionId = TeamsUtils.withStandaloneInvokeContext(
                runtime,
                card,
                Map.of("conversation_id", "invoke-1"),
                null,
                context -> {
                    assertThat(context.sessionId()).isEqualTo("invoke-1");
                    assertThat(runtime.getTeamSession("invoke-1")).isSameAs(context.session());
                    return context.sessionId();
                }
        );

        assertThat(sessionId).isEqualTo("invoke-1");
        assertThat(runtime.getTeamSession("invoke-1")).isNull();
    }

    @Test
    void standaloneInvokeContextLeavesCallerOwnedSessionAlone() {
        TeamRuntime runtime = new TeamRuntime(new RuntimeConfig());
        AgentTeamSession existing = new AgentTeamSession("runner-1", null, "team-a");

        String sessionId = TeamsUtils.withStandaloneInvokeContext(
                runtime,
                new TeamCard("team-a", "Team A", ""),
                "message",
                existing,
                context -> {
                    assertThat(context.session()).isSameAs(existing);
                    assertThat(runtime.getTeamSession("runner-1")).isNull();
                    return context.sessionId();
                }
        );

        assertThat(sessionId).isEqualTo("runner-1");
        assertThat(runtime.getTeamSession("runner-1")).isNull();
    }

    @Test
    void standaloneStreamContextYieldsChunksAndCleansOwnedSession() {
        TeamRuntime runtime = new TeamRuntime(new RuntimeConfig());
        TeamCard card = new TeamCard("team-a", "Team A", "");

        List<Object> chunks = TeamsUtils.standaloneStreamContext(
                runtime,
                card,
                Map.of("conversation_id", "stream-1"),
                (session, sessionId) -> {
                    assertThat(runtime.getTeamSession(sessionId)).isSameAs(session);
                    session.writeStream("chunk");
                    return CompletableFuture.completedFuture(null);
                }
        ).toList();

        assertThat(chunks).hasSize(1);
        assertThat(runtime.getTeamSession("stream-1")).isNull();
    }

    @Test
    void standaloneStreamContextPropagatesBackgroundException() {
        TeamRuntime runtime = new TeamRuntime(new RuntimeConfig());

        assertThatThrownBy(() -> TeamsUtils.standaloneStreamContext(
                runtime,
                new TeamCard("team-a", "Team A", ""),
                Map.of("conversation_id", "stream-error"),
                (session, sessionId) -> {
                    throw new IllegalStateException("boom");
                }
        ).toList()).isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(runtime.getTeamSession("stream-error")).isNull();
    }
}
