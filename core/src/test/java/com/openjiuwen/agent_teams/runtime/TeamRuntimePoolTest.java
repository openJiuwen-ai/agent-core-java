/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.TeamAgent;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for the process-local team runtime pool.
 *
 * <p>Mirrors Python's {@code TeamRuntimePool} tests for
 * {@code openjiuwen/agent_teams/runtime/pool.py}.</p>
 */
class TeamRuntimePoolTest {

    @Test
    void activeTeamDefaultsToRunningAndOwnGate() {
        TeamAgent agent = agent("a");

        ActiveTeam entry = new ActiveTeam("team-a", agent, "session-a");

        assertThat(entry.teamName()).isEqualTo("team-a");
        assertThat(entry.agent()).isSameAs(agent);
        assertThat(entry.currentSessionId()).isEqualTo("session-a");
        assertThat(entry.state()).isEqualTo(RuntimeState.RUNNING);
        assertThat(entry.interactGate()).isNotNull();
        assertThat(entry.interactGate().isClosed()).isFalse();
    }

    @Test
    void addGetHasAndRemoveByTeamName() {
        TeamRuntimePool pool = new TeamRuntimePool();
        ActiveTeam first = new ActiveTeam("team-a", agent("a"), "session-a");
        ActiveTeam replacement = new ActiveTeam("team-a", agent("b"), "session-b", RuntimeState.PAUSED, null);

        pool.add(first);
        assertThat(pool.hasActive("team-a")).isTrue();
        assertThat(pool.get("team-a")).isSameAs(first);

        pool.add(replacement);
        assertThat(pool.get("team-a")).isSameAs(replacement);
        assertThat(pool.remove("team-a")).isSameAs(replacement);
        assertThat(pool.get("team-a")).isNull();
        assertThat(pool.hasActive("team-a")).isFalse();
    }

    @Test
    void listTeamNamesReturnsInsertionOrderSnapshot() {
        TeamRuntimePool pool = new TeamRuntimePool();
        pool.add(new ActiveTeam("team-b", agent("b"), "session-b"));
        pool.add(new ActiveTeam("team-a", agent("a"), "session-a"));

        List<String> names = pool.listTeamNames();
        names.add("external");

        assertThat(names).containsExactly("team-b", "team-a", "external");
        assertThat(pool.listTeamNames()).containsExactly("team-b", "team-a");
    }

    @Test
    void teamsForSessionReturnsMatchingActiveTeams() {
        TeamRuntimePool pool = new TeamRuntimePool();
        ActiveTeam first = new ActiveTeam("team-a", agent("a"), "session-a");
        ActiveTeam second = new ActiveTeam("team-b", agent("b"), "session-a");
        ActiveTeam other = new ActiveTeam("team-c", agent("c"), "session-c");
        pool.add(first);
        pool.add(second);
        pool.add(other);

        assertThat(pool.teamsForSession("session-a")).containsExactly(first, second);
        assertThat(pool.teamsForSession("missing")).isEmpty();
    }

    @Test
    void listAllInfoExcludesLiveAgentAndReportsGateClosed() {
        TeamRuntimePool pool = new TeamRuntimePool();
        ActiveTeam running = new ActiveTeam("team-a", agent("a"), "session-a");
        ActiveTeam paused = new ActiveTeam("team-b", agent("b"), "session-b", RuntimeState.PAUSED, new InteractGate());
        paused.interactGate().closeAndDrain();
        pool.add(running);
        pool.add(paused);

        assertThat(pool.listAllInfo()).containsExactly(
                new ActiveTeamInfo("team-a", "session-a", RuntimeState.RUNNING, false),
                new ActiveTeamInfo("team-b", "session-b", RuntimeState.PAUSED, true)
        );
    }

    private static TeamAgent agent(String id) {
        return new TeamAgent(new AgentCard(id, "Agent " + id, "desc"));
    }
}
