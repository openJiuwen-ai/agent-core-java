/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Lightweight LLM resilience helpers for evolution flows.
 * <p>
 * Provides retry policies and invocation wrappers for evolution-layer LLM calls
 * with total budget control and usability validation.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.llm_resilience}.
 */
public final class LlmResilience {

    private LlmResilience() {
        // Utility class
    }

    /**
     * Policy for a single evolution-layer LLM invocation.
     */
    public static class LLMInvokePolicy {
        private final double attemptTimeoutSecs;
        private final double totalBudgetSecs;
        private final int maxAttempts;
        private final double backoffBaseSecs;
        private final boolean retryEmptyResponse;

        public LLMInvokePolicy(double attemptTimeoutSecs, double totalBudgetSecs, int maxAttempts,
                               double backoffBaseSecs, boolean retryEmptyResponse) {
            this.attemptTimeoutSecs = attemptTimeoutSecs;
            this.totalBudgetSecs = totalBudgetSecs;
            this.maxAttempts = maxAttempts;
            this.backoffBaseSecs = backoffBaseSecs;
            this.retryEmptyResponse = retryEmptyResponse;
        }

        public double getAttemptTimeoutSecs() {
            return attemptTimeoutSecs;
        }

        public double getTotalBudgetSecs() {
            return totalBudgetSecs;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public double getBackoffBaseSecs() {
            return backoffBaseSecs;
        }

        public boolean isRetryEmptyResponse() {
            return retryEmptyResponse;
        }

        /**
         * Create a default policy with common values.
         */
        public static LLMInvokePolicy defaultPolicy(double attemptTimeoutSecs, double totalBudgetSecs) {
            return new LLMInvokePolicy(attemptTimeoutSecs, totalBudgetSecs, 2, 1.0, true);
        }
    }

    /**
     * Result of invoke_text_with_retry_and_prompt containing raw text and the prompt used.
     */
    public static class InvokeResult {
        private final String raw;
        private final String promptUsed;

        public InvokeResult(String raw, String promptUsed) {
            this.raw = raw;
            this.promptUsed = promptUsed;
        }

        public String getRaw() {
            return raw;
        }

        public String getPromptUsed() {
            return promptUsed;
        }
    }

    /**
     * Context for tracking retry state.
     */
    private static class RetryContext {
        final long startedAt;
        final AtomicReference<Exception> lastError;
        final AtomicReference<String> lastResponse;
        final AtomicReference<Boolean> useRetryPrompt;

        RetryContext() {
            this.startedAt = System.nanoTime();
            this.lastError = new AtomicReference<>(null);
            this.lastResponse = new AtomicReference<>("");
            this.useRetryPrompt = new AtomicReference<>(false);
        }

        double elapsedSecs() {
            return (System.nanoTime() - startedAt) / 1_000_000_000.0;
        }
        
        Exception getLastError() {
            return lastError.get();
        }
        
        void setLastError(Exception e) {
            lastError.set(e);
        }
        
        String getLastResponse() {
            return lastResponse.get();
        }
        
        void setLastResponse(String s) {
            lastResponse.set(s);
        }
        
        boolean isUseRetryPrompt() {
            return useRetryPrompt.get();
        }
        
        void setUseRetryPrompt(boolean b) {
            useRetryPrompt.set(b);
        }
    }

    /**
     * Convert response to text, handling various response types.
     */
    private static String responseToText(Object response) {
        if (response == null) {
            return "";
        }
        if (response instanceof BaseMessage message) {
            Object content = message.getContent();
            return content != null ? String.valueOf(content) : "";
        }
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            Object content = map.get("content");
            if (content != null) {
                return String.valueOf(content);
            }
            Object text = map.get("text");
            if (text != null) {
                return String.valueOf(text);
            }
            return "";
        }
        return String.valueOf(response);
    }

    /**
     * Invoke Model with evolution-layer usability retry and total budget control.
     * Returns only the raw text.
     */
    public static String invokeTextWithRetry(
            Model llm,
            String model,
            String prompt,
            LLMInvokePolicy policy,
            String retryPrompt,
            Float temperature,
            Predicate<String> isResultUsable) throws Exception {
        InvokeResult result = invokeTextWithRetryAndPrompt(
                llm, model, prompt, policy, retryPrompt, temperature, isResultUsable);
        return result.getRaw();
    }

    /**
     * Invoke Model with evolution-layer usability retry and total budget control.
     * Returns raw text and the prompt that was actually used.
     */
    public static InvokeResult invokeTextWithRetryAndPrompt(
            Model llm,
            String model,
            String prompt,
            LLMInvokePolicy policy,
            String retryPrompt,
            Float temperature,
            Predicate<String> isResultUsable) throws Exception {

        if (policy.getTotalBudgetSecs() <= 0) {
            raiseLlmResilienceError(
                    StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR,
                    "total_budget_exceeded",
                    0,
                    null,
                    "");
        }

        RetryContext ctx = new RetryContext();

        for (int attempt = 1; attempt <= Math.max(policy.getMaxAttempts(), 1); attempt++) {
            double remainingBudget = policy.getTotalBudgetSecs() - ctx.elapsedSecs();
            if (remainingBudget <= 0) {
                raiseLlmResilienceError(
                        StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR,
                        "total_budget_exceeded",
                        attempt - 1,
ctx.getLastError(),
                    ctx.getLastResponse());
            }

            double timeoutSecs = Math.min(policy.getAttemptTimeoutSecs(), remainingBudget);
            String currentPrompt = (ctx.isUseRetryPrompt() && retryPrompt != null) ? retryPrompt : prompt;

            try {
                Object response = invokeWithTimeout(llm, model, currentPrompt, temperature, timeoutSecs);
                String raw = responseToText(response);
                ctx.setLastResponse(raw);

                // Handle empty response
                if (policy.isRetryEmptyResponse() && raw.strip().isEmpty()) {
                    if (attempt >= policy.getMaxAttempts()) {
                        raiseLlmResilienceError(
                                StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_OUTPUT_PARSE_ERROR,
                                "empty_response",
                                attempt,
                                ctx.getLastError(),
                                raw);
                    }
                    sleepBeforeRetry(policy, ctx, attempt);
                    continue;
                }

                // Check usability
                if (isResultUsable != null) {
                    boolean usable;
                    try {
                        usable = isResultUsable.test(raw);
                    } catch (Exception e) {
                        usable = false;
                        ctx.setLastError(e);
                    }

                    if (!usable) {
                        if (attempt >= policy.getMaxAttempts()) {
                            raiseLlmResilienceError(
                                    StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_OUTPUT_PARSE_ERROR,
                                    "unusable_response",
                                    attempt,
                                    ctx.getLastError(),
                                raw);
                        }
                        sleepBeforeRetry(policy, ctx, attempt);
                        continue;
                    }
                }

                return new InvokeResult(raw, currentPrompt);

            } catch (Exception exc) {
                ctx.setLastError(exc);
                if (ctx.elapsedSecs() >= policy.getTotalBudgetSecs()) {
                    raiseLlmResilienceError(
                            StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR,
                            "total_budget_exceeded",
                            attempt,
                            exc,
                            ctx.getLastResponse());
                }
                if (retryPrompt != null && attempt < policy.getMaxAttempts() && isTimeoutLike(exc)) {
                    ctx.setUseRetryPrompt(true);
                    Loggers.AGENT.info("[llm_resilience] attempt {} timed out; retrying with shorter prompt",
                            attempt, policy.getMaxAttempts());
                    sleepBeforeRetry(policy, ctx, attempt);
                    continue;
                }
                raiseLlmResilienceError(
                        StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR,
                        "invoke_failed",
                        attempt,
                        exc,
                        ctx.getLastResponse());
            }
        }

        // Should never reach here
        raiseLlmResilienceError(
                StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_OUTPUT_PARSE_ERROR,
                "unusable_response",
                policy.getMaxAttempts(),
                ctx.getLastError(),
                ctx.getLastResponse());
        return null;
    }

    private static Object invokeWithTimeout(
            Model llm,
            String model,
            String prompt,
            Float temperature,
            double timeoutSecs) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        Future<Object> future = executor.submit(() -> {
            started.countDown();
            return llm.invoke(
                    Collections.singletonList(new UserMessage(prompt)),
                    null,
                    temperature,
                    null,
                    model,
                    null,
                    null,
                    null,
                    (float) timeoutSecs,
                    null);
        });
        try {
            long timeoutMs = Math.max(1L, (long) Math.ceil(timeoutSecs * 1000.0));
            if (!started.await(1, TimeUnit.SECONDS)) {
                throw new TimeoutException("model invocation did not start");
            }
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(cause);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Check if an exception is timeout-like.
     */
    private static boolean isTimeoutLike(Exception exc) {
        if (exc instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        String typeName = exc.getClass().getSimpleName().toLowerCase();
        if (typeName.contains("timeout")) {
            return true;
        }
        String message = exc.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("timeout") || lowerMessage.contains("timed out");
        }
        return false;
    }

    /**
     * Sleep before retry with exponential backoff respecting remaining budget.
     */
    private static void sleepBeforeRetry(LLMInvokePolicy policy, RetryContext ctx, int attempt)
            throws InterruptedException {
        if (policy.getBackoffBaseSecs() <= 0) {
            return;
        }

        double remainingBudget = policy.getTotalBudgetSecs() - ctx.elapsedSecs();
        if (remainingBudget <= 0) {
            return;
        }

        double backoffSecs = policy.getBackoffBaseSecs() * Math.pow(2.0, Math.max(attempt - 1, 0));
        long sleepMs = (long) (Math.min(backoffSecs, remainingBudget) * 1000);
        Thread.sleep(sleepMs);
    }

    /**
     * Raise LLM resilience error with detailed context.
     */
    private static void raiseLlmResilienceError(
            StatusCode status,
            String reason,
            int attempts,
            Exception lastError,
            String lastResponse) {
        throw ErrorHelper.buildError(status,
                "error_msg", reason,
                "reason", reason,
                "attempts", String.valueOf(attempts),
                "last_response", lastResponse != null ? lastResponse : "",
                "last_error", lastError != null ? lastError.getMessage() : "");
    }
}
