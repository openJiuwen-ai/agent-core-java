/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Tests pure team runtime dispatch decisions.
 *
 * <p>Mirrors Python's {@code decide_run_action} truth table in
 * {@code openjiuwen/agent_teams/runtime/dispatch.py}.</p>
 */
class TeamRunDispatcherTest {

    @Test
    void runActionKindValuesMatchPythonEnum() {
        assertThat(Arrays.stream(RunActionKind.values()).map(RunActionKind::getValue))
                .containsExactly(
                        "create",
                        "new_team_in_session",
                        "cold_recover",
                        "resume_from_pause",
                        "reject_running",
                        "reject_orphaned",
                        "reject_inconsistent"
                );
    }

    @Test
    void freshTeamRequiresSpecCreate() {
        RunAction action = decide(false, false, null, null);

        assertAction(action, RunActionKind.CREATE, true, null);
    }

    @Test
    void recreatableSessionBucketCreatesBeforeInconsistentPoolCheck() {
        RunAction pending = decide(
                false,
                true,
                new PoolEntry("s1", "running"),
                TeamRuntimeMetadata.TEAM_DB_STATE_PENDING_CREATE
        );
        RunAction cleaned = decide(
                false,
                true,
                new PoolEntry("s1", "running"),
                TeamRuntimeMetadata.TEAM_DB_STATE_CLEANED
        );

        assertAction(pending, RunActionKind.CREATE, true, null);
        assertAction(cleaned, RunActionKind.CREATE, true, null);
    }

    @Test
    void missingDbWithSessionBucketRejectsOrphaned() {
        RunAction action = decide(false, true, null, "created");

        assertAction(
                action,
                RunActionKind.REJECT_ORPHANED,
                false,
                "team 'team-a' not in DB but session bucket exists for 's1'"
        );
    }

    @Test
    void missingDbWithPoolEntryRejectsInconsistent() {
        RunAction action = decide(false, false, new PoolEntry("s1", "running"), null);

        assertAction(
                action,
                RunActionKind.REJECT_INCONSISTENT,
                false,
                "team 'team-a' present in pool but missing from DB"
        );
    }

    @Test
    void dbTeamWithoutPoolChoosesColdPaths() {
        RunAction newTeamInSession = decide(true, false, null, null);
        RunAction coldRecover = decide(true, true, null, null);

        assertAction(newTeamInSession, RunActionKind.NEW_TEAM_IN_SESSION, false, null);
        assertAction(coldRecover, RunActionKind.COLD_RECOVER, false, null);
    }

    @Test
    void sameSessionPausedPoolEntryResumes() {
        RunAction action = decide(true, true, new PoolEntry("s1", "paused"), null);

        assertAction(action, RunActionKind.RESUME_FROM_PAUSE, false, null);
    }

    @Test
    void sameSessionRunningPoolEntryRejectsRunning() {
        RunAction action = decide(true, true, new PoolEntry("s1", "running"), null);

        assertAction(
                action,
                RunActionKind.REJECT_RUNNING,
                false,
                "team 'team-a' already running on session 's1'; use interact"
        );
    }

    @Test
    void crossSessionPoolEntryViolatesManagerPreDispatchContract() {
        assertThatThrownBy(() -> TeamRunDispatcher.decideRunAction(
                true,
                true,
                new PoolEntry("old-session", "running"),
                "s1",
                "team-a",
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("dispatch invariant violated: pool entry for 'team-a' on session "
                        + "'old-session' must be torn down before dispatching to session 's1'");
    }

    private static RunAction decide(
            boolean teamInDb,
            boolean teamInSession,
            TeamRunDispatcher.PoolEntryView poolEntry,
            String teamDbState
    ) {
        return TeamRunDispatcher.decideRunAction(
                teamInDb,
                teamInSession,
                poolEntry,
                "s1",
                "team-a",
                teamDbState
        );
    }

    private static void assertAction(
            RunAction action,
            RunActionKind kind,
            boolean requireSpec,
            String reason
    ) {
        assertThat(action.kind()).isEqualTo(kind);
        assertThat(action.requireSpec()).isEqualTo(requireSpec);
        assertThat(action.reason()).isEqualTo(reason);
    }

    private record PoolEntry(String currentSessionId, String state) implements TeamRunDispatcher.PoolEntryView {
    }
}
