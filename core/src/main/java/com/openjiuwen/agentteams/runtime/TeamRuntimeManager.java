/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.runtime;

import com.openjiuwen.agentteams.LeaderTeammateAgentTeam;
import com.openjiuwen.agentteams.agent.CoordinationManager;
import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-process agent-team runtimes across streaming rounds.
 *
 * <p>A persistent team remains registered after a round and can be resumed by
 * name on the same session. A temporary team is stopped and removed when its
 * stream finishes.</p>
 *
 * @since 0.1.13
 */
public final class TeamRuntimeManager {
    private final Map<String, RuntimeEntry> entries = new ConcurrentHashMap<>();
    private final Object runtimeLock = new Object();

    /**
     * Activate a team runtime for one streaming round.
     *
     * @param teamReference team spec, facade, runtime agent, or registered team name
     * @param sessionId target session id
     * @return activation containing the resolved runtime and dispatch action
     * @since 0.1.13
     */
    public Activation activate(Object teamReference, String sessionId) {
        synchronized (runtimeLock) {
            return activateLocked(teamReference, sessionId);
        }
    }

    private Activation activateLocked(Object teamReference, String sessionId) {
        String resolvedSessionId = requireSessionId(sessionId);
        String teamName = resolveTeamName(teamReference);
        RuntimeEntry current = entries.get(teamName);
        if (current != null) {
            return reactivate(current, teamReference, resolvedSessionId);
        }
        TeamAgent agent = createAgent(teamReference);
        agent.setSessionId(resolvedSessionId);
        RuntimeEntry created = new RuntimeEntry(agent, resolvedSessionId, RuntimeState.ACTIVE);
        entries.put(teamName, created);
        logActivation(teamName, resolvedSessionId, ActivationAction.CREATE);
        return new Activation(agent, teamName, resolvedSessionId, ActivationAction.CREATE);
    }

    /**
     * Finalize a completed streaming round.
     *
     * @param activation activation returned by {@link #activate(Object, String)}
     * @since 0.1.13
     */
    public void finalizeRound(Activation activation) {
        synchronized (runtimeLock) {
            finalizeRoundLocked(activation);
        }
    }

    private void finalizeRoundLocked(Activation activation) {
        RuntimeEntry current = entries.get(activation.teamName());
        if (!isMatching(current, activation)) {
            return;
        }
        if (isPersistent(current.getAgent())) {
            pauseRuntime(current.getAgent(), activation.teamName(), activation.sessionId());
            current.setState(RuntimeState.PAUSED);
            Loggers.AGENT.info("finalize: pausing persistent team {} session {}",
                    activation.teamName(), activation.sessionId());
            return;
        }
        stopRuntime(current.getAgent(), activation.teamName(), activation.sessionId());
        entries.remove(activation.teamName(), current);
        Loggers.AGENT.info("finalize: stopping temporary team {} session {}",
                activation.teamName(), activation.sessionId());
    }

    /**
     * Destroy a registered team and remove its runtime entry.
     *
     * @param teamName registered team name
     * @param isForceEnabled whether other members should be force-shut down
     * @return {@code true} when a registered team was cleaned successfully
     * @since 0.1.13
     */
    public boolean destroyTeam(String teamName, boolean isForceEnabled) {
        synchronized (runtimeLock) {
            return destroyTeamLocked(teamName, isForceEnabled);
        }
    }

    private boolean destroyTeamLocked(String teamName, boolean isForceEnabled) {
        RuntimeEntry entry = entries.get(teamName);
        if (entry == null) {
            return false;
        }
        boolean isDestroyed = entry.getAgent().destroyTeam(isForceEnabled);
        if (isDestroyed) {
            entries.remove(teamName, entry);
        }
        return isDestroyed;
    }

    /**
     * Stop and unregister every active team runtime.
     *
     * @since 0.1.13
     */
    public void stopAll() {
        synchronized (runtimeLock) {
            stopAllLocked();
        }
    }

    private void stopAllLocked() {
        List<Map.Entry<String, RuntimeEntry>> snapshot = new ArrayList<>(entries.entrySet());
        entries.clear();
        for (Map.Entry<String, RuntimeEntry> registeredEntry : snapshot) {
            RuntimeEntry runtimeEntry = registeredEntry.getValue();
            stopRuntime(runtimeEntry.getAgent(), registeredEntry.getKey(), runtimeEntry.getSessionId());
        }
    }

    /**
     * Return the state of a registered team.
     *
     * @param teamName team name
     * @return runtime state, or an empty optional when no runtime is registered
     * @since 0.1.13
     */
    public Optional<RuntimeState> getState(String teamName) {
        RuntimeEntry entry = entries.get(teamName);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(entry.getState());
    }

    private Activation reactivate(RuntimeEntry current, Object teamReference, String sessionId) {
        String teamName = resolveTeamName(teamReference);
        if (current.getState() == RuntimeState.ACTIVE) {
            throw new IllegalStateException("Agent team is already running: " + teamName);
        }
        if (!current.getSessionId().equals(sessionId)) {
            return switchSession(current, teamReference, teamName, sessionId);
        }
        current.setState(RuntimeState.ACTIVE);
        logActivation(teamName, sessionId, ActivationAction.RESUME_FROM_PAUSE);
        return new Activation(current.getAgent(), teamName, sessionId, ActivationAction.RESUME_FROM_PAUSE);
    }

    private Activation switchSession(
            RuntimeEntry current,
            Object teamReference,
            String teamName,
            String sessionId) {
        if (teamReference instanceof String) {
            throw new IllegalStateException(
                    "A team spec or instance is required when switching sessions: " + teamName);
        }
        stopRuntime(current.getAgent(), teamName, current.getSessionId());
        TeamAgent replacement = createReplacementAgent(teamReference);
        replacement.setSessionId(sessionId);
        entries.put(teamName, new RuntimeEntry(replacement, sessionId, RuntimeState.ACTIVE));
        logActivation(teamName, sessionId, ActivationAction.REBUILD_FOR_SESSION);
        return new Activation(replacement, teamName, sessionId, ActivationAction.REBUILD_FOR_SESSION);
    }

    private static boolean isMatching(RuntimeEntry current, Activation activation) {
        return current != null
                && current.getAgent() == activation.agent()
                && current.getSessionId().equals(activation.sessionId());
    }

    private static boolean isPersistent(TeamAgent agent) {
        return "persistent".equalsIgnoreCase(agent.getSpec().getLifecycle());
    }

    private static void pauseRuntime(TeamAgent agent, String teamName, String sessionId) {
        CoordinationManager coordinationManager = agent.getCoordinationManager();
        if (coordinationManager == null) {
            Loggers.AGENT.warn("Cannot pause unconfigured team {} session {}", teamName, sessionId);
            return;
        }
        try {
            coordinationManager.pause();
        } catch (BaseError | CompletionException | IllegalStateException exception) {
            Loggers.AGENT.warn("Failed to pause team {} session {}: {}",
                    teamName, sessionId, exception.getMessage());
        }
    }

    private static void stopRuntime(TeamAgent agent, String teamName, String sessionId) {
        CoordinationManager coordinationManager = agent.getCoordinationManager();
        if (coordinationManager == null) {
            Loggers.AGENT.warn("Cannot stop unconfigured team {} session {}", teamName, sessionId);
            return;
        }
        try {
            coordinationManager.stop();
        } catch (BaseError | CompletionException | IllegalStateException exception) {
            Loggers.AGENT.warn("Failed to stop team {} session {}: {}",
                    teamName, sessionId, exception.getMessage());
        }
    }

    private static TeamAgent createAgent(Object teamReference) {
        if (teamReference instanceof TeamAgent agent) {
            return agent;
        }
        if (teamReference instanceof TeamAgentSpec spec) {
            return TeamFactory.createAgentTeam(spec);
        }
        if (teamReference instanceof LeaderTeammateAgentTeam team) {
            return team.agent();
        }
        if (teamReference instanceof String teamName) {
            throw new IllegalArgumentException("Agent team is not registered: " + teamName);
        }
        throw new IllegalArgumentException(
                "agentTeam must be a team name, TeamAgentSpec, LeaderTeammateAgentTeam, or TeamAgent");
    }

    private static TeamAgent createReplacementAgent(Object teamReference) {
        if (teamReference instanceof LeaderTeammateAgentTeam team) {
            return TeamFactory.createAgentTeam(team.spec());
        }
        if (teamReference instanceof TeamAgent) {
            throw new IllegalStateException(
                    "A TeamAgentSpec or LeaderTeammateAgentTeam is required when switching sessions");
        }
        return createAgent(teamReference);
    }

    private static String resolveTeamName(Object teamReference) {
        String teamName;
        if (teamReference instanceof String value) {
            teamName = value;
        } else if (teamReference instanceof TeamAgentSpec spec) {
            teamName = spec.getName();
        } else if (teamReference instanceof LeaderTeammateAgentTeam team) {
            teamName = team.teamName();
        } else if (teamReference instanceof TeamAgent agent) {
            teamName = agent.getSpec() == null ? null : agent.getSpec().getName();
        } else {
            throw new IllegalArgumentException(
                    "agentTeam must be a team name, TeamAgentSpec, LeaderTeammateAgentTeam, or TeamAgent");
        }
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException("Agent team name cannot be blank");
        }
        return teamName;
    }

    private static String requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Agent team session id cannot be blank");
        }
        return sessionId;
    }

    private static void logActivation(String teamName, String sessionId, ActivationAction action) {
        Loggers.AGENT.info("activate: team {} session {} dispatched to {}",
                teamName, sessionId, action.getValue());
    }

    /**
     * Runtime activation returned to the Runner.
     *
     * @param agent resolved team agent
     * @param teamName team name
     * @param sessionId session id
     * @param action activation action
     * @since 0.1.13
     */
    public record Activation(
            TeamAgent agent,
            String teamName,
            String sessionId,
            ActivationAction action) {
    }

    /**
     * Team runtime activation action.
     *
     * @since 0.1.13
     */
    public enum ActivationAction {
        CREATE("create"),
        RESUME_FROM_PAUSE("resume_from_pause"),
        REBUILD_FOR_SESSION("rebuild_for_session");

        private final String value;

        ActivationAction(String value) {
            this.value = value;
        }

        /**
         * Return the log-facing action value.
         *
         * @return action value
         * @since 0.1.13
         */
        public String getValue() {
            return value;
        }
    }

    /**
     * State of a registered runtime.
     *
     * @since 0.1.13
     */
    public enum RuntimeState {
        ACTIVE,
        PAUSED
    }

    private static final class RuntimeEntry {
        private final TeamAgent agent;
        private final String sessionId;
        private volatile RuntimeState state;

        private RuntimeEntry(TeamAgent agent, String sessionId, RuntimeState state) {
            this.agent = agent;
            this.sessionId = sessionId;
            this.state = state;
        }

        private TeamAgent getAgent() {
            return agent;
        }

        private String getSessionId() {
            return sessionId;
        }

        private RuntimeState getState() {
            return state;
        }

        private void setState(RuntimeState value) {
            this.state = value;
        }
    }
}
