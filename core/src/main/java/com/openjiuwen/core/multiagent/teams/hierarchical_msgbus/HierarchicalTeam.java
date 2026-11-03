/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.team_runtime.TeamRuntime;
import com.openjiuwen.core.multiagent.teams.TeamsUtils;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterators;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Hierarchical multi-agent team driven by a supervisor agent over the message bus.
 *
 * <p>Mirrors Python's {@code HierarchicalTeam} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/hierarchical_team.py}.</p>
 */
public class HierarchicalTeam extends BaseTeam {

    private final HierarchicalTeamConfig hierarchicalConfig;
    private final String supervisorId;
    private Object supervisorInstance;

    public HierarchicalTeam(TeamCard card, HierarchicalTeamConfig config) {
        this(card, config, null);
    }

    public HierarchicalTeam(TeamCard card, HierarchicalTeamConfig config, TeamRuntime runtime) {
        super(card, requireConfig(config), runtime);
        this.hierarchicalConfig = (HierarchicalTeamConfig) getConfig();
        AgentCard supervisorAgent = this.hierarchicalConfig.getSupervisorAgent();
        this.supervisorId = supervisorAgent == null ? null : supervisorAgent.getId();
    }

    @Override
    public HierarchicalTeam addAgent(AgentCard agentCard, Function<AgentCard, ?> provider) {
        super.addAgent(agentCard, provider);
        if (Objects.equals(agentCard.getId(), supervisorId)) {
            Loggers.MULTI_AGENT.info("[{}] Registered supervisor '{}' in team '{}'",
                    getClass().getSimpleName(), agentCard.getId(), getTeamId());
            if (hierarchicalConfig.getTimeout() != null) {
                getRuntime().setP2pTimeout(hierarchicalConfig.getTimeout());
            }
        }
        return this;
    }

    @Override
    public CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
        return invoke(message, session, null);
    }

    public CompletionStage<Object> invoke(Object message, AgentSessionApi session, Double timeout) {
        assertReady();
        Double effectiveTimeout = timeout == null ? hierarchicalConfig.getTimeout() : timeout;
        if (session != null) {
            return getRuntime().send(message, supervisorId, getCard().getId(), session.getSessionId(),
                    effectiveTimeout);
        }

        TeamsUtils.InvokeContext context = TeamsUtils.standaloneInvokeContext(getRuntime(), getCard(), message);
        CompletionStage<Object> response;
        try {
            Loggers.MULTI_AGENT.debug("[{}] invoke start session_id={} supervisor={}",
                    getClass().getSimpleName(), context.sessionId(), supervisorId);
            response = getRuntime().send(message, supervisorId, getCard().getId(), context.sessionId(),
                    effectiveTimeout);
        } catch (RuntimeException exception) {
            context.close();
            throw exception;
        }
        return response.handle((value, error) -> {
            try {
                context.close();
            } catch (RuntimeException closeError) {
                if (error == null) {
                    throw closeError;
                }
                error.addSuppressed(closeError);
            }
            if (error != null) {
                throw asCompletionException(error);
            }
            Loggers.MULTI_AGENT.debug("[{}] invoke end session_id={}",
                    getClass().getSimpleName(), context.sessionId());
            return value;
        });
    }

    @Override
    public Stream<Object> stream(Object message, AgentSessionApi session) {
        return stream(message, session, null);
    }

    public Stream<Object> stream(Object message, AgentSessionApi session, Double timeout) {
        assertReady();
        Double effectiveTimeout = timeout == null ? hierarchicalConfig.getTimeout() : timeout;
        Loggers.MULTI_AGENT.debug("[{}] stream start supervisor={}", getClass().getSimpleName(), supervisorId);
        if (session != null) {
            runSupervisorToExternalSession(message, session, effectiveTimeout);
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(session.streamIterator(), 0), false);
        }
        return TeamsUtils.standaloneStreamContext(
                getRuntime(),
                getCard(),
                message,
                (teamSession, sessionId) -> runSupervisorToSession(message, teamSession, sessionId, effectiveTimeout)
        );
    }

    public String getSupervisorId() {
        return supervisorId;
    }

    public Object getSupervisorInstance() {
        return supervisorInstance;
    }

    public void setSupervisorInstance(Object supervisorInstance) {
        this.supervisorInstance = supervisorInstance;
    }

    void assertReady() {
        if (supervisorId == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    "error_msg",
                    "No supervisor configured in HierarchicalTeamConfig."
            );
        }
        if (!getRuntime().hasAgent(supervisorId)) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    "error_msg",
                    "Supervisor '" + supervisorId + "' is not registered in runtime. "
                            + "Call add_agent(supervisor_card, supervisor_provider) before invoke()/stream()."
            );
        }
    }

    private CompletionStage<Void> runSupervisorToSession(
            Object message,
            AgentTeamSession teamSession,
            String sessionId,
            Double timeout
    ) {
        return getRuntime().send(message, supervisorId, getCard().getId(), sessionId, timeout)
                .thenAccept(result -> {
                    if (result != null) {
                        writeFinalResult(teamSession, result);
                    }
                    Loggers.MULTI_AGENT.debug("[{}] stream end session_id={}",
                            getClass().getSimpleName(), sessionId);
                });
    }

    private void runSupervisorToExternalSession(Object message, AgentSessionApi session, Double timeout) {
        try {
            Object result = getRuntime().send(message, supervisorId, getCard().getId(), session.getSessionId(),
                    timeout).join();
            if (result != null) {
                writeFinalResult(session, result);
            }
        } finally {
            Loggers.MULTI_AGENT.debug("[{}] stream end session_id={}",
                    getClass().getSimpleName(), session.getSessionId());
        }
    }

    private void writeFinalResult(AgentTeamSession teamSession, Object result) {
        try {
            teamSession.writeStream(finalOutput(result));
        } catch (RuntimeException exception) {
            Loggers.MULTI_AGENT.warning("[{}] failed to write final result to stream: {}",
                    getClass().getSimpleName(), exception.getMessage());
        }
    }

    private void writeFinalResult(AgentSessionApi session, Object result) {
        try {
            session.writeStream(finalOutput(result));
        } catch (RuntimeException exception) {
            Loggers.MULTI_AGENT.warning("[{}] failed to write final result to stream: {}",
                    getClass().getSimpleName(), exception.getMessage());
        }
    }

    private Map<String, Object> finalOutput(Object result) {
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("output", result);
        return output;
    }

    private static HierarchicalTeamConfig requireConfig(HierarchicalTeamConfig config) {
        return Objects.requireNonNull(config, "config must not be null");
    }

    private static CompletionException asCompletionException(Throwable error) {
        if (error instanceof CompletionException completionException) {
            return completionException;
        }
        return new CompletionException(error);
    }
}
