/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Lightweight LLM resilience helpers for evolution flows.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.llm_resilience} in
 * {@code openjiuwen/agent_evolving/optimizer/llm_resilience.py}.</p>
 */
public final class LlmResilience {

    private LlmResilience() {
    }

    /**
     * Policy for a single evolution-layer LLM invocation.
     *
     * <p>Mirrors Python's {@code LLMInvokePolicy} in
     * {@code openjiuwen/agent_evolving/optimizer/llm_resilience.py}.</p>
     */
    public record LLMInvokePolicy(
            double attemptTimeoutSecs,
            double totalBudgetSecs,
            int maxAttempts,
            double backoffBaseSecs,
            boolean retryEmptyResponse) {

        public static LLMInvokePolicy defaultPolicy(double attemptTimeoutSecs, double totalBudgetSecs) {
            return new LLMInvokePolicy(attemptTimeoutSecs, totalBudgetSecs, 2, 1.0, true);
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
    }

    /**
     * Return value for {@link #invokeTextWithRetryAndPrompt}.
     */
    public record InvokeResult(String raw, String promptUsed) {

        public String getRaw() {
            return raw;
        }

        public String getPromptUsed() {
            return promptUsed;
        }
    }

    private static final class RetryContext {
        private final long startedAtNanos;
        private Exception lastError;
        private String lastResponse = "";
        private boolean useRetryPrompt;

        private RetryContext() {
            this.startedAtNanos = System.nanoTime();
        }

        private double elapsedSecs() {
            return (System.nanoTime() - startedAtNanos) / 1_000_000_000.0;
        }
    }

    public static String invokeTextWithRetry(
            Model llm,
            String model,
            String prompt,
            LLMInvokePolicy policy,
            String retryPrompt,
            Float temperature,
            Predicate<String> isResultUsable) throws Exception {
        return invokeTextWithRetry(
                llm, model, prompt, policy, retryPrompt, temperature, isResultUsable, Map.of());
    }

    public static String invokeTextWithRetry(
            Model llm,
            String model,
            String prompt,
            LLMInvokePolicy policy,
            String retryPrompt,
            Float temperature,
            Predicate<String> isResultUsable,
            Map<String, Object> extraFields) throws Exception {
        InvokeResult result = invokeTextWithRetryAndPrompt(
                llm, model, prompt, policy, retryPrompt, temperature, isResultUsable, extraFields);
        return result.raw();
    }

    public static InvokeResult invokeTextWithRetryAndPrompt(
            Model llm,
            String model,
            String prompt,
            LLMInvokePolicy policy,
            String retryPrompt,
            Float temperature,
            Predicate<String> isResultUsable) throws Exception {
        return invokeTextWithRetryAndPrompt(
                llm, model, prompt, policy, retryPrompt, temperature, isResultUsable, Map.of());
    }

    public static InvokeResult invokeTextWithRetryAndPrompt(
            Model llm,
            String model,
            String prompt,
            LLMInvokePolicy policy,
            String retryPrompt,
            Float temperature,
            Predicate<String> isResultUsable,
            Map<String, Object> extraFields) throws Exception {
        LLMInvokePolicy resolvedPolicy = policy != null ? policy : LLMInvokePolicy.defaultPolicy(30.0, 60.0);
        if (resolvedPolicy.totalBudgetSecs() <= 0) {
            raiseLlmResilienceError(
                    StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR,
                    "total_budget_exceeded",
                    0,
                    null,
                    "",
                    null);
        }

        RetryContext context = new RetryContext();
        int maxAttempts = Math.max(resolvedPolicy.maxAttempts(), 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            double remainingBudget = resolvedPolicy.totalBudgetSecs() - context.elapsedSecs();
            if (remainingBudget <= 0) {
                raiseLlmResilienceError(
                        StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR,
                        "total_budget_exceeded",
                        attempt - 1,
                        context.lastError,
                        context.lastResponse,
                        context.lastError);
            }

            double timeoutSecs = Math.min(resolvedPolicy.attemptTimeoutSecs(), remainingBudget);
            String currentPrompt = context.useRetryPrompt && retryPrompt != null ? retryPrompt : prompt;
            try {
                AssistantMessage response = invokeWithTimeout(
                        llm, model, currentPrompt, temperature, timeoutSecs, extraFields);
                String raw = responseToText(response);
                context.lastResponse = raw;

                if (resolvedPolicy.retryEmptyResponse() && raw.strip().isEmpty()) {
                    if (attempt >= maxAttempts) {
                        raiseLlmResilienceError(
                                StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_OUTPUT_PARSE_ERROR,
                                "empty_response",
                                attempt,
                                context.lastError,
                                raw,
                                context.lastError);
                    }
                    Loggers.AGENT.warning(
                            "[llm_resilience] empty LLM response; retrying: model={} attempt={}/{}",
                            model,
                            attempt,
                            maxAttempts);
                    sleepBeforeRetry(resolvedPolicy, context, attempt);
                    continue;
                }

                if (isResultUsable != null && !isUsable(isResultUsable, raw, context)) {
                    if (attempt >= maxAttempts) {
                        raiseLlmResilienceError(
                                StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_OUTPUT_PARSE_ERROR,
                                "unusable_response",
                                attempt,
                                context.lastError,
                                raw,
                                context.lastError);
                    }
                    Loggers.AGENT.warning(
                            "[llm_resilience] unusable LLM response; retrying: model={} attempt={}/{} response_chars={}",
                            model,
                            attempt,
                            maxAttempts,
                            raw.length());
                    sleepBeforeRetry(resolvedPolicy, context, attempt);
                    continue;
                }

                return new InvokeResult(raw, currentPrompt);
            } catch (Exception exc) {
                context.lastError = exc;
                if (context.elapsedSecs() >= resolvedPolicy.totalBudgetSecs()) {
                    Loggers.AGENT.warning(
                            "[llm_resilience] LLM total budget exceeded: model={} attempts_started={} "
                                    + "prompt_chars={} total_budget={}s elapsed={}s last_error={}",
                            model,
                            attempt,
                            prompt != null ? prompt.length() : 0,
                            resolvedPolicy.totalBudgetSecs(),
                            context.elapsedSecs(),
                            errorText(context.lastError));
                    raiseLlmResilienceError(
                            StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR,
                            "total_budget_exceeded",
                            attempt,
                            exc,
                            context.lastResponse,
                            exc);
                }
                Loggers.AGENT.warning(
                        "[llm_resilience] LLM attempt failed: model={} attempt={}/{} prompt_chars={} timeout={}s "
                                + "timeout_like={} error={}",
                        model,
                        attempt,
                        maxAttempts,
                        currentPrompt != null ? currentPrompt.length() : 0,
                        timeoutSecs,
                        isTimeoutLike(exc),
                        errorText(exc));
                if (retryPrompt != null && attempt < maxAttempts && isTimeoutLike(exc)) {
                    context.useRetryPrompt = true;
                    Loggers.AGENT.info(
                            "[llm_resilience] attempt {}/{} timed out; retrying with shorter prompt",
                            attempt,
                            maxAttempts);
                    sleepBeforeRetry(resolvedPolicy, context, attempt);
                    continue;
                }
                raiseLlmResilienceError(
                        StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR,
                        "invoke_failed",
                        attempt,
                        exc,
                        context.lastResponse,
                        exc);
            }
        }

        raiseLlmResilienceError(
                StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_OUTPUT_PARSE_ERROR,
                "unusable_response",
                maxAttempts,
                context.lastError,
                context.lastResponse,
                context.lastError);
        return null;
    }

    private static String responseToText(Object response) {
        if (response == null) {
            return "";
        }
        if (response instanceof BaseMessage message) {
            Object content = message.getContent();
            return content != null ? String.valueOf(content) : "";
        }
        if (response instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null) {
                return String.valueOf(content);
            }
            Object text = map.get("text");
            return text != null ? String.valueOf(text) : "";
        }
        return String.valueOf(response);
    }

    private static AssistantMessage invokeWithTimeout(
            Model llm,
            String model,
            String prompt,
            Float temperature,
            double timeoutSecs,
            Map<String, Object> extraFields) throws Exception {
        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .model(model)
                .temperature(temperature)
                .timeout((float) timeoutSecs)
                .extraFields(extraFields != null ? new LinkedHashMap<>(extraFields) : new LinkedHashMap<>())
                .build();
        try {
            long timeoutMillis = Math.max(1L, (long) Math.ceil(timeoutSecs * 1000.0));
            return llm.invoke(List.of(new UserMessage(prompt)), options)
                    .toCompletableFuture()
                    .get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exc) {
            throw exc;
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            throw exc;
        } catch (ExecutionException exc) {
            throw unwrapExecutionException(exc);
        } catch (CompletionException exc) {
            throw unwrapCompletionException(exc);
        }
    }

    private static boolean isUsable(Predicate<String> isResultUsable, String raw, RetryContext context) {
        try {
            return isResultUsable.test(raw);
        } catch (Exception exc) {
            context.lastError = exc;
            return false;
        }
    }

    private static Exception unwrapExecutionException(ExecutionException exc) {
        Throwable cause = exc.getCause();
        if (cause instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(cause);
    }

    private static Exception unwrapCompletionException(CompletionException exc) {
        Throwable cause = exc.getCause();
        if (cause instanceof Exception exception) {
            return exception;
        }
        return exc;
    }

    private static boolean isTimeoutLike(Exception exc) {
        if (exc instanceof TimeoutException) {
            return true;
        }
        String typeName = exc.getClass().getSimpleName().toLowerCase();
        if (typeName.contains("timeout")) {
            return true;
        }
        String message = exc.getMessage();
        if (message == null) {
            return false;
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("timeout") || lowerMessage.contains("timed out");
    }

    private static void sleepBeforeRetry(LLMInvokePolicy policy, RetryContext context, int attempt)
            throws InterruptedException {
        if (policy.backoffBaseSecs() <= 0) {
            return;
        }
        double remainingBudget = policy.totalBudgetSecs() - context.elapsedSecs();
        if (remainingBudget <= 0) {
            return;
        }
        double backoffSecs = policy.backoffBaseSecs() * Math.pow(2.0, Math.max(attempt - 1, 0));
        long sleepMillis = (long) (Math.min(backoffSecs, remainingBudget) * 1000);
        Thread.sleep(Math.max(0L, sleepMillis));
    }

    private static void raiseLlmResilienceError(
            StatusCode status,
            String reason,
            int attempts,
            Exception lastError,
            String lastResponse,
            Throwable cause) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("attempts", attempts);
        details.put("last_response", lastResponse != null ? lastResponse : "");
        details.put("last_error", lastError != null ? errorText(lastError) : "");

        Map<String, Object> params = new LinkedHashMap<>(details);
        params.put("error_msg", reason);
        BaseError error = ErrorHelper.buildError(status, null, details, cause, params);
        throw error;
    }

    private static String errorText(Exception error) {
        String message = error.getMessage();
        return message != null ? message : error.getClass().getSimpleName();
    }
}
