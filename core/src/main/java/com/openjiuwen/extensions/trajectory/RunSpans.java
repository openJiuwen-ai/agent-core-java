/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

/**
 * Opens and closes the run-root span of one agent request.
 *
 * <p>Mirrors Python Agent Core
 * {@code openjiuwen/harness/observability/run_span.py}: the span is named
 * {@code agent.<mode>.<sessionId>}, kind {@code SERVER}, and carries the
 * routing facts the trajectory projector needs. Exactly one run is open per
 * session at a time; the swarm dispatcher opens the span before streaming and
 * closes it on every terminal path (completion, error, cancellation,
 * disconnect).</p>
 *
 * @since 0.1.18
 */
public final class RunSpans {
    private RunSpans() {
    }

    /**
     * Open the run-root span for a request and register its run info.
     *
     * @param info immutable run info describing the request
     * @return a handle ending the span and unregistering the run
     */
    public static RunSpan open(TrajectorySpanContext.RunInfo info) {
        Tracer tracer = TrajectoryObservability.active().getTracer();
        String sessionId = info.sessionId();
        Span span = tracer.spanBuilder(buildRunSpanName(info.mode(), sessionId))
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(TrajectorySemConv.OJ_AGENT_MODE, info.mode())
                .setAttribute(TrajectorySemConv.OJ_TRACE_ROOT, true)
                .setAttribute(TrajectorySemConv.GEN_AI_OPERATION_NAME,
                        TrajectorySemConv.GEN_AI_OPERATION_INVOKE_AGENT)
                .setAttribute(TrajectorySemConv.OJ_TRAJECTORY_RECORD_KIND,
                        TrajectorySemConv.RECORD_KIND_TURN)
                .setAttribute(TrajectorySemConv.GEN_AI_CONVERSATION_ID, sessionId)
                .setAttribute(TrajectorySemConv.OJ_REQUEST_ID, info.requestId())
                .setAttribute(TrajectorySemConv.OJ_RUN_ID, info.runId())
                .setAttribute(TrajectorySemConv.OJ_TURN_ID, info.turnId())
                .setAttribute(TrajectorySemConv.OJ_TURN_NUMBER, info.turnNumber())
                .setAttribute(TrajectorySemConv.OJ_REQUEST_NUMBER, info.requestNumber())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_ID, info.subjectId())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_DISPLAY_NAME,
                        info.subjectDisplayName())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_KIND, info.subjectKind())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_PARENT_ID,
                        info.subjectParentId())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_SESSION_ID,
                        info.subjectSessionId())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_REQUEST_NUMBER,
                        info.requestNumber())
                .setAttribute(TrajectorySemConv.OJ_REQUEST_PURPOSE,
                        TrajectorySemConv.REQUEST_PURPOSE_ASSISTANT)
                .setAttribute(TrajectorySemConv.OJ_TRAJECTORY_SCHEMA_VERSION,
                        TrajectorySemConv.SCHEMA_VERSION_VALUE)
                .startSpan();
        TrajectorySpanContext.registerRun(info);
        return new RunSpan(span, info);
    }

    /**
     * Build the stable run-root span name.
     *
     * @param mode swarm agent mode
     * @param sessionId session id
     * @return span name in the {@code agent.<mode>.<sessionId>} form
     */
    public static String buildRunSpanName(String mode, String sessionId) {
        return "agent." + mode + "." + sessionId;
    }

    /**
     * Handle owning one open run-root span.
     *
     * @since 0.1.18
     */
    public static final class RunSpan implements AutoCloseable {
        private final Span span;
        private final TrajectorySpanContext.RunInfo info;
        private volatile boolean isClosed;

        private RunSpan(Span span, TrajectorySpanContext.RunInfo info) {
            this.span = span;
            this.info = info;
        }

        /**
         * The underlying OTel span.
         *
         * @return the run-root span
         */
        public Span span() {
            return span;
        }

        /**
         * The immutable run info registered for this run.
         *
         * @return run info, never null
         */
        public TrajectorySpanContext.RunInfo info() {
            return info;
        }

        /**
         * Complete the run normally: stamp {@code openjiuwen.trace.complete},
         * end the span and unregister the run.
         */
        public void complete() {
            finish(true, null);
        }

        /**
         * Complete the run with an error status and message.
         *
         * @param error the terminal error, may be null
         */
        public void fail(Throwable error) {
            finish(false, error);
        }

        /**
         * Complete the run normally; equivalent to {@link #complete()}.
         */
        @Override
        public void close() {
            complete();
        }

        private synchronized void finish(boolean isSuccess, Throwable error) {
            if (isClosed) {
                return;
            }
            isClosed = true;
            if (isSuccess) {
                span.setAttribute(TrajectorySemConv.OJ_TRACE_COMPLETE, true);
            } else {
                span.setAttribute(TrajectorySemConv.OJ_TRACE_COMPLETE, false);
                if (error != null) {
                    span.recordException(error);
                }
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR,
                        error != null ? error.getMessage() : "run failed");
            }
            span.end();
            TrajectorySpanContext.unregisterRun(info.sessionId());
        }
    }
}
