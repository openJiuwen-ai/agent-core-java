/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Mirrors Python's {@code CallbackChain} in
 * {@code openjiuwen/core/runner/callback/chain.py}.
 */
public class CallbackChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(CallbackChain.class);

    private final String name;

    private final List<CallbackInfo> callbacks = new ArrayList<>();

    private final Map<Function<Map<String, Object>, Object>, Function<ChainContext, Object>> rollbackHandlers =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private final Map<Function<Map<String, Object>, Object>, BiFunction<Exception, ChainContext, Object>> errorHandlers =
            Collections.synchronizedMap(new IdentityHashMap<>());

    public CallbackChain() {
        this("");
    }

    public CallbackChain(String name) {
        this.name = name != null ? name : "";
    }

    public String getName() {
        return name;
    }

    public List<CallbackInfo> getCallbacks() {
        return callbacks;
    }

    public Map<Function<Map<String, Object>, Object>, Function<ChainContext, Object>> getRollbackHandlers() {
        return rollbackHandlers;
    }

    public Map<Function<Map<String, Object>, Object>, BiFunction<Exception, ChainContext, Object>> getErrorHandlers() {
        return errorHandlers;
    }

    public boolean hasRollbackHandler(Function<Map<String, Object>, Object> callback) {
        return rollbackHandlers.containsKey(callback);
    }

    public boolean hasErrorHandler(Function<Map<String, Object>, Object> callback) {
        return errorHandlers.containsKey(callback);
    }

    public void add(CallbackInfo callbackInfo) {
        add(callbackInfo, null, null);
    }

    public void add(CallbackInfo callbackInfo, Function<ChainContext, Object> rollbackHandler) {
        add(callbackInfo, rollbackHandler, null);
    }

    public void add(
            CallbackInfo callbackInfo,
            Function<ChainContext, Object> rollbackHandler,
            BiFunction<Exception, ChainContext, Object> errorHandler
    ) {
        Objects.requireNonNull(callbackInfo, "callbackInfo");
        callbacks.add(callbackInfo);
        callbacks.sort((left, right) -> Integer.compare(right.getPriority(), left.getPriority()));

        if (rollbackHandler != null) {
            rollbackHandlers.put(callbackInfo.getCallback(), rollbackHandler);
        }
        if (errorHandler != null) {
            errorHandlers.put(callbackInfo.getCallback(), errorHandler);
        }
    }

    public void remove(Function<Map<String, Object>, Object> callback) {
        callbacks.removeIf(info -> info.getCallback() == callback);
        rollbackHandlers.remove(callback);
        errorHandlers.remove(callback);
    }

    public CompletableFuture<ChainResult> execute(ChainContext context) {
        return CompletableFuture.supplyAsync(() -> executeInternal(context));
    }

    private ChainResult executeInternal(ChainContext context) {
        List<Function<Map<String, Object>, Object>> executedCallbacks = new ArrayList<>();

        for (int index = 0; index < callbacks.size(); index++) {
            CallbackInfo callbackInfo = callbacks.get(index);
            if (!callbackInfo.isEnabled()) {
                continue;
            }

            context.setCurrentIndex(index);
            Function<Map<String, Object>, Object> callback = callbackInfo.getCallback();

            boolean recoveredByErrorHandler = false;
            boolean completedNormally = false;

            for (int attempt = 0; attempt <= callbackInfo.getMaxRetries(); attempt++) {
                try {
                    Map<String, Object> kwargs = buildInvocationKwargs(context);
                    Object resolvedResult = invokeCallback(callback, kwargs, callbackInfo.getTimeout());

                    ProcessOutcome outcome = processResult(resolvedResult, callbackInfo, executedCallbacks, context);
                    if (outcome.retryCurrent()) {
                        continue;
                    }
                    if (outcome.terminalResult() != null) {
                        return outcome.terminalResult();
                    }

                    executedCallbacks.add(callback);
                    completedNormally = true;
                    break;
                } catch (TimeoutException timeoutError) {
                    LOGGER.error("Callback {} timed out", describeCallback(callback));
                    if (attempt < callbackInfo.getMaxRetries()) {
                        sleepRetryDelay(callbackInfo.getRetryDelay());
                        continue;
                    }
                    rollback(executedCallbacks, context);
                    return ChainResult.builder()
                            .action(ChainAction.ROLLBACK)
                            .context(context)
                            .error(new TimeoutException("Callback timeout"))
                            .build();
                } catch (Exception error) {
                    Exception normalizedError = normalizeException(error);

                    if (errorHandlers.containsKey(callback)) {
                        try {
                            Object errorResult = errorHandlers.get(callback).apply(normalizedError, context);
                            Object resolvedErrorResult = awaitResult(errorResult, null);
                            if (isTruthy(resolvedErrorResult)) {
                                context.getResults().add(resolvedErrorResult);
                                executedCallbacks.add(callback);
                                recoveredByErrorHandler = true;
                                break;
                            }
                        } catch (Exception handlerError) {
                            LOGGER.error("Error handler failed", handlerError);
                        }
                    }

                    if (attempt < callbackInfo.getMaxRetries()) {
                        LOGGER.info("Retrying {} (attempt {})", describeCallback(callback), attempt + 1);
                        sleepRetryDelay(callbackInfo.getRetryDelay());
                        continue;
                    }

                    rollback(executedCallbacks, context);
                    return ChainResult.builder()
                            .action(ChainAction.ROLLBACK)
                            .context(context)
                            .error(normalizedError)
                            .build();
                }
            }

            if (completedNormally && callbackInfo.isOnce()) {
                callbackInfo.setEnabled(false);
            }

            if (recoveredByErrorHandler) {
                continue;
            }
        }

        context.setCompleted(true);
        return ChainResult.builder()
                .action(ChainAction.CONTINUE)
                .result(context.getLastResult())
                .context(context)
                .build();
    }

    private Map<String, Object> buildInvocationKwargs(ChainContext context) {
        Map<String, Object> kwargs = new LinkedHashMap<>(context.getInitialKwargs());
        kwargs.put("_chain_context", context);
        kwargs.put("_initial_args", Arrays.copyOf(context.getInitialArgs(), context.getInitialArgs().length));

        Object[] invocationArgs = context.getInitialArgs();
        if (!context.getResults().isEmpty()) {
            invocationArgs = prependLastResult(context.getLastResult(), context.getInitialArgs());
            kwargs.put("_last_result", context.getLastResult());
        }
        kwargs.put("_args", invocationArgs);
        return kwargs;
    }

    private static Object[] prependLastResult(Object lastResult, Object[] initialArgs) {
        Object[] combined = new Object[initialArgs.length + 1];
        combined[0] = lastResult;
        System.arraycopy(initialArgs, 0, combined, 1, initialArgs.length);
        return combined;
    }

    private ProcessOutcome processResult(
            Object resolvedResult,
            CallbackInfo callbackInfo,
            List<Function<Map<String, Object>, Object>> executedCallbacks,
            ChainContext context
    ) {
        if (!(resolvedResult instanceof ChainResult chainResult)) {
            context.getResults().add(resolvedResult);
            return new ProcessOutcome(false, null);
        }

        if (chainResult.getAction() == ChainAction.BREAK) {
            context.getResults().add(chainResult.getResult());
            return new ProcessOutcome(false, ChainResult.builder()
                    .action(ChainAction.BREAK)
                    .result(chainResult.getResult())
                    .context(context)
                    .build());
        }
        if (chainResult.getAction() == ChainAction.RETRY) {
            return new ProcessOutcome(true, null);
        }
        if (chainResult.getAction() == ChainAction.ROLLBACK) {
            Function<Map<String, Object>, Object> callback = callbackInfo.getCallback();
            rollback(executedCallbacks, context);
            return new ProcessOutcome(false, ChainResult.builder()
                    .action(ChainAction.ROLLBACK)
                    .context(context)
                    .error(chainResult.getError())
                .build());
        }

        Function<Map<String, Object>, Object> callback = callbackInfo.getCallback();
        if (shouldRecordChainResult(callbackInfo, callback)) {
            context.getResults().add(chainResult.getResult());
        }
        return new ProcessOutcome(false, null);
    }

    private boolean shouldRecordChainResult(
            CallbackInfo callbackInfo,
            Function<Map<String, Object>, Object> callback
    ) {
        if (callbackInfo.getCallbackName() == null || callbackInfo.getCallbackName().isEmpty()) {
            return true;
        }
        return hasRollbackHandler(callback) || hasErrorHandler(callback);
    }

    private void rollback(List<Function<Map<String, Object>, Object>> executedCallbacks, ChainContext context) {
        context.setRolledBack(true);

        for (int index = executedCallbacks.size() - 1; index >= 0; index--) {
            Function<Map<String, Object>, Object> callback = executedCallbacks.get(index);
            Function<ChainContext, Object> rollbackHandler = rollbackHandlers.get(callback);
            if (rollbackHandler == null) {
                continue;
            }

            try {
                awaitResult(rollbackHandler.apply(context), null);
            } catch (Exception rollbackError) {
                LOGGER.error("Rollback failed for {}", describeCallback(callback), rollbackError);
            }
        }
    }

    private static Object awaitResult(Object value, Double timeoutSeconds) throws Exception {
        CompletableFuture<Object> future = toCompletableFuture(value);
        try {
            if (timeoutSeconds != null && timeoutSeconds > 0) {
                return future.get((long) (timeoutSeconds * 1000L), TimeUnit.MILLISECONDS);
            }
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Callback execution interrupted", interrupted);
        } catch (ExecutionException executionError) {
            throw normalizeException(executionError);
        } catch (CompletionException completionError) {
            throw normalizeException(completionError);
        }
    }

    private static Object invokeCallback(
            Function<Map<String, Object>, Object> callback,
            Map<String, Object> kwargs,
            Double timeoutSeconds
    ) throws Exception {
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return awaitResult(callback.apply(kwargs), null);
        }
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<Object> future = executor.submit(() -> awaitResult(callback.apply(kwargs), null));
        try {
            return future.get((long) (timeoutSeconds * 1000L), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutError) {
            future.cancel(true);
            throw timeoutError;
        } finally {
            executor.shutdownNow();
        }
    }

    private static CompletableFuture<Object> toCompletableFuture(Object value) {
        if (!(value instanceof CompletionStage<?> stage)) {
            return CompletableFuture.completedFuture(value);
        }

        CompletableFuture<Object> future = new CompletableFuture<>();
        stage.whenComplete((resolvedValue, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
                return;
            }
            future.complete(resolvedValue);
        });
        return future;
    }

    private static Exception normalizeException(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }

        if (current instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(current);
    }

    private static void sleepRetryDelay(double retryDelaySeconds) {
        if (retryDelaySeconds <= 0) {
            return;
        }
        try {
            Thread.sleep((long) (retryDelaySeconds * 1000L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence charSequence) {
            return charSequence.length() > 0;
        }
        if (value instanceof Map<?, ?> mapValue) {
            return !mapValue.isEmpty();
        }
        if (value instanceof Iterable<?> iterableValue) {
            return iterableValue.iterator().hasNext();
        }
        if (value instanceof Optional<?> optionalValue) {
            return optionalValue.isPresent();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }

    private static String describeCallback(Function<Map<String, Object>, Object> callback) {
        return callback == null ? "<null-callback>" : callback.toString();
    }

    private record ProcessOutcome(boolean retryCurrent, ChainResult terminalResult) {
    }
}
