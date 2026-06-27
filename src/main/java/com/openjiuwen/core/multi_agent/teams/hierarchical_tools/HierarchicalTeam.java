/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.hierarchical_tools;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multi_agent.BaseTeam;
import com.openjiuwen.core.multi_agent.schema.TeamCard;
import com.openjiuwen.core.multi_agent.team_runtime.TeamRuntime;
import com.openjiuwen.core.multi_agent.teams.TeamsUtils;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Agents-as-tools hierarchical multi-agent team.
 *
 * <p>Mirrors Python's {@code HierarchicalTeam} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_tools/hierarchical_team.py}.</p>
 */
public class HierarchicalTeam extends BaseTeam {

    private final HierarchicalTeamConfig hierarchicalConfig;
    private final String rootAgentId;
    private final Map<String, List<AgentCard>> pendingChildren = new LinkedHashMap<>();

    public HierarchicalTeam(TeamCard card, HierarchicalTeamConfig config) {
        this(card, config, null);
    }

    public HierarchicalTeam(TeamCard card, HierarchicalTeamConfig config, TeamRuntime runtime) {
        super(card, requireConfig(config), runtime);
        this.hierarchicalConfig = (HierarchicalTeamConfig) getConfig();
        AgentCard rootAgent = Objects.requireNonNull(
                this.hierarchicalConfig.getRootAgent(),
                "config.rootAgent must not be null"
        );
        this.rootAgentId = rootAgent.getId();
        Loggers.MULTI_AGENT.debug("[{}] Initialized with team_id: {}, root_agent_id: {}",
                getClass().getSimpleName(), getCard().getId(), rootAgentId);
    }

    @Override
    public HierarchicalTeam addAgent(AgentCard agentCard, Function<AgentCard, ?> provider) {
        return addAgent(agentCard, provider, null);
    }

    public HierarchicalTeam addAgent(AgentCard agentCard, Function<AgentCard, ?> provider, String parentAgentId) {
        super.addAgent(agentCard, provider);
        if (parentAgentId != null && !parentAgentId.isEmpty()) {
            pendingChildren.computeIfAbsent(parentAgentId, ignored -> new ArrayList<>()).add(agentCard);
            Loggers.MULTI_AGENT.debug("[{}] Queued {} as child of {}",
                    getClass().getSimpleName(), agentCard.getId(), parentAgentId);
        }
        return this;
    }

    @Override
    public CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
        assertReady();
        return setupHierarchy().thenCompose(ignored -> invokeRoot(message, session));
    }

    @Override
    public Stream<Object> stream(Object message, AgentSessionApi session) {
        assertReady();
        setupHierarchy().toCompletableFuture().join();
        if (session != null) {
            streamRootToExternalSession(message, session);
            return StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(session.streamIterator(), 0),
                    false
            );
        }
        return TeamsUtils.standaloneStreamContext(
                getRuntime(),
                getCard(),
                message,
                (teamSession, sessionId) -> streamRootToSession(message, teamSession, sessionId)
        );
    }

    public String getRootAgentId() {
        return rootAgentId;
    }

    public Map<String, List<AgentCard>> getPendingChildren() {
        Map<String, List<AgentCard>> copy = new LinkedHashMap<>();
        pendingChildren.forEach((parentId, cards) -> copy.put(parentId, List.copyOf(cards)));
        return Collections.unmodifiableMap(copy);
    }

    CompletionStage<Void> setupHierarchy() {
        Map<String, List<AgentCard>> snapshot = snapshotPendingChildren();
        if (snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (Map.Entry<String, List<AgentCard>> entry : snapshot.entrySet()) {
            String parentId = entry.getKey();
            List<AgentCard> childCards = entry.getValue();
            chain = chain.thenCompose(ignored -> Runner.resourceMgr()
                    .getAgent(parentId)
                    .thenAccept(parentAgent -> registerChildCards(parentId, parentAgent, childCards)));
        }
        return chain.thenRun(pendingChildren::clear);
    }

    void assertReady() {
        if (!getRuntime().hasAgent(rootAgentId)) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    "error_msg",
                    "Root agent '" + rootAgentId + "' is not registered in runtime. "
                            + "Call add_agent(root_card, root_provider) before invoke()/stream()."
            );
        }
    }

    private CompletionStage<Object> invokeRoot(Object message, AgentSessionApi session) {
        if (session != null) {
            return getRuntime().send(message, rootAgentId, getCard().getId(), session.getSessionId());
        }

        TeamsUtils.InvokeContext context = TeamsUtils.standaloneInvokeContext(getRuntime(), getCard(), message);
        CompletionStage<Object> response;
        try {
            response = getRuntime().send(message, rootAgentId, getCard().getId(), context.sessionId());
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
            return value;
        });
    }

    private CompletionStage<Void> streamRootToSession(Object message, AgentTeamSession teamSession, String sessionId) {
        return Runner.resourceMgr()
                .getAgent(rootAgentId)
                .thenAccept(rootAgent -> writeRootStream(message, teamSession, sessionId, rootAgent));
    }

    private void streamRootToExternalSession(Object message, AgentSessionApi session) {
        Object rootAgent = Runner.resourceMgr().getAgent(rootAgentId).toCompletableFuture().join();
        try {
            Object inputsWithSession = inputsWithSession(message, session.getSessionId());
            Iterator<Object> iterator = streamIterator(rootAgent, inputsWithSession);
            while (iterator.hasNext()) {
                session.writeStream(iterator.next());
            }
        } catch (RuntimeException exception) {
            Loggers.MULTI_AGENT.error("[{}] Error during streaming: {}",
                    getClass().getSimpleName(), exception.getMessage());
            session.writeStream(errorResult(exception));
        }
    }

    private void writeRootStream(Object message, AgentTeamSession teamSession, String sessionId, Object rootAgent) {
        try {
            Object inputsWithSession = inputsWithSession(message, sessionId);
            Iterator<Object> iterator = streamIterator(rootAgent, inputsWithSession);
            while (iterator.hasNext()) {
                teamSession.writeStream(iterator.next());
            }
        } catch (RuntimeException exception) {
            Loggers.MULTI_AGENT.error("[{}] Error during streaming: {}",
                    getClass().getSimpleName(), exception.getMessage());
            teamSession.writeStream(errorResult(exception));
        }
    }

    private void registerChildCards(String parentId, Object parentAgent, List<AgentCard> childCards) {
        AbilityManager abilityManager = abilityManagerOf(parentAgent, parentId);
        for (AgentCard childCard : childCards) {
            abilityManager.add(childCard);
            Loggers.MULTI_AGENT.debug("[{}] Registered {} -> {}.ability_manager",
                    getClass().getSimpleName(), childCard.getId(), parentId);
        }
    }

    private AbilityManager abilityManagerOf(Object parentAgent, String parentId) {
        if (parentAgent instanceof BaseAgent baseAgent) {
            return baseAgent.getAbilityManager();
        }
        for (String methodName : List.of("getAbilityManager", "get_ability_manager")) {
            try {
                Method method = parentAgent == null ? null : parentAgent.getClass().getMethod(methodName);
                if (method == null) {
                    continue;
                }
                Object result = method.invoke(parentAgent);
                if (result instanceof AbilityManager abilityManager) {
                    return abilityManager;
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot access ability manager for parent agent '" + parentId + "'",
                        exception);
            } catch (InvocationTargetException exception) {
                throw new IllegalStateException("Cannot access ability manager for parent agent '" + parentId + "'",
                        exception.getCause());
            } catch (NoSuchMethodException ignored) {
                // Try the Python-style method name next.
            }
        }
        throw ErrorHelper.buildError(
                StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                "error_msg",
                "Parent agent '" + parentId + "' does not expose ability_manager"
        );
    }

    private Iterator<Object> streamIterator(Object agent, Object inputs) {
        if (agent instanceof BaseAgent baseAgent) {
            return baseAgent.stream(inputs, null, List.of(StreamMode.OUTPUT));
        }
        Object result = invokeStreamMethod(agent, inputs);
        return toIterator(result);
    }

    private Object invokeStreamMethod(Object agent, Object inputs) {
        if (agent == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    "error_msg",
                    "Root agent '" + rootAgentId + "' is not available from Runner.resource_mgr"
            );
        }
        for (Method method : streamMethods(agent.getClass())) {
            try {
                method.setAccessible(true);
                Object value = switch (method.getParameterCount()) {
                    case 3 -> method.invoke(agent, inputs, null, List.of(StreamMode.OUTPUT));
                    case 2 -> method.invoke(agent, inputs, null);
                    case 1 -> method.invoke(agent, inputs);
                    default -> null;
                };
                return awaitIfNeeded(value);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot access stream method on " + agent.getClass().getName(),
                        exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Agent stream failed", cause);
            } catch (IllegalArgumentException ignored) {
                // Try the next overload.
            }
        }
        throw ErrorHelper.buildError(
                StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                "error_msg",
                "Agent does not expose a stream method: " + agent.getClass().getName()
        );
    }

    private List<Method> streamMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        Class<?> current = type;
        while (current != null) {
            appendCandidateStreamMethods(methods, current.getDeclaredMethods());
            current = current.getSuperclass();
        }
        appendCandidateStreamMethods(methods, type.getMethods());
        return methods;
    }

    private void appendCandidateStreamMethods(List<Method> methods, Method[] candidates) {
        for (Method method : candidates) {
            if ("stream".equals(method.getName())
                    && method.getParameterCount() >= 1
                    && method.getParameterCount() <= 3
                    && methods.stream().noneMatch(existing -> sameSignature(existing, method))) {
                methods.add(method);
            }
        }
    }

    private static boolean sameSignature(Method left, Method right) {
        return left.getName().equals(right.getName())
                && List.of(left.getParameterTypes()).equals(List.of(right.getParameterTypes()));
    }

    @SuppressWarnings("unchecked")
    private Iterator<Object> toIterator(Object value) {
        Object resolved = awaitIfNeeded(value);
        if (resolved == null) {
            return List.<Object>of().iterator();
        }
        if (resolved instanceof Iterator<?> iterator) {
            return (Iterator<Object>) iterator;
        }
        if (resolved instanceof Iterable<?> iterable) {
            return (Iterator<Object>) iterable.iterator();
        }
        if (resolved instanceof Stream<?> stream) {
            return (Iterator<Object>) stream.iterator();
        }
        return List.of(resolved).iterator();
    }

    private Object awaitIfNeeded(Object value) {
        if (value instanceof CompletionStage<?> stage) {
            return stage.toCompletableFuture().join();
        }
        return value;
    }

    private Map<String, Object> inputsWithSession(Object message, String sessionId) {
        LinkedHashMap<String, Object> inputs = new LinkedHashMap<>();
        if (message instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                inputs.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        } else {
            inputs.put("query", message);
        }
        inputs.put("conversation_id", sessionId);
        inputs.put("sender", getCard().getId());
        return inputs;
    }

    private Map<String, Object> errorResult(RuntimeException exception) {
        return new LinkedHashMap<>(Map.of(
                "output", Objects.toString(exception.getMessage(), ""),
                "result_type", "error"
        ));
    }

    private Map<String, List<AgentCard>> snapshotPendingChildren() {
        Map<String, List<AgentCard>> snapshot = new LinkedHashMap<>();
        pendingChildren.forEach((parentId, cards) -> snapshot.put(parentId, List.copyOf(cards)));
        return snapshot;
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
