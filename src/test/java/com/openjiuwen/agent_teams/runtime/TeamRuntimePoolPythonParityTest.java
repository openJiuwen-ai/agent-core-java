/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.TeamAgent;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.runtime.test_pool} in
 * {@code tests/unit_tests/agent_teams/runtime/test_pool.py}.
 */
class TeamRuntimePoolPythonParityTest {

    @Test
    void testEmptyPoolHasNoTeams() {
        TeamRuntimePool pool = new TeamRuntimePool();

        assertThat(pool.listTeamNames()).isEmpty();
        assertThat(pool.hasActive("alpha")).isFalse();
        assertThat(pool.get("alpha")).isNull();
    }

    @Test
    void testAddGetAndRemoveRoundTrip() {
        TeamRuntimePool pool = new TeamRuntimePool();
        ActiveTeam team = makeTeam("alpha");
        pool.add(team);

        assertThat(pool.hasActive("alpha")).isTrue();
        assertThat(pool.get("alpha")).isSameAs(team);

        ActiveTeam removed = pool.remove("alpha");
        assertThat(removed).isSameAs(team);
        assertThat(pool.hasActive("alpha")).isFalse();
        assertThat(pool.remove("alpha")).isNull();
    }

    @Test
    void testAddReplacesExistingEntry() {
        TeamRuntimePool pool = new TeamRuntimePool();
        ActiveTeam first = makeTeam("alpha", "s-old");
        ActiveTeam second = makeTeam("alpha", "s-new");
        pool.add(first);
        pool.add(second);

        assertThat(pool.get("alpha")).isSameAs(second);
        assertThat(pool.listTeamNames()).containsExactly("alpha");
    }

    @Test
    void testMultiTeamInSameSessionListedTogether() {
        TeamRuntimePool pool = new TeamRuntimePool();
        pool.add(makeTeam("alpha", "shared"));
        pool.add(makeTeam("beta", "shared"));
        pool.add(makeTeam("gamma", "other"));

        List<String> names = pool.teamsForSession("shared").stream()
                .map(ActiveTeam::teamName)
                .sorted()
                .toList();
        assertThat(names).containsExactly("alpha", "beta");
        assertThat(pool.listTeamNames()).containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }

    @Test
    void testLifecycleStatePersistsAcrossGet() {
        TeamRuntimePool pool = new TeamRuntimePool();
        ActiveTeam team = makeTeam("alpha");
        team.setState(RuntimeState.PAUSED);
        pool.add(team);

        ActiveTeam fetched = pool.get("alpha");
        assertThat(fetched).isNotNull();
        assertThat(fetched.state()).isEqualTo(RuntimeState.PAUSED);
    }

    @Test
    void testListAllInfoReturnsReadonlySnapshots() {
        TeamRuntimePool pool = new TeamRuntimePool();
        ActiveTeam running = makeTeam("alpha", "s-running");
        ActiveTeam paused = makeTeam("beta", "s-paused");
        paused.setState(RuntimeState.PAUSED);
        pool.add(running);
        pool.add(paused);

        List<ActiveTeamInfo> snapshots = pool.listAllInfo();

        assertThat(snapshots).containsExactly(
                new ActiveTeamInfo("alpha", "s-running", RuntimeState.RUNNING, false),
                new ActiveTeamInfo("beta", "s-paused", RuntimeState.PAUSED, false)
        );
        assertThat(ActiveTeamInfo.class.isRecord()).isTrue();
        assertThat(Arrays.stream(ActiveTeamInfo.class.getMethods()).map(Method::getName))
                .doesNotContain("setState", "setTeamName", "setCurrentSessionId", "setGateClosed");
    }

    @Test
    void testConcurrentAddRemoveKeepsPoolConsistent() {
        TeamRuntimePool pool = new TeamRuntimePool();
        List<String> names = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> "team-" + index)
                .toList();
        List<ActiveTeam> teams = names.stream().map(TeamRuntimePoolPythonParityTest::makeTeam).toList();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            CompletableFuture<?>[] adds = teams.stream()
                    .map(team -> CompletableFuture.runAsync(() -> pool.add(team), executor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(adds).join();
            assertThat(pool.listTeamNames()).containsExactlyInAnyOrderElementsOf(names);

            CompletableFuture<?>[] removes = names.stream()
                    .map(name -> CompletableFuture.runAsync(() -> pool.remove(name), executor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(removes).join();
            assertThat(pool.listTeamNames()).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    private static ActiveTeam makeTeam(String teamName) {
        return makeTeam(teamName, "s1");
    }

    private static ActiveTeam makeTeam(String teamName, String sessionId) {
        return new ActiveTeam(teamName, agent(teamName), sessionId);
    }

    private static TeamAgent agent(String id) {
        return new TeamAgent(new AgentCard(id, "Agent " + id, "desc"));
    }
}
