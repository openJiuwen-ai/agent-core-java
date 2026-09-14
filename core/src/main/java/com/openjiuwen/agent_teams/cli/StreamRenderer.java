/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.harness.cli.ui.CliRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Starts and renders a team-agent stream in the background.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.cli.stream_renderer} in
 * {@code openjiuwen/agent_teams/cli/stream_renderer.py}.</p>
 */
public final class StreamRenderer {

    static final String TEAM_RUNTIME_READY_EVENT = "team.runtime_ready";
    static final String REASONING_PREFIX = "\033[2m🤔 ";
    static final String REASONING_RESET = "\033[0m\n";

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamRenderer.class);
    private static final String CHUNK_LLM_REASONING = "llm_reasoning";
    private static final String CHUNK_MESSAGE = "message";

    private StreamRenderer() {
    }

    public static StreamHandle spawnStream(
            TeamAgentSpec spec,
            String sessionId,
            Map<String, Object> inputs,
            PrintStream terminal,
            PrintStream console
    ) {
        return spawnStream(spec, sessionId, inputs, terminal, console, null, true);
    }

    public static StreamHandle spawnStream(
            TeamAgentSpec spec,
            String sessionId,
            Map<String, Object> inputs,
            PrintStream terminal,
            PrintStream console,
            OnRuntimeReady onRuntimeReady,
            boolean showReasoning
    ) {
        return spawnStream(
                spec,
                sessionId,
                inputs,
                terminal,
                console,
                onRuntimeReady,
                showReasoning,
                (teamSpec, streamInputs, streamSession) -> Runner.runAgentTeamStreaming(
                        teamSpec, streamInputs, streamSession, null, null, null),
                new CliRenderer(),
                ForkJoinPool.commonPool()
        );
    }

    static StreamHandle spawnStream(
            TeamAgentSpec spec,
            String sessionId,
            Map<String, Object> inputs,
            PrintStream terminal,
            PrintStream console,
            OnRuntimeReady onRuntimeReady,
            boolean showReasoning,
            StreamSource streamSource,
            CliRenderer renderer,
            Executor executor
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(terminal, "terminal");
        Objects.requireNonNull(console, "console");
        Objects.requireNonNull(streamSource, "streamSource");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(executor, "executor");

        CompletableFuture<Map<String, Object>> runtimeReady = new CompletableFuture<>();
        StreamHandle handle = new StreamHandle(
                spec.getTeamName(),
                sessionId,
                runtimeReady,
                CompletableFuture.completedFuture(null)
        );

        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> consume(
                spec,
                sessionId,
                inputs == null ? Map.<String, Object>of() : inputs,
                terminal,
                console,
                onRuntimeReady,
                showReasoning,
                streamSource,
                renderer,
                handle
        ), executor);
        handle.setTask(task);
        return handle;
    }

    public static CompletionStage<Void> stopStream(StreamHandle handle) {
        Objects.requireNonNull(handle, "handle");
        handle.setCancelled(true);
        CompletableFuture<Void> task = handle.getTask();
        if (task == null || task.isDone()) {
            return CompletableFuture.completedFuture(null);
        }
        task.cancel(true);
        return CompletableFuture.runAsync(() -> {
            try {
                task.join();
            } catch (CancellationException ignored) {
                // Python suppresses asyncio.CancelledError during stream teardown.
            } catch (CompletionException error) {
                if (!(error.getCause() instanceof CancellationException)) {
                    throw error;
                }
            }
        });
    }

    static Iterator<Object> wrapStream(
            Iterator<?> source,
            StreamHandle handle,
            OnRuntimeReady onRuntimeReady,
            PrintStream terminal,
            boolean showReasoning
    ) {
        return new StreamFilteringIterator(source, handle, onRuntimeReady, terminal, showReasoning);
    }

    private static void consume(
            TeamAgentSpec spec,
            String sessionId,
            Map<String, Object> inputs,
            PrintStream terminal,
            PrintStream console,
            OnRuntimeReady onRuntimeReady,
            boolean showReasoning,
            StreamSource streamSource,
            CliRenderer renderer,
            StreamHandle handle
    ) {
        try {
            Iterator<Object> source = await(streamSource.open(spec, inputs, sessionId));
            Iterator<Object> filtered = wrapStream(source, handle, onRuntimeReady, terminal, showReasoning);
            console.println("[dim cyan][" + handle.getTeamName() + "] stream started (session="
                    + handle.getSessionId() + ")[/dim cyan]");
            renderer.renderStream(filtered, terminal, console, null, false);
        } catch (CancellationException error) {
            if (!handle.isCancelled()) {
                LOGGER.info("[cli.stream] cancelled team={} session={}", handle.getTeamName(), handle.getSessionId());
            }
            if (!handle.getRuntimeReady().isDone()) {
                handle.getRuntimeReady().cancel(false);
            }
            throw error;
        } catch (Exception error) {
            if (!handle.getRuntimeReady().isDone()) {
                handle.getRuntimeReady().completeExceptionally(error);
            }
            LOGGER.warn("[cli.stream] failed team={} session={}: {}",
                    handle.getTeamName(), handle.getSessionId(), error.toString());
            console.println("[red]\\[" + handle.getTeamName() + "] stream failed: "
                    + escapeRichMarkup(error.getMessage()) + "[/red]");
        }
    }

    private static <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw error;
        }
    }

    private static String escapeRichMarkup(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
    }

    private static Object readMember(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            // Fall through to getter lookup.
        }
        try {
            String methodName = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Map<String, Object> mapPayload(Object payload) {
        if (!(payload instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> mapped = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                mapped.put(String.valueOf(key), value);
            }
        });
        return mapped;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String contentFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return "";
        }
        Object content = payload.get("content");
        return content == null ? "" : String.valueOf(content);
    }

    /**
     * Runtime-ready callback alias.
     *
     * <p>Mirrors Python's {@code OnRuntimeReady} in
     * {@code openjiuwen/agent_teams/cli/stream_renderer.py}.</p>
     */
    @FunctionalInterface
    public interface OnRuntimeReady {
        CompletionStage<Void> onRuntimeReady(String teamName, String sessionId, Map<String, Object> payload);
    }

    /**
     * Testable source adapter for {@link Runner#runAgentTeamStreaming(Object, Object, Object, com.openjiuwen.core.context.ModelContext, java.util.List, Map)}.
     *
     * <p>Mirrors Python's call to {@code Runner.run_agent_team_streaming} in
     * {@code openjiuwen/agent_teams/cli/stream_renderer.py}.</p>
     */
    @FunctionalInterface
    interface StreamSource {
        CompletionStage<Iterator<Object>> open(TeamAgentSpec spec, Map<String, Object> inputs, String sessionId);
    }

    /**
     * Empty message chunk used to flush the harness renderer before reasoning text.
     *
     * <p>Mirrors Python's {@code _BoundaryChunk} in
     * {@code openjiuwen/agent_teams/cli/stream_renderer.py}.</p>
     */
    static final class BoundaryChunk {
        private final String type = CHUNK_MESSAGE;
        private final Map<String, Object> payload = Map.of("content", "");

        public String getType() {
            return type;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }
    }

    /**
     * Lazy stream wrapper that filters runtime-ready chunks and renders reasoning tokens inline.
     *
     * <p>Mirrors Python's {@code _wrap_stream} in
     * {@code openjiuwen/agent_teams/cli/stream_renderer.py}.</p>
     */
    private static final class StreamFilteringIterator implements Iterator<Object> {
        private final Iterator<?> source;
        private final StreamHandle handle;
        private final OnRuntimeReady onRuntimeReady;
        private final PrintStream terminal;
        private final boolean showReasoning;

        private boolean callbackPending = true;
        private boolean inReasoning;
        private boolean nextReady;
        private boolean endReached;
        private Object next;
        private String pendingReasoning;

        private StreamFilteringIterator(
                Iterator<?> source,
                StreamHandle handle,
                OnRuntimeReady onRuntimeReady,
                PrintStream terminal,
                boolean showReasoning
        ) {
            this.source = Objects.requireNonNull(source, "source");
            this.handle = Objects.requireNonNull(handle, "handle");
            this.onRuntimeReady = onRuntimeReady;
            this.terminal = Objects.requireNonNull(terminal, "terminal");
            this.showReasoning = showReasoning;
        }

        @Override
        public boolean hasNext() {
            if (nextReady) {
                return true;
            }
            loadNext();
            return nextReady;
        }

        @Override
        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Object value = next;
            next = null;
            nextReady = false;
            return value;
        }

        private void loadNext() {
            if (endReached) {
                return;
            }
            writePendingReasoning();
            while (source.hasNext()) {
                Object chunk = source.next();
                Map<String, Object> payload = mapPayload(readMember(chunk, "payload"));
                if (payload != null && TEAM_RUNTIME_READY_EVENT.equals(payload.get("event_type"))) {
                    completeRuntimeReady(payload);
                    continue;
                }

                String chunkType = stringValue(readMember(chunk, "type"));
                if (CHUNK_LLM_REASONING.equals(chunkType)) {
                    handleReasoning(payload);
                    if (nextReady) {
                        return;
                    }
                    continue;
                }

                if (inReasoning) {
                    terminal.print(REASONING_RESET);
                    terminal.flush();
                    inReasoning = false;
                }
                next = chunk;
                nextReady = true;
                return;
            }
            if (inReasoning) {
                terminal.print(REASONING_RESET);
                terminal.flush();
                inReasoning = false;
            }
            endReached = true;
        }

        private void handleReasoning(Map<String, Object> payload) {
            if (!showReasoning) {
                return;
            }
            String text = contentFromPayload(payload);
            if (text.isEmpty()) {
                return;
            }
            if (!inReasoning) {
                inReasoning = true;
                pendingReasoning = text;
                next = new BoundaryChunk();
                nextReady = true;
                return;
            }
            terminal.print(text);
            terminal.flush();
        }

        private void writePendingReasoning() {
            if (pendingReasoning == null) {
                return;
            }
            terminal.print(REASONING_PREFIX);
            terminal.print(pendingReasoning);
            terminal.flush();
            pendingReasoning = null;
        }

        private void completeRuntimeReady(Map<String, Object> payload) {
            if (!handle.getRuntimeReady().isDone()) {
                handle.getRuntimeReady().complete(payload);
            }
            if (!callbackPending || onRuntimeReady == null) {
                return;
            }
            callbackPending = false;
            try {
                CompletionStage<Void> stage = onRuntimeReady.onRuntimeReady(
                        handle.getTeamName(),
                        handle.getSessionId(),
                        payload
                );
                if (stage != null) {
                    stage.toCompletableFuture().join();
                }
            } catch (Exception ignored) {
                // Python suppresses callback exceptions so stream rendering can continue.
            }
        }
    }
}
