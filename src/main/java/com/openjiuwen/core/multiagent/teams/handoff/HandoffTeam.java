/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.team_runtime.TeamRuntime;
import com.openjiuwen.core.multiagent.teams.TeamsUtils;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Event-driven handoff multi-agent team.
 *
 * <p>Mirrors Python's {@code HandoffTeam} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_team.py}.</p>
 */
public class HandoffTeam extends BaseTeam {

    private final Map<String, Supplier<? extends BaseAgent>> agentProviders = new ConcurrentHashMap<>();
    private final Map<String, HandoffOrchestrator> coordinatorRegistry = new ConcurrentHashMap<>();
    private final Object initLock = new Object();
    private volatile boolean internalAgentsReady;
    private CompletableFuture<Void> internalAgentsFuture;

    public HandoffTeam(TeamCard card) {
        this(card, null, null);
    }

    public HandoffTeam(TeamCard card, HandoffTeamConfig config) {
        this(card, config, null);
    }

    public HandoffTeam(TeamCard card, HandoffTeamConfig config, TeamRuntime runtime) {
        super(card, config == null ? new HandoffTeamConfig() : config, runtime);
    }

    @Override
    protected TeamConfig createDefaultConfig() {
        return new HandoffTeamConfig();
    }

    @Override
    public HandoffTeamConfig getConfig() {
        return (HandoffTeamConfig) super.getConfig();
    }

    @Override
    public HandoffTeam configure(TeamConfig config) {
        super.configure(config == null ? new HandoffTeamConfig() : config);
        markInternalAgentsDirty();
        return this;
    }

    public HandoffTeam configure(HandoffTeamConfig config) {
        return configure((TeamConfig) config);
    }

    @Override
    public HandoffTeam addAgent(AgentCard card, Function<AgentCard, ?> provider) {
        Objects.requireNonNull(card, "card must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        boolean alreadyRegistered = getRuntime().hasAgent(card.getId());
        super.addAgent(card, provider);
        if (!alreadyRegistered) {
            agentProviders.put(card.getId(), () -> asBaseAgent(provider.apply(card), card.getId()));
            markInternalAgentsDirty();
        }
        return this;
    }

    public HandoffTeam addAgent(AgentCard card, Supplier<? extends BaseAgent> provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        return addAgent(card, ignored -> provider.get());
    }

    @Override
    public HandoffTeam removeAgent(String agentId) {
        super.removeAgent(agentId);
        agentProviders.remove(agentId);
        markInternalAgentsDirty();
        return this;
    }

    @Override
    public HandoffTeam removeAgent(AgentCard agentCard) {
        return removeAgent(agentCard.getId());
    }

    HandoffOrchestrator lookupCoordinator(String sessionId) {
        return coordinatorRegistry.get(sessionId);
    }

    protected String getStartAgentId() {
        HandoffConfig config = getConfig().getHandoff();
        if (config != null && config.getStartAgent() != null) {
            return config.getStartAgent().getId();
        }
        List<AgentCard> agentCards = getCard().getAgentCards();
        if (agentCards.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_CONFIG_INVALID,
                    "reason",
                    "HandoffTeam requires at least one agent"
            );
        }
        return agentCards.get(0).getId();
    }

    protected CompletableFuture<Void> ensureInternalAgents() {
        if (internalAgentsReady) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (initLock) {
            if (internalAgentsReady) {
                return CompletableFuture.completedFuture(null);
            }
            if (internalAgentsFuture != null && !internalAgentsFuture.isDone()) {
                return internalAgentsFuture;
            }
            internalAgentsFuture = doEnsureInternalAgents()
                    .whenComplete((ignored, error) -> {
                        synchronized (initLock) {
                            if (error == null) {
                                internalAgentsReady = true;
                            } else {
                                internalAgentsFuture = null;
                            }
                        }
                    });
            return internalAgentsFuture;
        }
    }

    protected CompletionStage<Object> runChain(Object message, AgentTeamSession session) {
        Objects.requireNonNull(session, "session must not be null");
        String sessionId = session.getSessionId();
        return ensureInternalAgents().thenCompose(ignored -> {
            HandoffConfig config = getConfig().getHandoff();
            List<String> registeredAgents = getCard().getAgentCards().stream()
                    .map(AgentCard::getId)
                    .collect(Collectors.toList());
            HandoffOrchestrator coordinator = HandoffOrchestrator.restoreFromSession(
                    session,
                    getStartAgentId(),
                    registeredAgents,
                    config
            );
            List<Map<String, Object>> history = asHistoryList(session.getState(HandoffOrchestrator.HANDOFF_HISTORY_KEY));
            boolean resume = session.getState(HandoffOrchestrator.COORDINATOR_STATE_KEY) != null;
            if (resume) {
                history = history.stream()
                        .filter(item -> !isInterruptHistoryItem(item))
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            boolean boundByRunChain = getRuntime().getTeamSession(sessionId) == null;
            if (boundByRunChain) {
                getRuntime().bindTeamSession(session);
            }
            coordinatorRegistry.put(sessionId, coordinator);
            Loggers.MULTI_AGENT.info(
                    "[{}:{}] run_chain start session_id='{}' start_agent='{}' resume={} history_hops={}",
                    getClass().getSimpleName(),
                    getCard().getId(),
                    sessionId,
                    coordinator.getCurrentAgentId(),
                    resume,
                    history.size()
            );

            CompletableFuture<Object> resultFuture = applyTimeout(
                    coordinator.doneFuture(),
                    getConfig().getMessageTimeout()
            );
            CompletionStage<Void> publishStage = getRuntime().publish(
                    new HandoffRequest(message, history, session),
                    "container_" + coordinator.getCurrentAgentId(),
                    getCard().getId(),
                    sessionId
            );
            CompletableFuture<Object> publishFailure = publishFailureFuture(publishStage);
            return CompletableFuture.anyOf(resultFuture, publishFailure)
                    .thenApply(result -> result)
                    .handle((result, error) -> finishRunChain(
                            sessionId,
                            coordinator,
                            result,
                            error,
                            boundByRunChain
                    ));
        });
    }

    @Override
    public CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
        AgentTeamSession teamSession = asTeamSession(session);
        TeamsUtils.InvokeContext context = TeamsUtils.standaloneInvokeContext(
                getRuntime(),
                getCard(),
                message,
                teamSession
        );
        return runChain(message, context.session())
                .whenComplete((ignored, error) -> context.close());
    }

    public CompletionStage<Object> invoke(Object message, AgentTeamSession session) {
        return invoke(message, (AgentSessionApi) session);
    }

    @Override
    public Stream<Object> stream(Object message, AgentSessionApi session) {
        AgentTeamSession teamSession = asTeamSession(session);
        return TeamsUtils.standaloneStreamContext(
                getRuntime(),
                getCard(),
                message,
                (currentSession, ignored) -> runChain(message, currentSession).thenApply(result -> null),
                teamSession
        );
    }

    public Stream<Object> stream(Object message, AgentTeamSession session) {
        return stream(message, (AgentSessionApi) session);
    }

    private CompletableFuture<Void> doEnsureInternalAgents() {
        HandoffConfig config = getConfig().getHandoff();
        List<AgentCard> cards = getCard().getAgentCards();
        List<String> agentIds = cards.stream().map(AgentCard::getId).collect(Collectors.toList());
        Map<String, Set<String>> routeGraph = HandoffOrchestrator.buildRouteGraph(
                agentIds,
                config == null || config.getRoutes() == null ? List.of() : config.getRoutes()
        );
        Loggers.MULTI_AGENT.info(
                "[{}:{}] initializing internal agents agents={} routes={}",
                getClass().getSimpleName(),
                getCard().getId(),
                agentIds,
                config == null || config.getRoutes() == null || config.getRoutes().isEmpty()
                        ? "full-mesh"
                        : config.getRoutes()
        );

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String agentId : agentIds) {
            AgentCard targetCard = getRuntime().getAgentCard(agentId);
            if (targetCard == null) {
                return CompletableFuture.failedFuture(ErrorHelper.buildError(
                        StatusCode.AGENT_TEAM_AGENT_NOT_FOUND,
                        "error_msg",
                        "Agent '" + agentId + "' not found in team '" + getTeamId() + "'"
                ));
            }
            List<String> allowedTargets = new ArrayList<>(routeGraph.getOrDefault(agentId, Set.of()));
            String endpointId = "__handoff_ep_" + getCard().getId() + "_" + agentId;
            AgentCard endpointCard = new AgentCard(endpointId, endpointId, "");
            Supplier<? extends BaseAgent> targetProvider = agentProviders.get(agentId);
            if (targetProvider == null) {
                return CompletableFuture.failedFuture(ErrorHelper.buildError(
                        StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                        "error_msg",
                        "Provider for agent '" + agentId + "' not found in handoff team"
                ));
            }
            Supplier<ContainerAgent> containerProvider = makeContainerProvider(
                    targetCard,
                    targetProvider,
                    allowedTargets
            );
            getRuntime().registerAgent(endpointCard, containerProvider);
            chain = chain.thenCompose(ignored -> getRuntime().subscribe(endpointId, "container_" + agentId));
            Loggers.MULTI_AGENT.debug(
                    "[{}:{}] endpoint registered endpoint_id='{}' agent_id='{}' targets={} provider_type={}",
                    getClass().getSimpleName(),
                    getCard().getId(),
                    endpointId,
                    agentId,
                    allowedTargets,
                    targetProvider.getClass().getSimpleName()
            );
        }
        return chain.thenRun(() -> Loggers.MULTI_AGENT.info(
                "[{}:{}] internal agents ready count={}",
                getClass().getSimpleName(),
                getCard().getId(),
                agentIds.size()
        ));
    }

    private Supplier<ContainerAgent> makeContainerProvider(
            AgentCard card,
            Supplier<? extends BaseAgent> targetProvider,
            List<String> allowedTargets
    ) {
        return () -> new ContainerAgent(
                card,
                targetProvider,
                allowedTargets,
                this::lookupCoordinator
        );
    }

    private Object finishRunChain(
            String sessionId,
            HandoffOrchestrator coordinator,
            Object result,
            Throwable error,
            boolean boundByRunChain
    ) {
        try {
            if (error != null) {
                throw toRunChainException(error);
            }
            Loggers.MULTI_AGENT.info(
                    "[{}:{}] run_chain done session_id='{}' hops={}",
                    getClass().getSimpleName(),
                    getCard().getId(),
                    sessionId,
                    coordinator.getHandoffCount()
            );
            return result;
        } finally {
            coordinatorRegistry.remove(sessionId);
            if (boundByRunChain) {
                getRuntime().unbindTeamSession(sessionId);
            }
            getRuntime().cleanupSession(sessionId).join();
        }
    }

    private RuntimeException toRunChainException(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof TimeoutException) {
            double timeout = getConfig().getMessageTimeout();
            String errorMessage = "handoff chain timeout after " + timeout + "s, team='" + getCard().getId() + "'";
            Loggers.MULTI_AGENT.error("[{}:{}] {}", getClass().getSimpleName(), getCard().getId(), errorMessage);
            return ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    null,
                    null,
                    cause,
                    Map.of("error_msg", errorMessage)
            );
        }
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new CompletionException(cause);
    }

    private CompletableFuture<Object> applyTimeout(CompletableFuture<Object> resultFuture, double timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            return resultFuture;
        }
        long timeoutMillis = Math.max(1L, (long) (timeoutSeconds * 1000));
        return resultFuture.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private CompletableFuture<Object> publishFailureFuture(CompletionStage<Void> publishStage) {
        CompletableFuture<Object> failure = new CompletableFuture<>();
        publishStage.whenComplete((ignored, error) -> {
            if (error != null) {
                failure.completeExceptionally(unwrap(error));
            }
        });
        return failure;
    }

    private AgentTeamSession asTeamSession(AgentSessionApi session) {
        if (session == null) {
            return null;
        }
        if (session instanceof AgentTeamSession teamSession) {
            return teamSession;
        }
        return new AgentTeamSession(session.getSessionId(), null, getCard().getId());
    }

    private void markInternalAgentsDirty() {
        synchronized (initLock) {
            internalAgentsReady = false;
            internalAgentsFuture = null;
        }
    }

    private static BaseAgent asBaseAgent(Object value, String agentId) {
        if (value instanceof BaseAgent baseAgent) {
            return baseAgent;
        }
        throw ErrorHelper.buildError(
                StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                "error_msg",
                "Provider for agent '" + agentId + "' did not return a BaseAgent"
        );
    }

    private static List<Map<String, Object>> asHistoryList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> history = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> rawMap) {
                history.add(toStringObjectMap(rawMap));
            }
        }
        return history;
    }

    private static boolean isInterruptHistoryItem(Map<String, Object> item) {
        Object output = item.get("output");
        return output instanceof Map<?, ?> map && "interrupt".equals(Objects.toString(map.get("result_type"), ""));
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
