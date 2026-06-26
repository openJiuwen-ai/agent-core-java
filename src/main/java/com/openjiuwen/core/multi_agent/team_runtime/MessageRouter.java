/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.team_runtime;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Routes P2P and Pub-Sub messages to agents via {@link TeamRuntime}.
 *
 * <p>Mirrors Python's {@code MessageRouter} in
 * {@code openjiuwen/core/multi_agent/team_runtime/message_router.py}.</p>
 */
public class MessageRouter {

    private final SubscriptionManager subscriptionManager;
    private TeamRuntime runtime;

    public MessageRouter(SubscriptionManager subscriptionManager, TeamRuntime runtime) {
        this.subscriptionManager = subscriptionManager;
        this.runtime = runtime;
    }

    public void setRuntime(TeamRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Route a P2P message to the recipient and return the response.
     *
     * @param envelope message envelope
     * @return response future
     */
    public CompletableFuture<Object> routeP2pMessage(MessageEnvelope envelope) {
        Loggers.MULTI_AGENT.debug("[{}] Routing P2P message {} to {} with session_id={}",
                getClass().getSimpleName(), envelope.getMessageId(), envelope.getRecipient(), envelope.getSessionId());
        if (runtime == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("MessageRouter requires a TeamRuntime"));
        }

        try {
            Object session = buildAgentSession(envelope.getSessionId(), envelope.getRecipient());
            Object sessionArgument = session != null ? session : envelope.getSessionId();
            return runtime.dispatchToAgent(envelope.getRecipient(), envelope.getMessage(), sessionArgument)
                    .toCompletableFuture();
        } catch (Exception exception) {
            String errorMsg = "Error routing P2P message " + envelope.getMessageId() + ": " + exception.getMessage();
            Loggers.MULTI_AGENT.error("[{}] {}", getClass().getSimpleName(), errorMsg);
            return CompletableFuture.failedFuture(ErrorHelper.buildError(
                    StatusCode.RUNNER_RUN_AGENT_ERROR,
                    "agent", String.valueOf(envelope.getRecipient()),
                    "reason", errorMsg
            ));
        }
    }

    /**
     * Fan out a Pub-Sub message to all matching subscribers.
     *
     * @param envelope message envelope with topic id
     * @return completion future
     */
    public CompletableFuture<Void> routePubsubMessage(MessageEnvelope envelope) {
        Loggers.MULTI_AGENT.debug("[{}] Routing Pub-Sub message {} to topic {}",
                getClass().getSimpleName(), envelope.getMessageId(), envelope.getTopicId());

        List<String> subscribers = subscriptionManager.getSubscribers(envelope.getTopicId());
        if (subscribers.isEmpty()) {
            Loggers.MULTI_AGENT.warning("[{}] No subscribers for topic '{}', message '{}' dropped (fire-and-forget).",
                    getClass().getSimpleName(), envelope.getTopicId(), envelope.getMessageId());
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String subscriber : subscribers) {
            futures.add(invokeSubscriber(subscriber, envelope));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> Loggers.MULTI_AGENT.debug("[{}] Pub-Sub message {} delivered to {} subscribers",
                        getClass().getSimpleName(), envelope.getMessageId(), subscribers.size()));
    }

    private CompletableFuture<Void> invokeSubscriber(String subscriber, MessageEnvelope envelope) {
        if (runtime == null) {
            Loggers.MULTI_AGENT.error("[{}] MessageRouter requires a TeamRuntime for subscriber {}",
                    getClass().getSimpleName(), subscriber);
            return CompletableFuture.completedFuture(null);
        }

        Object session = buildAgentSession(envelope.getSessionId(), subscriber);
        Object sessionArgument = session != null ? session : envelope.getSessionId();
        return runtime.dispatchToAgent(subscriber, envelope.getMessage(), sessionArgument)
                .thenApply(ignored -> (Void) null)
                .exceptionally(error -> {
                    Loggers.MULTI_AGENT.error("[{}] Error invoking subscriber {}: {}",
                            getClass().getSimpleName(), subscriber, error.getMessage());
                    return null;
                })
                .toCompletableFuture();
    }

    private Object buildAgentSession(String sessionId, String agentId) {
        if (runtime == null || sessionId == null) {
            return null;
        }
        Object teamSession = runtime.getTeamSession(sessionId);
        if (teamSession == null) {
            return null;
        }

        Object card = runtime.getAgentCard(agentId);
        Object created = invokeCreateAgentSession(teamSession, card, agentId);
        return created != null ? created : teamSession;
    }

    private Object invokeCreateAgentSession(Object teamSession, Object card, String agentId) {
        for (String methodName : List.of("createAgentSession", "create_agent_session")) {
            for (Method method : teamSession.getClass().getMethods()) {
                if (!methodName.equals(method.getName())) {
                    continue;
                }
                try {
                    if (method.getParameterCount() == 2) {
                        return method.invoke(teamSession, card, agentId);
                    }
                    if (method.getParameterCount() == 1) {
                        return method.invoke(teamSession, agentId);
                    }
                } catch (IllegalAccessException ignored) {
                    return null;
                } catch (InvocationTargetException exception) {
                    throw new IllegalStateException("create_agent_session failed", exception.getCause());
                }
            }
        }
        return null;
    }
}
