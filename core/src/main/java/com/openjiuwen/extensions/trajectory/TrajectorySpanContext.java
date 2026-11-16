/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import io.opentelemetry.api.trace.Span;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of active trajectory run contexts and pairing state
 * for model/tool spans.
 *
 * <p>One {@link RunInfo} is registered per open run-root span and removed when
 * the span closes. Rails resolve the run to stamp session attributes on child
 * spans. Model and tool span stacks mirror Python's contextvar-based pairing:
 * bracketing hooks run on the same thread, so a thread-local deque is
 * sufficient and avoids leaking state across requests.</p>
 *
 * @since 0.1.18
 */
public final class TrajectorySpanContext {
    private static final Map<String, RunInfo> ACTIVE_RUNS = new ConcurrentHashMap<>();

    private static final ThreadLocal<Deque<LlmSpanState>> LLM_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private static final ThreadLocal<Deque<ToolSpanState>> TOOL_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Session-indexed mirror of the model-call stack. Streaming frames are
     * emitted from the runtime's callback threads, which differ from the rail
     * hook thread, so pairing by ThreadLocal alone would never find the
     * running inference span.
     */
    private static final Map<String, Deque<LlmSpanState>> SESSION_LLMS =
            new ConcurrentHashMap<>();

    private TrajectorySpanContext() {
    }

    /** Identity and routing facts of one open run-root span. */
    public static final class RunInfo {
        private final String sessionId;
        private final String mode;
        private final String requestId;
        private final String runId;
        private final String turnId;
        private final long turnNumber;
        private final long requestNumber;
        private final String subjectId;
        private final String subjectDisplayName;
        private final String subjectKind;
        private final String subjectParentId;
        private final String subjectSessionId;

        private RunInfo(Builder builder) {
            this.sessionId = builder.sessionId;
            this.mode = builder.mode;
            this.requestId = builder.requestId;
            this.runId = builder.runId;
            this.turnId = builder.turnId;
            this.turnNumber = builder.turnNumber;
            this.requestNumber = builder.requestNumber;
            this.subjectId = builder.subjectId;
            this.subjectDisplayName = builder.subjectDisplayName;
            this.subjectKind = builder.subjectKind;
            this.subjectParentId = builder.subjectParentId;
            this.subjectSessionId = builder.subjectSessionId;
        }

        /**
         * Session id owning the run.
         *
         * @return session id, never null
         */
        public String sessionId() {
            return sessionId;
        }

        /**
         * Swarm agent mode of the run.
         *
         * @return agent mode, may be null
         */
        public String mode() {
            return mode;
        }

        /**
         * Stable request id.
         *
         * @return request id, may be null
         */
        public String requestId() {
            return requestId;
        }

        /**
         * Stable run id.
         *
         * @return run id, may be null
         */
        public String runId() {
            return runId;
        }

        /**
         * ReAct turn id stitching request traces of one displayed turn.
         *
         * @return turn id, may be null
         */
        public String turnId() {
            return turnId;
        }

        /**
         * Human-visible turn number.
         *
         * @return turn number
         */
        public long turnNumber() {
            return turnNumber;
        }

        /**
         * Monotonic request number within the session.
         *
         * @return request number
         */
        public long requestNumber() {
            return requestNumber;
        }

        /**
         * Execution subject id.
         *
         * @return subject id, may be null
         */
        public String subjectId() {
            return subjectId;
        }

        /**
         * Execution subject display name.
         *
         * @return display name, may be null
         */
        public String subjectDisplayName() {
            return subjectDisplayName;
        }

        /**
         * Execution subject kind.
         *
         * @return subject kind, may be null
         */
        public String subjectKind() {
            return subjectKind;
        }

        /**
         * Parent execution subject id.
         *
         * @return parent subject id, may be null
         */
        public String subjectParentId() {
            return subjectParentId;
        }

        /**
         * Session id owning the execution subject.
         *
         * @return subject session id, may be null
         */
        public String subjectSessionId() {
            return subjectSessionId;
        }

        /**
         * Builder for {@link RunInfo}.
         *
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Fluent builder for immutable {@link RunInfo} values.
         *
         * @since 0.1.18
         */
        public static final class Builder {
            private String sessionId;
            private String mode;
            private String requestId;
            private String runId;
            private String turnId;
            private long turnNumber;
            private long requestNumber;
            private String subjectId;
            private String subjectDisplayName;
            private String subjectKind;
            private String subjectParentId;
            private String subjectSessionId;

            private Builder() {
            }

            /**
             * Set the session id.
             *
             * @param value session id
             * @return this builder
             */
            public Builder sessionId(String value) {
                this.sessionId = value;
                return this;
            }

            /**
             * Set the swarm agent mode.
             *
             * @param value agent mode
             * @return this builder
             */
            public Builder mode(String value) {
                this.mode = value;
                return this;
            }

            /**
             * Set the request id.
             *
             * @param value request id
             * @return this builder
             */
            public Builder requestId(String value) {
                this.requestId = value;
                return this;
            }

            /**
             * Set the run id.
             *
             * @param value run id
             * @return this builder
             */
            public Builder runId(String value) {
                this.runId = value;
                return this;
            }

            /**
             * Set the turn id.
             *
             * @param value turn id
             * @return this builder
             */
            public Builder turnId(String value) {
                this.turnId = value;
                return this;
            }

            /**
             * Set the turn number.
             *
             * @param value turn number
             * @return this builder
             */
            public Builder turnNumber(long value) {
                this.turnNumber = value;
                return this;
            }

            /**
             * Set the request number.
             *
             * @param value request number
             * @return this builder
             */
            public Builder requestNumber(long value) {
                this.requestNumber = value;
                return this;
            }

            /**
             * Set the execution subject id.
             *
             * @param value subject id
             * @return this builder
             */
            public Builder subjectId(String value) {
                this.subjectId = value;
                return this;
            }

            /**
             * Set the execution subject display name.
             *
             * @param value display name
             * @return this builder
             */
            public Builder subjectDisplayName(String value) {
                this.subjectDisplayName = value;
                return this;
            }

            /**
             * Set the execution subject kind.
             *
             * @param value subject kind
             * @return this builder
             */
            public Builder subjectKind(String value) {
                this.subjectKind = value;
                return this;
            }

            /**
             * Set the parent execution subject id.
             *
             * @param value parent subject id
             * @return this builder
             */
            public Builder subjectParentId(String value) {
                this.subjectParentId = value;
                return this;
            }

            /**
             * Set the execution subject session id.
             *
             * @param value subject session id
             * @return this builder
             */
            public Builder subjectSessionId(String value) {
                this.subjectSessionId = value;
                return this;
            }

            /**
             * Build the immutable run info.
             *
             * @return a new {@link RunInfo}
             */
            public RunInfo build() {
                return new RunInfo(this);
            }
        }
    }

    /** State pushed between model-call bracketing hooks. */
    public record LlmSpanState(Span span, String model, String sessionId) {
    }

    /** State pushed between tool-call bracketing hooks. */
    public record ToolSpanState(Span span, String toolName, String toolId, String sessionId) {
    }

    /**
     * Register an open run for its session id.
     *
     * @param info immutable run info
     * @throws IllegalStateException when another run is active for the session
     */
    public static void registerRun(RunInfo info) {
        Objects.requireNonNull(info, "info");
        RunInfo previous = ACTIVE_RUNS.putIfAbsent(info.sessionId(), info);
        if (previous != null) {
            throw new IllegalStateException(
                    "Run already active for session " + info.sessionId());
        }
    }

    /**
     * Remove the run registered for the session id.
     *
     * @param sessionId session id
     */
    public static void unregisterRun(String sessionId) {
        if (sessionId != null) {
            ACTIVE_RUNS.remove(sessionId);
        }
    }

    /**
     * Resolve the active run for a session.
     *
     * @param sessionId session id
     * @return the active run info, or empty when no run is open
     */
    public static Optional<RunInfo> currentRun(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ACTIVE_RUNS.get(sessionId));
    }

    /**
     * Push model-call state for the current thread and the owning session.
     *
     * @param state the model-call span state
     */
    public static void pushLlmSpanState(LlmSpanState state) {
        LLM_STACK.get().push(state);
        if (state.sessionId() != null) {
            SESSION_LLMS.computeIfAbsent(state.sessionId(),
                    key -> new java.util.concurrent.ConcurrentLinkedDeque<>()).push(state);
        }
    }

    /**
     * Pop the most recent model-call state for the current thread.
     *
     * @return the state, or empty when the stack is empty
     */
    public static Optional<LlmSpanState> popLlmSpanState() {
        Deque<LlmSpanState> stack = LLM_STACK.get();
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        LlmSpanState popped = stack.pop();
        if (popped.sessionId() != null) {
            Deque<LlmSpanState> sessionStack = SESSION_LLMS.get(popped.sessionId());
            if (sessionStack != null) {
                sessionStack.remove(popped);
                if (sessionStack.isEmpty()) {
                    SESSION_LLMS.remove(popped.sessionId(), sessionStack);
                }
            }
        }
        return Optional.of(popped);
    }

    /**
     * Resolve the innermost model-call span state of the current thread when
     * it belongs to the given session.
     *
     * <p>Bracketing hooks run on one thread, so rail-internal pairing keeps
     * using this; cross-thread consumers should use
     * {@link #currentLlmSpanStateForSession(String)}.</p>
     *
     * @param sessionId session id to match, or null to accept any session
     * @return the innermost model-call state, or empty when absent
     */
    public static Optional<LlmSpanState> currentLlmSpanState(String sessionId) {
        LlmSpanState top = LLM_STACK.get().peek();
        if (top != null && (sessionId == null || sessionId.equals(top.sessionId()))) {
            return Optional.of(top);
        }
        return Optional.empty();
    }

    /**
     * Resolve the innermost running model-call span of a session regardless of
     * the calling thread.
     *
     * <p>Stream-frame emitters run on the runtime's streaming callback threads
     * and use this to attach frames to the running inference span without
     * knowing the span ids.</p>
     *
     * @param sessionId session id, may be null
     * @return the running model-call state, or empty when none is open
     */
    public static Optional<LlmSpanState> currentLlmSpanStateForSession(
            String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        Deque<LlmSpanState> stack = SESSION_LLMS.get(sessionId);
        LlmSpanState top = stack == null ? null : stack.peek();
        return top == null ? Optional.empty() : Optional.of(top);
    }

    /**
     * Push tool-call state for the current thread.
     *
     * @param state the tool-call span state
     */
    public static void pushToolSpanState(ToolSpanState state) {
        TOOL_STACK.get().push(state);
    }

    /**
     * Remove and return the tool-call state matching the tool name.
     *
     * <p>Nested tool calls for other names stay on the stack.</p>
     *
     * @param toolName tool name to match
     * @return the matching state, or empty when absent
     */
    public static Optional<ToolSpanState> popToolSpanState(String toolName) {
        Deque<ToolSpanState> stack = TOOL_STACK.get();
        ToolSpanState top = stack.isEmpty() ? null : stack.pop();
        if (top != null && Objects.equals(top.toolName(), toolName)) {
            return Optional.of(top);
        }
        if (top != null) {
            stack.push(top);
        }
        Iterator<ToolSpanState> iterator = stack.descendingIterator();
        while (iterator.hasNext()) {
            ToolSpanState state = iterator.next();
            if (Objects.equals(state.toolName(), toolName)) {
                iterator.remove();
                return Optional.of(state);
            }
        }
        return Optional.ofNullable(top);
    }

    /**
     * Clear all thread-local pairing state; used by tests and shutdown paths.
     */
    public static void clearThreadState() {
        LLM_STACK.get().clear();
        TOOL_STACK.get().clear();
        LLM_STACK.remove();
        TOOL_STACK.remove();
        SESSION_LLMS.clear();
    }
}
