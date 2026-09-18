/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import java.util.Objects;

/**
 * Pure dispatch decision logic for team runtime activation.
 *
 * <p>Mirrors Python's {@code decide_run_action} in
 * {@code openjiuwen/agent_teams/runtime/dispatch.py}.</p>
 */
public final class TeamRunDispatcher {

    public static final String RUNTIME_STATE_PAUSED = "paused";

    private TeamRunDispatcher() {
    }

    public static RunAction decideRunAction(
            boolean teamInDb,
            boolean teamInSession,
            PoolEntryView poolEntry,
            String targetSessionId,
            String targetTeamName,
            String teamDbState
    ) {
        if (!teamInDb && teamInSession) {
            if (TeamRuntimeMetadata.TEAM_DB_STATE_PENDING_CREATE.equals(teamDbState)
                    || TeamRuntimeMetadata.TEAM_DB_STATE_CLEANED.equals(teamDbState)) {
                return new RunAction(RunActionKind.CREATE, true);
            }
            return new RunAction(
                    RunActionKind.REJECT_ORPHANED,
                    false,
                    "team '" + targetTeamName + "' not in DB but session bucket exists for '"
                            + targetSessionId + "'"
            );
        }

        if (!teamInDb && poolEntry != null) {
            return new RunAction(
                    RunActionKind.REJECT_INCONSISTENT,
                    false,
                    "team '" + targetTeamName + "' present in pool but missing from DB"
            );
        }

        if (!teamInDb) {
            return new RunAction(RunActionKind.CREATE, true);
        }

        if (poolEntry == null) {
            if (teamInSession) {
                return new RunAction(RunActionKind.COLD_RECOVER, false);
            }
            return new RunAction(RunActionKind.NEW_TEAM_IN_SESSION, false);
        }

        if (!Objects.equals(poolEntry.currentSessionId(), targetSessionId)) {
            throw new IllegalStateException(
                    "dispatch invariant violated: pool entry for '" + targetTeamName
                            + "' on session '" + poolEntry.currentSessionId()
                            + "' must be torn down before dispatching to session '"
                            + targetSessionId + "'"
            );
        }

        if (RUNTIME_STATE_PAUSED.equals(poolEntry.state())) {
            return new RunAction(RunActionKind.RESUME_FROM_PAUSE, false);
        }
        return new RunAction(
                RunActionKind.REJECT_RUNNING,
                false,
                "team '" + targetTeamName + "' already running on session '"
                        + targetSessionId + "'; use interact"
        );
    }

    public static RunAction decideRunAction(
            boolean teamInDb,
            boolean teamInSession,
            PoolEntryView poolEntry,
            String targetSessionId,
            String targetTeamName
    ) {
        return decideRunAction(teamInDb, teamInSession, poolEntry, targetSessionId, targetTeamName, null);
    }

    /**
     * Minimal pool-entry view required by dispatch.py.
     *
     * <p>Mirrors Python's {@code ActiveTeam.current_session_id/state} access in
     * {@code openjiuwen/agent_teams/runtime/dispatch.py}.</p>
     */
    public interface PoolEntryView {
        String currentSessionId();

        String state();
    }
}
