/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multi_agent.team_runtime.CommunicableAgent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Internal per-agent wrapper created by {@code HandoffTeam}.
 *
 * <p>Mirrors Python's {@code ContainerAgent} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/container_agent.py}.</p>
 */
public class ContainerAgent extends BaseAgent implements CommunicableAgent {

    public static final String CONTEXT_HISTORY_KEY = "__handoff_ctx_history__";
    public static final String DEFAULT_CONTEXT_ID = "default_context_id";

    private final Supplier<? extends BaseAgent> targetProvider;
    private final Set<String> allowedTargets;
    private final Function<String, HandoffOrchestrator> coordinatorLookup;
    private BaseAgent targetInstance;
    private boolean toolsInjected;

    public ContainerAgent(AgentCard targetCard,
                          Supplier<? extends BaseAgent> targetProvider,
                          Collection<String> allowedTargets) {
        this(targetCard, targetProvider, allowedTargets, null);
    }

    public ContainerAgent(AgentCard targetCard,
                          Supplier<? extends BaseAgent> targetProvider,
                          Collection<String> allowedTargets,
                          Function<String, HandoffOrchestrator> coordinatorLookup) {
        super(targetCard);
        this.targetProvider = Objects.requireNonNull(targetProvider, "targetProvider");
        this.allowedTargets = allowedTargets == null ? Set.of() : new LinkedHashSet<>(allowedTargets);
        this.coordinatorLookup = coordinatorLookup;
    }

    protected BaseAgent getTargetAgent() {
        if (targetInstance == null) {
            targetInstance = targetProvider.get();
        }
        return targetInstance;
    }

    protected void injectToolsOnce(BaseAgent targetAgent) {
        if (toolsInjected) {
            return;
        }
        toolsInjected = true;
        AbilityManager abilityManager = targetAgent.getAbilityManager();
        if (abilityManager == null) {
            Loggers.MULTI_AGENT.debug("[{}:{}] '{}' has no ability_manager, skipping",
                    getClass().getSimpleName(), getCard().getId(), targetAgent.getCard().getId());
            return;
        }

        List<String> sortedTargets = new ArrayList<>(allowedTargets);
        Collections.sort(sortedTargets);
        for (String targetId : sortedTargets) {
            AgentCard card = isBound() ? getRuntime().getAgentCard(targetId) : null;
            String description = card == null ? "" : Objects.toString(card.getDescription(), "");
            HandoffTool tool = new HandoffTool(targetId, description);
            abilityManager.add(tool.getCard());
            try {
                Runner.getResourceMgr().addTool(tool, List.of(targetAgent.getCard().getId()), false);
            } catch (RuntimeException duplicateOrRegistryError) {
                Loggers.MULTI_AGENT.debug("[{}:{}] skipped resource registration for '{}': {}",
                        getClass().getSimpleName(), getCard().getId(), tool.getCard().getName(),
                        duplicateOrRegistryError.toString());
            }
            Loggers.MULTI_AGENT.debug("[{}:{}] injected '{}' -> '{}'",
                    getClass().getSimpleName(), getCard().getId(), tool.getCard().getName(),
                    targetAgent.getCard().getId());
        }
    }

    protected Object buildAgentInput(HandoffRequest inputs) {
        Object message = inputs.getInputMessage();
        List<Map<String, Object>> history = inputs.getHistory();
        if (history == null || history.isEmpty()) {
            return message;
        }
        if (message instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = stringObjectMap(rawMap);
            result.put("handoff_history", history);
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", message);
        result.put("handoff_history", history);
        return result;
    }

    public static List<Object> stripHandoffMessages(List<?> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Object> cleaned = new ArrayList<>();
        for (Object message : messages) {
            if ("tool".equals(Objects.toString(readProperty(message, "role"), ""))) {
                continue;
            }
            Object toolCalls = readProperty(message, "toolCalls");
            if (toolCalls == null) {
                toolCalls = readProperty(message, "tool_calls");
            }
            boolean hasToolCalls = toolCalls instanceof Collection<?> calls && !calls.isEmpty();
            boolean assistantLike = message instanceof com.openjiuwen.core.foundation.llm.schema.AssistantMessage
                    || "assistant".equals(Objects.toString(readProperty(message, "role"), ""));
            if (assistantLike && hasToolCalls) {
                continue;
            }
            cleaned.add(message);
        }
        return cleaned;
    }

    protected void saveContextToTeamSession(AgentSessionApi agentSession, AgentTeamSession teamSession) {
        if (agentSession == null || teamSession == null) {
            return;
        }
        Object contextState = agentSession.getState("context");
        if (!(contextState instanceof Map<?, ?> contextMap)) {
            return;
        }
        Object defaultContext = contextMap.get(DEFAULT_CONTEXT_ID);
        if (!(defaultContext instanceof Map<?, ?> defaultMap)) {
            return;
        }
        Object messagesValue = defaultMap.get("messages");
        if (!(messagesValue instanceof List<?> messages) || messages.isEmpty()) {
            return;
        }

        List<Object> cleaned = stripHandoffMessages(messages);
        if (cleaned.isEmpty()) {
            return;
        }

        List<Object> existing = asObjectList(teamSession.getState(CONTEXT_HISTORY_KEY));
        Set<MessageKey> existingKeys = new LinkedHashSet<>();
        for (Object item : existing) {
            existingKeys.add(MessageKey.from(item));
        }

        List<Object> toAppend = new ArrayList<>();
        for (Object item : cleaned) {
            if (!existingKeys.contains(MessageKey.from(item))) {
                toAppend.add(item);
            }
        }
        if (toAppend.isEmpty()) {
            return;
        }

        List<Object> updated = new ArrayList<>(existing);
        updated.addAll(toAppend);
        teamSession.updateState(Map.of(CONTEXT_HISTORY_KEY, updated));
        Loggers.MULTI_AGENT.debug("[{}:{}] saved {} messages to team context history (total={})",
                getClass().getSimpleName(), getCard().getId(), toAppend.size(), updated.size());
    }

    protected void injectContextHistory(AgentSessionApi agentSession, AgentTeamSession teamSession) {
        if (agentSession == null || teamSession == null) {
            return;
        }
        List<Object> historyMessages = asObjectList(teamSession.getState(CONTEXT_HISTORY_KEY));
        if (historyMessages.isEmpty()) {
            return;
        }
        Map<String, Object> defaultContext = new LinkedHashMap<>();
        defaultContext.put("messages", new ArrayList<>(historyMessages));
        defaultContext.put("offload_messages", new LinkedHashMap<>());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put(DEFAULT_CONTEXT_ID, defaultContext);
        agentSession.updateState(Map.of("context", context));
        Loggers.MULTI_AGENT.debug("[{}:{}] injected {} history messages into agent_session for '{}'",
                getClass().getSimpleName(), getCard().getId(), historyMessages.size(), getCard().getId());
    }

    @Override
    public BaseAgent configure(Object config) {
        return this;
    }

    public CompletionStage<Object> invoke(HandoffRequest inputs, Session session) {
        return invoke((Object) inputs, (AgentSessionApi) session);
    }

    @Override
    public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
        if (!(inputs instanceof HandoffRequest request)) {
            return CompletableFuture.completedFuture(Map.of());
        }
        String sessionId = request.getSessionId();
        HandoffOrchestrator coordinator = coordinatorLookup == null ? null : coordinatorLookup.apply(sessionId);
        if (coordinator == null) {
            String errorMessage = sessionId == null || sessionId.isEmpty()
                    ? "ContainerAgent invoked without a HandoffTeam session (session_id is empty)"
                    : "coordinator not found for session_id='" + sessionId
                    + "'; session may have already ended or never been registered";
            Loggers.MULTI_AGENT.error("[{}:{}] {}", getClass().getSimpleName(), getCard().getId(), errorMessage);
            throw ErrorHelper.buildError(StatusCode.AGENT_TEAM_EXECUTION_ERROR, "error_msg", errorMessage);
        }

        List<Map<String, Object>> history = request.getHistory() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getHistory());
        CompletionStage<TargetResult> targetStage;
        try {
            BaseAgent targetAgent = getTargetAgent();
            injectToolsOnce(targetAgent);
            Object agentInput = buildAgentInput(request);
            Loggers.MULTI_AGENT.info("[{}:{}] invoking session_id='{}' resolved_agent='{}' provider_type={} hop={} streaming={}",
                    getClass().getSimpleName(), getCard().getId(), sessionId, targetAgent.getCard().getId(),
                    targetProvider.getClass().getSimpleName(), history.size(), request.getSession() != null);
            if (request.getSession() != null) {
                targetStage = invokeTargetWithStream(targetAgent, agentInput, request.getSession());
            } else {
                AgentSession agentSession = AgentSession.createAgentSession(
                        sessionId == null || sessionId.isEmpty() ? null : sessionId,
                        null,
                        targetAgent.getCard()
                );
                targetStage = targetAgent.invoke(agentInput, agentSession)
                        .thenApply(result -> new TargetResult(
                                targetAgent,
                                result,
                                HandoffSignal.extractHandoffSignal(result, agentSession)
                        ));
            }
        } catch (RuntimeException error) {
            targetStage = CompletableFuture.failedFuture(error);
        }

        return targetStage.handle((targetResult, error) -> {
            if (error != null) {
                return handleTargetException(unwrap(error), coordinator, history, request);
            }
            return handleTargetResult(targetResult, coordinator, history, request);
        }).thenCompose(Function.identity());
    }

    @Override
    public java.util.Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        return List.of(invoke(inputs, session).toCompletableFuture().join()).iterator();
    }

    protected CompletionStage<TargetResult> invokeTargetWithStream(BaseAgent targetAgent,
                                                                   Object agentInput,
                                                                   AgentTeamSession teamSession) {
        AgentSession agentSession = teamSession.createAgentSession(targetAgent.getCard(), null);
        injectContextHistory(agentSession, teamSession);
        return targetAgent.invoke(agentInput, agentSession)
                .thenApply(result -> {
                    if (result instanceof Map<?, ?>) {
                        teamSession.writeStream(result);
                    } else if (result instanceof List<?> values) {
                        for (Object value : values) {
                            if (value instanceof Map<?, ?>) {
                                teamSession.writeStream(value);
                            }
                        }
                    }
                    saveAgentContext(targetAgent, agentSession);
                    saveContextToTeamSession(agentSession, teamSession);
                    return new TargetResult(targetAgent, result, HandoffSignal.extractHandoffSignal(result, agentSession));
                });
    }

    protected void saveAgentContext(BaseAgent targetAgent, AgentSessionApi agentSession) {
        Object contextEngine = readNoArg(targetAgent, "getContextEngine");
        if (contextEngine == null && targetAgent.getAbilityManager() != null) {
            contextEngine = targetAgent.getAbilityManager().getContextEngine();
        }
        if (contextEngine == null) {
            return;
        }
        try {
            Method method = contextEngine.getClass().getMethod("saveContexts", Object.class);
            method.invoke(contextEngine, agentSession);
        } catch (NoSuchMethodException ignored) {
            // Mirrors Python getattr(..., None): no context engine save hook is a no-op.
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException error) {
            Loggers.MULTI_AGENT.warning("[{}:{}] failed to save agent context for '{}': {}",
                    getClass().getSimpleName(), getCard().getId(), targetAgent.getCard().getId(), error.toString());
        }
    }

    private CompletionStage<Object> handleTargetResult(TargetResult targetResult,
                                                       HandoffOrchestrator coordinator,
                                                       List<Map<String, Object>> history,
                                                       HandoffRequest request) {
        Map<String, Object> historyItem = new LinkedHashMap<>();
        historyItem.put("agent", targetResult.targetAgent().getCard().getId());
        historyItem.put("output", targetResult.result());
        history.add(historyItem);

        Optional<TeamInterruptSignal> interruptSignal = HandoffInterrupts.extractInterruptSignal(targetResult.result());
        if (interruptSignal.isPresent()) {
            return handleTeamInterrupt(interruptSignal.get(), coordinator, history, request);
        }

        Optional<HandoffSignal> signal = targetResult.signal();
        if (signal.isEmpty()) {
            Loggers.MULTI_AGENT.info("[{}:{}] completing session_id='{}' resolved_agent='{}'",
                    getClass().getSimpleName(), getCard().getId(), request.getSessionId(),
                    targetResult.targetAgent().getCard().getId());
            return coordinator.complete(targetResult.result()).thenApply(ignored -> (Object) Map.of());
        }

        HandoffSignal handoffSignal = signal.get();
        return coordinator.requestHandoff(handoffSignal.getTarget(), handoffSignal.getReason().orElse(null))
                .thenCompose(allowed -> {
                    if (Boolean.TRUE.equals(allowed)) {
                        Loggers.MULTI_AGENT.info("[{}:{}] handoff approved session_id='{}' from='{}' to='{}'",
                                getClass().getSimpleName(), getCard().getId(), request.getSessionId(),
                                targetResult.targetAgent().getCard().getId(), handoffSignal.getTarget());
                        Object nextInput = handoffSignal.getMessage().<Object>map(value -> value)
                                .orElse(request.getInputMessage());
                        HandoffRequest nextRequest = new HandoffRequest(nextInput, history, request.getSession());
                        return publish(nextRequest, "container_" + handoffSignal.getTarget(), request.getSessionId())
                                .thenApply(ignored -> (Object) Map.of());
                    }
                    Loggers.MULTI_AGENT.info("[{}:{}] handoff blocked session_id='{}' target='{}' completing with current result",
                            getClass().getSimpleName(), getCard().getId(), request.getSessionId(),
                            handoffSignal.getTarget());
                    return coordinator.complete(targetResult.result()).thenApply(ignored -> (Object) Map.of());
                });
    }

    private CompletionStage<Object> handleTargetException(Throwable error,
                                                          HandoffOrchestrator coordinator,
                                                          List<Map<String, Object>> history,
                                                          HandoffRequest request) {
        Optional<TeamInterruptSignal> interruptSignal = HandoffInterrupts.extractInterruptSignal(null, error);
        if (interruptSignal.isPresent()) {
            return handleTeamInterrupt(interruptSignal.get(), coordinator, history, request);
        }
        String errorMessage = "agent execution error in '" + getCard().getId() + "': " + error;
        Loggers.MULTI_AGENT.exception("[{}:{}] {}", error, getClass().getSimpleName(), getCard().getId(), errorMessage);
        RuntimeException structuredError = ErrorHelper.buildError(
                StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                null,
                null,
                error,
                Map.of("error_msg", errorMessage)
        );
        return coordinator.error(structuredError).thenApply(ignored -> (Object) Map.of());
    }

    protected CompletionStage<Object> handleTeamInterrupt(TeamInterruptSignal signal,
                                                          HandoffOrchestrator coordinator,
                                                          List<Map<String, Object>> history,
                                                          HandoffRequest inputs) {
        AgentTeamSession teamSession = inputs.getSession();
        if (teamSession != null) {
            coordinator.saveToSession(teamSession);
            teamSession.updateState(Map.of(HandoffOrchestrator.HANDOFF_HISTORY_KEY, history));
            HandoffInterrupts.flushTeamSession(teamSession);
        }
        return coordinator.complete(signal.getResult()).thenApply(ignored -> (Object) Map.of());
    }

    public Set<String> getAllowedTargets() {
        return Set.copyOf(allowedTargets);
    }

    public Supplier<? extends BaseAgent> getTargetProvider() {
        return targetProvider;
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        if (error instanceof InvocationTargetException invocationException && invocationException.getCause() != null) {
            return invocationException.getCause();
        }
        return error;
    }

    private static Object readNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object readProperty(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        String accessor = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return target.getClass().getMethod(accessor).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private static List<Object> asObjectList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return new ArrayList<>(values);
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * Dynamic message identity matching Python's tuple key in {@code _save_context_to_team_session}.
     */
    private record MessageKey(Object role, String content, String toolCalls, Object toolCallId) {
        private static MessageKey from(Object message) {
            Object toolCalls = readProperty(message, "toolCalls");
            if (toolCalls == null) {
                toolCalls = readProperty(message, "tool_calls");
            }
            Object toolCallId = readProperty(message, "toolCallId");
            if (toolCallId == null) {
                toolCallId = readProperty(message, "tool_call_id");
            }
            return new MessageKey(
                    Objects.toString(readProperty(message, "role"), ""),
                    Objects.toString(readProperty(message, "content"), ""),
                    Objects.toString(toolCalls, ""),
                    Objects.toString(toolCallId, "")
            );
        }
    }

    protected record TargetResult(BaseAgent targetAgent, Object result, Optional<HandoffSignal> signal) {
    }
}
