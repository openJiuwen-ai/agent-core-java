/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import com.openjiuwen.agent_teams.agent.AgentCustomizer;
import com.openjiuwen.agent_teams.agent.MemberRuntime;
import com.openjiuwen.agent_teams.external.cli_agent.CliAgentAdapter;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Shared {@link MemberRuntime} surface for external CLI-backed members.
 *
 * <p>Mirrors Python's {@code _CliRuntimeBase} in
 * {@code openjiuwen/agent_teams/external/runtime.py}.</p>
 */
abstract class CliRuntimeBase implements MemberRuntime {

    static final String FOLLOWUP_SEPARATOR = "\n\n---\n\n";
    static final int STDERR_TAIL_LIMIT = 2000;
    static final double DEFAULT_INACTIVITY_TIMEOUT_S = 180.0d;
    static final double STDERR_PEEK_TIMEOUT_S = 5.0d;

    private final String memberName;
    private final CliAgentAdapter adapter;

    CliRuntimeBase(String memberName, CliAgentAdapter adapter) {
        this.memberName = Objects.requireNonNull(memberName, "memberName");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    protected final String memberName() {
        return memberName;
    }

    protected final CliAgentAdapter adapter() {
        return adapter;
    }

    @Override
    public final Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
        BlockingRuntimeIterator iterator = new BlockingRuntimeIterator();
        CompletableFuture.runAsync(() -> {
            try {
                drive(inputs == null ? Map.of() : inputs, iterator::emit);
            } catch (Throwable throwable) {
                iterator.fail(throwable);
            } finally {
                iterator.finish();
            }
        });
        return iterator;
    }

    protected abstract void drive(Map<String, Object> inputs, Consumer<Object> emitter);

    protected final OutputSchema makeChunk(String text, int index) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", text);
        payload.put("result_type", "answer");
        return new OutputSchema("llm_output", index, payload);
    }

    protected static String readStderrTail(InputStream stream) {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        byte[] buffer = new byte[4096];
        try {
            while (true) {
                int read = stream.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                if (builder.length() > STDERR_TAIL_LIMIT) {
                    builder.delete(0, builder.length() - STDERR_TAIL_LIMIT);
                }
            }
        } catch (IOException ignored) {
            return builder.toString().trim();
        }
        return builder.toString().trim();
    }

    protected static void terminate(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            process.waitFor();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    protected static Integer returnCode(Process process) {
        if (process == null) {
            return null;
        }
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException ignored) {
            return null;
        }
    }

    protected static String queryText(Map<String, Object> inputs) {
        Object query = inputs.get("query");
        return query instanceof String text ? text : String.valueOf(query);
    }

    @Override
    public void initCwdForRound() {
        // No-op: the external CLI subprocess owns its working directory.
    }

    @Override
    public boolean hasPendingInterrupt() {
        return false;
    }

    @Override
    public boolean isPendingInterruptResumeValid(Object userInput) {
        return false;
    }

    @Override
    public List<Object> findRails(Class<?> railType) {
        return List.of();
    }

    @Override
    public CompletionStage<Void> registerRail(Object rail) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> unregisterRail(Object rail) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void registerMemberTools(Object memoryManager) {
        // No-op: external CLI members do not mount the team memory toolkit.
    }

    @Override
    public CompletionStage<Void> injectMemberMemory(Object memoryManager, String query) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void runAgentCustomizer(AgentCustomizer customizer) {
        // No-op: customizers target local DeepAgent runtimes only.
    }

    @Override
    public Object workspace() {
        return null;
    }

    @Override
    public Object sysOperation() {
        return null;
    }

    /**
     * Mirrors Python's streaming iterator contract in
     * {@code openjiuwen/agent_teams/external/runtime.py}.
     */
    private static final class BlockingRuntimeIterator implements Iterator<Object> {

        private static final Object END = new Object();

        private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        private Object nextItem;
        private boolean finished;

        void emit(Object item) {
            queue.offer(item);
        }

        void fail(Throwable throwable) {
            queue.offer(new FailureSignal(throwable));
        }

        void finish() {
            queue.offer(END);
        }

        @Override
        public boolean hasNext() {
            if (finished) {
                return false;
            }
            if (nextItem == null) {
                try {
                    nextItem = queue.take();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for runtime output", interrupted);
                }
            }
            if (nextItem == END) {
                finished = true;
                nextItem = null;
                return false;
            }
            if (nextItem instanceof FailureSignal failureSignal) {
                Throwable cause = failureSignal.throwable();
                nextItem = null;
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("External CLI runtime failed", cause);
            }
            return true;
        }

        @Override
        public Object next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            Object current = nextItem;
            nextItem = null;
            return current;
        }
    }

    /**
     * Mirrors Python's internal failure forwarding path in
     * {@code openjiuwen/agent_teams/external/runtime.py}.
     */
    private record FailureSignal(Throwable throwable) {
    }
}
