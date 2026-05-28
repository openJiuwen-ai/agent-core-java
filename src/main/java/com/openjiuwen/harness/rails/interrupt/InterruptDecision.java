/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import java.util.Optional;

/**
 * Base decision types for interrupt-resume handling.
 * <p>
 * Mirrors Python's {@code InterruptDecision / ApproveResult / RejectResult / InterruptResult}
 * in {@code openjiuwen.harness.rails.interrupt.interrupt_base}.
 */
public abstract class InterruptDecision {

    private InterruptDecision() {
    }

    /** Whether this decision approves the pending tool call. */
    public boolean isApproved() {
        return this instanceof ApproveResult;
    }

    /** Whether this decision rejects the pending tool call. */
    public boolean isRejected() {
        return this instanceof RejectResult;
    }

    /** Whether this decision requests an interrupt. */
    public boolean isInterrupted() {
        return this instanceof InterruptResult;
    }

    // ── Approve ──────────────────────────────────────────────────────

    /** Decision to continue tool execution. */
    public static final class ApproveResult extends InterruptDecision {
        private final String newArgs;

        private ApproveResult(String newArgs) {
            this.newArgs = newArgs;
        }

        public Optional<String> getNewArgs() {
            return Optional.ofNullable(newArgs);
        }
    }

    // ── Reject ───────────────────────────────────────────────────────

    /** Decision to reject tool execution. */
    public static final class RejectResult extends InterruptDecision {
        private final Object toolResult;
        private final Object toolMessage;

        private RejectResult(Object toolResult, Object toolMessage) {
            this.toolResult = toolResult;
            this.toolMessage = toolMessage;
        }

        public Optional<Object> getToolResult() {
            return Optional.ofNullable(toolResult);
        }

        public Optional<Object> getToolMessage() {
            return Optional.ofNullable(toolMessage);
        }
    }

    // ── Interrupt ────────────────────────────────────────────────────

    /** Decision to interrupt and wait for user input. */
    public static final class InterruptResult extends InterruptDecision {
        private final Object request;

        private InterruptResult(Object request) {
            this.request = request;
        }

        public Object getRequest() {
            return request;
        }
    }

    // ── Factory methods ──────────────────────────────────────────────

    /** Create an approve decision to continue execution. */
    public static ApproveResult approve() {
        return approve(null);
    }

    /** Create an approve decision with optional modified args. */
    public static ApproveResult approve(String newArgs) {
        return new ApproveResult(newArgs);
    }

    /** Create a reject decision to skip tool execution. */
    public static RejectResult reject() {
        return reject(null, null);
    }

    /** Create a reject decision with a tool result. */
    public static RejectResult reject(Object toolResult) {
        return reject(toolResult, null);
    }

    /** Create a reject decision with a tool result and message. */
    public static RejectResult reject(Object toolResult, Object toolMessage) {
        return new RejectResult(toolResult, toolMessage);
    }

    /** Create an interrupt decision to wait for user input. */
    public static InterruptResult interrupt(Object request) {
        return new InterruptResult(request);
    }
}
