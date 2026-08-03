/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams;

import com.openjiuwen.core.multi_agent.schema.TeamCard;
import com.openjiuwen.core.multi_agent.team_runtime.TeamRuntime;
import com.openjiuwen.core.session.AgentTeamSession;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterators;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Standalone team session lifecycle helpers.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/core/multi_agent/teams/utils.py}.</p>
 */
public final class TeamsUtils {

    private TeamsUtils() {
    }

    public static AgentTeamSession makeTeamSession(TeamCard card, Object message) {
        Objects.requireNonNull(card, "card must not be null");
        String sessionId = extractConversationId(message);
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        return AgentTeamSession.createAgentTeamSession(sessionId, null, card.getId());
    }

    public static InvokeContext standaloneInvokeContext(TeamRuntime runtime, TeamCard card, Object message) {
        return standaloneInvokeContext(runtime, card, message, null);
    }

    public static InvokeContext standaloneInvokeContext(
            TeamRuntime runtime,
            TeamCard card,
            Object message,
            AgentTeamSession session
    ) {
        return new InvokeContext(runtime, card, message, session);
    }

    public static <T> T withStandaloneInvokeContext(
            TeamRuntime runtime,
            TeamCard card,
            Object message,
            AgentTeamSession session,
            Function<InvokeContext, T> body
    ) {
        try (InvokeContext context = standaloneInvokeContext(runtime, card, message, session)) {
            return body.apply(context);
        }
    }

    public static Stream<Object> standaloneStreamContext(
            TeamRuntime runtime,
            TeamCard card,
            Object message,
            BiFunction<AgentTeamSession, String, CompletionStage<Void>> runCoro
    ) {
        return standaloneStreamContext(runtime, card, message, runCoro, null);
    }

    public static Stream<Object> standaloneStreamContext(
            TeamRuntime runtime,
            TeamCard card,
            Object message,
            BiFunction<AgentTeamSession, String, CompletionStage<Void>> runCoro,
            AgentTeamSession session
    ) {
        Objects.requireNonNull(runCoro, "runCoro must not be null");
        AgentTeamSession teamSession;
        boolean callerOwns = session != null;
        if (callerOwns) {
            teamSession = session;
        } else {
            Objects.requireNonNull(runtime, "runtime must not be null");
            teamSession = makeTeamSession(card, message);
            teamSession.preRun(preRunInputs(message));
            runtime.bindTeamSession(teamSession);
        }

        String sessionId = teamSession.getSessionId();
        AtomicReference<Throwable> backgroundException = new AtomicReference<>();
        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            try {
                runCoro.apply(teamSession, sessionId).toCompletableFuture().join();
            } catch (CompletionException exception) {
                backgroundException.set(unwrapCompletionException(exception));
            } catch (RuntimeException exception) {
                backgroundException.set(exception);
            } finally {
                finalizeStream(runtime, teamSession, sessionId, callerOwns);
            }
        });

        Iterator<Object> iterator = teamSession.streamIterator();
        Iterator<Object> guardingIterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                boolean hasNext = iterator.hasNext();
                if (!hasNext) {
                    awaitTask(task, backgroundException.get());
                }
                return hasNext;
            }

            @Override
            public Object next() {
                return iterator.next();
            }
        };

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(guardingIterator, 0), false)
                .onClose(() -> awaitTask(task, backgroundException.get()));
    }

    private static void finalizeStream(
            TeamRuntime runtime,
            AgentTeamSession teamSession,
            String sessionId,
            boolean callerOwns
    ) {
        if (!callerOwns) {
            runtime.unbindTeamSession(sessionId);
            runtime.cleanupSession(sessionId).join();
            teamSession.closeStream();
            teamSession.commit();
        } else {
            teamSession.closeStream();
        }
    }

    private static String extractConversationId(Object message) {
        if (message instanceof Map<?, ?> map) {
            Object conversationId = map.get("conversation_id");
            return conversationId == null ? null : String.valueOf(conversationId);
        }
        return null;
    }

    private static Map<String, Object> preRunInputs(Object message) {
        if (message instanceof Map<?, ?> map) {
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("inputs", mapToStringKeyMap(map));
            return inputs;
        }
        return null;
    }

    private static LinkedHashMap<String, Object> mapToStringKeyMap(Map<?, ?> map) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Throwable unwrapCompletionException(CompletionException exception) {
        return exception.getCause() == null ? exception : exception.getCause();
    }

    private static void awaitTask(CompletableFuture<Void> task, Throwable backgroundException) {
        try {
            task.join();
        } catch (CompletionException exception) {
            throw exception;
        }
        if (backgroundException instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (backgroundException != null) {
            throw new CompletionException(backgroundException);
        }
    }

    public static final class InvokeContext implements AutoCloseable {
        private final TeamRuntime runtime;
        private final AgentTeamSession session;
        private final String sessionId;
        private final boolean callerOwns;
        private boolean closed;

        private InvokeContext(TeamRuntime runtime, TeamCard card, Object message, AgentTeamSession existingSession) {
            this.runtime = runtime;
            this.callerOwns = existingSession != null;
            if (callerOwns) {
                this.session = existingSession;
            } else {
                Objects.requireNonNull(runtime, "runtime must not be null");
                this.session = makeTeamSession(card, message);
                this.session.preRun(preRunInputs(message));
                runtime.bindTeamSession(this.session);
            }
            this.sessionId = session.getSessionId();
        }

        public AgentTeamSession session() {
            return session;
        }

        public String sessionId() {
            return sessionId;
        }

        @Override
        public void close() {
            if (closed || callerOwns) {
                closed = true;
                return;
            }
            closed = true;
            runtime.unbindTeamSession(sessionId);
            runtime.cleanupSession(sessionId).join();
            session.closeStream();
            session.commit();
        }
    }
}
