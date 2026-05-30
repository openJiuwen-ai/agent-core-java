/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Internal runtime owner for TeamAgent sessions.
 * <p>
 * Owns the in-process active TeamAgent runtime and manages session activation,
 * checkpoint resolution, and runtime lifecycle.
 * <p>
 * Mirrors Python's {@code TeamRuntimeManager} in
 * {@code openjiuwen.agent_teams.runtime_manager}.
 */
public class RuntimeManager {

    private static final Logger logger = Logger.getLogger(RuntimeManager.class.getName());

    private String activeTeamName;
    private String activeSessionId;
    private Object activeAgent;  // TeamAgent
    private boolean activePaused;

    /**
     * Create a new RuntimeManager.
     */
    public RuntimeManager() {
        this.activeTeamName = null;
        this.activeSessionId = null;
        this.activeAgent = null;
        this.activePaused = false;
    }

    public Optional<String> getActiveTeamName() {
        return Optional.ofNullable(activeTeamName);
    }

    public Optional<String> getActiveSessionId() {
        return Optional.ofNullable(activeSessionId);
    }

    public Optional<Object> getActiveAgent() {
        return Optional.ofNullable(activeAgent);
    }

    /**
     * Resolve the TeamAgent to run for the target team/session.
     *
     * @param spec   TeamAgentSpec
     * @param session Session ID or AgentTeamSession
     * @param inputs  Optional inputs for session initialization
     * @return TeamRuntimeActivation with resolved agent and metadata
     */
    public CompletableFuture<TeamRuntimeActivation> activate(
            Object spec,
            Object session,
            Object inputs) {
        
        // Build session from spec and session parameter
        Object teamSession = buildSession(spec, session);
        String sessionId = getSessionId(teamSession);
        String teamName = getTeamName(spec);

        // Handle existing active agent case
        if (activeAgent != null && activeTeamName != null && activeTeamName.equals(teamName)) {
            if (activeSessionId != null && activeSessionId.equals(sessionId)) {
                if (activePaused) {
                    // Resume paused session
                    preRun(teamSession, inputs);
                    activePaused = false;
                    return CompletableFuture.completedFuture(
                        new TeamRuntimeActivation(activeAgent, teamSession, "resume_paused")
                    );
                }
                logger.warning("run_agent_team_streaming called with active team/session " +
                    "(" + teamName + ", " + sessionId + "); prefer interact_agent_team for same-session follow-up");
                return CompletableFuture.completedFuture(
                    new TeamRuntimeActivation(activeAgent, teamSession, "same_session")
                );
            }

            // Resolve session checkpoint
            TeamSessionResolution resolution = resolveSessionCheckpoint(teamSession, teamName);
            if ("recoverable".equals(resolution.kind)) {
                recoverForExistingSession(activeAgent, teamSession);
                setActive(teamName, sessionId, activeAgent);
                return CompletableFuture.completedFuture(
                    new TeamRuntimeActivation(activeAgent, teamSession, "recover")
                );
            }
            if ("invalid".equals(resolution.kind)) {
                logger.warning("Refusing to resume team " + teamName + 
                    " on existing invalid session " + sessionId + ": " + 
                    (resolution.reason != null ? resolution.reason : "unknown checkpoint mismatch"));
                return CompletableFuture.completedFuture(
                    new TeamRuntimeActivation(activeAgent, teamSession, "invalid_session")
                );
            }

            preRun(teamSession, inputs);
            resumePersistentTeam(activeAgent, teamSession);
            setActive(teamName, sessionId, activeAgent);
            return CompletableFuture.completedFuture(
                new TeamRuntimeActivation(activeAgent, teamSession, "resume")
            );
        }

        // Deactivate existing agent if different
        if (activeAgent != null) {
            deactivateActiveRuntime();
        }

        // Resolve session checkpoint for new agent
        TeamSessionResolution resolution = resolveSessionCheckpoint(teamSession, teamName);
        if ("recoverable".equals(resolution.kind)) {
            Object recoveredAgent = recoverForNewSession(spec, teamSession);
            setActive(teamName, sessionId, recoveredAgent);
            return CompletableFuture.completedFuture(
                new TeamRuntimeActivation(recoveredAgent, teamSession, "recover")
            );
        }
        if ("fresh".equals(resolution.kind)) {
            preRun(teamSession, inputs);
            Object freshAgent = createFreshAgent(spec);
            setActive(teamName, sessionId, freshAgent);
            return CompletableFuture.completedFuture(
                new TeamRuntimeActivation(freshAgent, teamSession, "create")
            );
        }
        if ("invalid".equals(resolution.kind)) {
            logger.warning("Refusing to create team " + teamName +
                " on existing invalid session " + sessionId + ": " +
                (resolution.reason != null ? resolution.reason : "unknown checkpoint mismatch"));
            return CompletableFuture.completedFuture(
                new TeamRuntimeActivation(activeAgent, teamSession, "invalid_session")
            );
        }

        // Default: create fresh agent
        preRun(teamSession, inputs);
        Object newAgent = createFreshAgent(spec);
        setActive(teamName, sessionId, newAgent);
        return CompletableFuture.completedFuture(
            new TeamRuntimeActivation(newAgent, teamSession, "create")
        );
    }

    /**
     * Pause the active session.
     */
    public void pause() {
        activePaused = true;
    }

    /**
     * Check if the active session is paused.
     */
    public boolean isPaused() {
        return activePaused;
    }

    /**
     * Deactivate the active runtime.
     */
    public CompletableFuture<Void> deactivate() {
        return deactivateActiveRuntime();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void setActive(String teamName, String sessionId, Object agent) {
        this.activeTeamName = teamName;
        this.activeSessionId = sessionId;
        this.activeAgent = agent;
        this.activePaused = false;
    }

    private CompletableFuture<Void> deactivateActiveRuntime() {
        if (activeAgent == null) {
            return CompletableFuture.completedFuture(null);
        }
        logger.info("Deactivating runtime for team " + activeTeamName);
        if (activeAgent instanceof TeamAgent agent) {
            agent.stopCoordination();
        } else {
            invokeNoArg(activeAgent, "stopCoordination");
        }
        activeAgent = null;
        activeTeamName = null;
        activeSessionId = null;
        activePaused = false;
        return CompletableFuture.completedFuture(null);
    }

    private Object buildSession(Object spec, Object session) {
        if (session instanceof Session) {
            return session;
        }
        String teamName = getTeamName(spec);
        Map<String, Object> envs = new LinkedHashMap<>();
        envs.put("team_id", teamName);
        envs.put("team_name", teamName);
        if (session instanceof String sessionId && !sessionId.isBlank()) {
            return AgentSessionApi.create(sessionId, envs, null);
        }
        return AgentSessionApi.create(null, envs, null);
    }

    private String getSessionId(Object session) {
        if (session instanceof Session typedSession) {
            return typedSession.getSessionId();
        }
        Object value = invokeNoArg(session, "getSessionId");
        return value != null ? String.valueOf(value) : "";
    }

    private String getTeamName(Object spec) {
        if (spec instanceof TeamAgentSpec teamSpec) {
            return teamSpec.getTeamName();
        }
        Object value = invokeNoArg(spec, "getTeamName");
        return value != null ? String.valueOf(value) : "unknown";
    }

    private TeamSessionResolution resolveSessionCheckpoint(Object session, String teamName) {
        if (!(session instanceof Session typedSession)) {
            return new TeamSessionResolution("fresh", null);
        }
        String sessionId = typedSession.getSessionId();
        if (!CheckpointerFactory.getCheckpointer().sessionExists(sessionId)) {
            preRun(typedSession, null);
            return new TeamSessionResolution("fresh", null);
        }

        preRun(typedSession, null);
        Object checkpointTeamName = typedSession.getState("team_name");
        if (checkpointTeamName == null) {
            return new TeamSessionResolution("invalid", "checkpoint has no persisted team_name");
        }
        if (!teamName.equals(String.valueOf(checkpointTeamName))) {
            return new TeamSessionResolution("invalid", "checkpoint team_name='" + checkpointTeamName + "'");
        }
        return new TeamSessionResolution("recoverable", null);
    }

    private void preRun(Object session, Object inputs) {
        Object normalizedInputs = inputs instanceof Map<?, ?> ? inputs : null;
        if (session instanceof AgentSessionApi api) {
            api.preRun(normalizedInputs);
            return;
        }
        invoke(session, "preRun", normalizedInputs);
    }

    private void recoverForExistingSession(Object agent, Object session) {
        if (agent instanceof TeamAgent teamAgent && session instanceof Session typedSession) {
            teamAgent.recoverForExistingSession(typedSession);
            return;
        }
        invoke(agent, "recoverForExistingSession", session);
    }

    private void resumePersistentTeam(Object agent, Object session) {
        if (agent instanceof TeamAgent teamAgent && session instanceof Session typedSession) {
            teamAgent.resumeForNewSession(typedSession);
            return;
        }
        invoke(agent, "resumeForNewSession", session);
    }

    private Object recoverForNewSession(Object spec, Object session) {
        Object agent = createFreshAgent(spec);
        recoverForExistingSession(agent, session);
        return agent;
    }

    private Object createFreshAgent(Object spec) {
        if (spec instanceof TeamAgentSpec teamSpec) {
            return teamSpec.build();
        }
        return invokeNoArg(spec, "build");
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to call " + methodName + " on " + target.getClass().getName(), e);
        }
    }

    private static Object invoke(Object target, String methodName, Object argument) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            try {
                return method.invoke(target, argument);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to call " + methodName + " on " + target.getClass().getName(), e);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Inner classes
    // ------------------------------------------------------------------

    /**
     * Resolved team runtime and activation metadata.
     */
    public static class TeamRuntimeActivation {
        private final Object agent;
        private final Object session;
        private final String activationKind;

        public TeamRuntimeActivation(Object agent, Object session, String activationKind) {
            this.agent = agent;
            this.session = session;
            this.activationKind = activationKind;
        }

        public Object getAgent() { return agent; }
        public Object getSession() { return session; }
        public String getActivationKind() { return activationKind; }
    }

    /**
     * Checkpoint resolution for a target team session.
     */
    public static class TeamSessionResolution {
        private final String kind;
        private final String reason;

        public TeamSessionResolution(String kind, String reason) {
            this.kind = kind;
            this.reason = reason;
        }

        public String getKind() { return kind; }
        public String getReason() { return reason; }
    }
}
