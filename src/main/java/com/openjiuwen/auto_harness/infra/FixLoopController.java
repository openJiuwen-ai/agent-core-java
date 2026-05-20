/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

import java.util.concurrent.Callable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Public class FixLoopController used by the Java parity implementation.
 *
 * @since 1.0
 */
public class FixLoopController {
    private final int phase1MaxRetries;
    private final int phase2MaxRetries;
    private final double timeoutPerAttemptSecs;

    /**
     * Auto-generated for codecheck compliance.
     */
    public FixLoopController() {
        this(10, 9, 600.0);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public FixLoopController(int phase1MaxRetries, int phase2MaxRetries) {
        this(phase1MaxRetries, phase2MaxRetries, 600.0);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public FixLoopController(int phase1MaxRetries, int phase2MaxRetries, double timeoutPerAttemptSecs) {
        this.phase1MaxRetries = phase1MaxRetries;
        this.phase2MaxRetries = phase2MaxRetries;
        this.timeoutPerAttemptSecs = timeoutPerAttemptSecs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public FixLoopResult run(Callable<SimpleCheckResult> ciRunner,
                             Callable<Void> agentFixer,
                             Callable<SimpleApprovalResult> evaluator) throws Exception {
        return run(ciRunner, errors -> agentFixer.call(), evaluator);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public FixLoopResult run(Callable<SimpleCheckResult> ciRunner,
                             AgentFixer agentFixer,
                             Callable<SimpleApprovalResult> evaluator) throws Exception {
        FixLoopResult result = FixLoopResult.builder().build();
        for (int i = 1; i <= phase1MaxRetries; i++) {
            result.setAttempts(i);
            result.setPhase(1);
            SimpleCheckResult ci;
            try {
                ci = callWithTimeout(ciRunner);
            } catch (TimeoutException ex) {
                result.getErrorLog().add("Phase 1 attempt " + i + ": CI timeout");
                continue;
            }
            if (ci.isPassed()) {
                result.setSuccess(true);
                return result;
            }
            String errors = hasText(ci.errors()) ? ci.errors() : "unknown error";
            result.getErrorLog().add("Phase 1 attempt " + i + ": " + truncate(errors, 200));
            try {
                runWithTimeout(() -> {
                    agentFixer.fix(errors);
                    return null;
                });
            } catch (TimeoutException ex) {
                result.getErrorLog().add("Phase 1 attempt " + i + ": fixer timeout");
            }
        }
        if (evaluator == null) {
            return result;
        }
        for (int j = 1; j <= phase2MaxRetries; j++) {
            result.setAttempts(result.getAttempts() + 1);
            result.setPhase(2);
            SimpleApprovalResult review;
            try {
                review = callWithTimeout(evaluator);
            } catch (TimeoutException ex) {
                result.getErrorLog().add("Phase 2 attempt " + j + ": evaluator timeout");
                continue;
            }
            if (review.isApproved()) {
                result.setSuccess(true);
                return result;
            }
            result.getErrorLog().add("Phase 2 attempt " + j + ": evaluator rejected");
            try {
                runWithTimeout(() -> {
                    agentFixer.fix("evaluator rejected");
                    return null;
                });
            } catch (TimeoutException ex) {
                result.getErrorLog().add("Phase 2 attempt " + j + ": fixer timeout");
            }
        }
        return result;
    }

    private <T> T callWithTimeout(Callable<T> callable) throws Exception {
        if (timeoutPerAttemptSecs <= 0) {
            throw new TimeoutException("timed out");
        }
        ExecutorService executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1)
        );
        Future<T> future = executor.submit(callable);
        try {
            return future.get(timeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw ex;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException(cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private void runWithTimeout(Callable<Void> callable) throws Exception {
        callWithTimeout(callable);
    }

    private long timeoutMillis() {
        return Math.max(1L, (long) Math.ceil(timeoutPerAttemptSecs * 1000.0));
    }

    private static String truncate(String text, int limit) {
        String value = text == null ? "" : text;
        if (limit < 0 || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
 * Public interface AgentFixer used by the Java parity implementation.
 *
 * @since 1.0
 */
    @FunctionalInterface
public interface AgentFixer {
        void fix(String errors) throws Exception;
    }

    /**
 * Public record SimpleCheckResult used by the Java parity implementation.
 *
 * @since 1.0
 */
public record SimpleCheckResult(boolean isPassed, String errors) {}
    /**
 * Public record SimpleApprovalResult used by the Java parity implementation.
 *
 * @since 1.0
 */
public record SimpleApprovalResult(boolean isApproved) {}
}
