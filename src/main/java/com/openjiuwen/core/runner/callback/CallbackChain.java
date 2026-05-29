/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Manages sequential execution of callbacks with rollback support.
 * <p>
 * Provides ordered execution, error handling, and rollback capabilities
 * for groups of related callbacks.
 */
public class CallbackChain {

    private static final Logger logger = LoggerFactory.getLogger(CallbackChain.class);

    private final String name;
    private final List<CallbackInfo> callbacks = new ArrayList<>();
    private final Map<Function<Map<String, Object>, Object>, Consumer<ChainContext>> rollbackHandlers = new HashMap<>();
    private final Map<Function<Map<String, Object>, Object>, Function<ExceptionContext, Object>> errorHandlers = new HashMap<>();

    /**
     * Context isPassed to error handlers: the exception + the chain context.
     */
    public record ExceptionContext(Exception exception, ChainContext chainContext) {}

    /**
     * Auto-generated for codecheck compliance.
     */
    public CallbackChain(String name) {
        this.name = name != null ? name : "";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<CallbackInfo> getCallbacks() {
        return callbacks;
    }

    /**
     * Add callback to the chain.
     *
     * @param callbackInfo    Callback metadata and configuration
     * @param rollbackHandler Optional function to call on rollback
     * @param errorHandler    Optional function to call on error
     */
    public void add(CallbackInfo callbackInfo,
                    Consumer<ChainContext> rollbackHandler,
                    Function<ExceptionContext, Object> errorHandler) {
        callbacks.add(callbackInfo);
        callbacks.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        if (rollbackHandler != null) {
            rollbackHandlers.put(callbackInfo.getCallback(), rollbackHandler);
        }
        if (errorHandler != null) {
            errorHandlers.put(callbackInfo.getCallback(), errorHandler);
        }
    }

    /**
     * Remove callback from the chain.
     *
     * @param callback Callback function to remove
     */
    public void remove(Function<Map<String, Object>, Object> callback) {
        callbacks.removeIf(ci -> ci.getCallback() == callback);
        rollbackHandlers.remove(callback);
        errorHandlers.remove(callback);
    }

    /**
     * Execute the callback chain.
     * <p>
     * Executes callbacks in priority order, passing results between them.
     * Supports retry logic, error handling, and rollback on failure.
     *
     * @param context Chain execution context
     * @return ChainResult with execution outcome
     */
    public ChainResult execute(ChainContext context) {
        List<Function<Map<String, Object>, Object>> executedCallbacks = new ArrayList<>();

        for (int i = 0; i < callbacks.size(); i++) {
            CallbackInfo callbackInfo = callbacks.get(i);
            if (!callbackInfo.isEnabled()) {
                continue;
            }

            context.setCurrentIndex(i);
            Function<Map<String, Object>, Object> callback = callbackInfo.getCallback();

            // Retry loop
            for (int attempt = 0; attempt <= callbackInfo.getMaxRetries(); attempt++) {
                try {
                    // Prepare arguments - chain previous result
                    Map<String, Object> kwargs = new HashMap<>(context.getInitialKwargs());
                    kwargs.put("_chain_context", context);

                    if (!context.getResults().isEmpty()) {
                        kwargs.put("_last_result", context.getLastResult());
                    }
                    kwargs.put("_args", context.getInitialArgs());

                    // Execute with timeout if specified
                    Object result;
                    if (callbackInfo.getTimeout() != null && callbackInfo.getTimeout() > 0) {
                        result = executeWithTimeout(callback, kwargs, callbackInfo.getTimeout());
                    } else {
                        result = callback.apply(kwargs);
                    }

                    // Process result
                    if (result instanceof ChainResult chainResult) {
                        if (chainResult.getAction() == ChainAction.BREAK) {
                            context.getResults().add(chainResult.getResult());
                            mergeResultToContext(context, chainResult.getResult());
                            return ChainResult.builder()
                                    .action(ChainAction.BREAK)
                                    .result(chainResult.getResult())
                                    .context(context)
                                    .build();
                        } else if (chainResult.getAction() == ChainAction.RETRY) {
                            continue;
                        } else if (chainResult.getAction() == ChainAction.ROLLBACK) {
                            rollback(executedCallbacks, context);
                            return ChainResult.builder()
                                    .action(ChainAction.ROLLBACK)
                                    .context(context)
                                    .error(chainResult.getError())
                                    .build();
                        } else {
                            context.getResults().add(chainResult.getResult());
                            mergeResultToContext(context, chainResult.getResult());
                        }
                    } else {
                        context.getResults().add(result);
                        mergeResultToContext(context, result);
                    }

                    executedCallbacks.add(callback);

                    // Handle once-only callbacks
                    if (callbackInfo.isOnce()) {
                        callbackInfo.setEnabled(false);
                    }

                    break; // Success, exit retry loop

                } catch (TimeoutException e) {
                    logger.error("Callback {} timed out", callbackInfo.getCallbackDisplayName());
                    if (attempt < callbackInfo.getMaxRetries()) {
                        sleepRetryDelay(callbackInfo.getRetryDelay());
                        continue;
                    } else {
                        rollback(executedCallbacks, context);
                        return ChainResult.builder()
                                .action(ChainAction.ROLLBACK)
                                .context(context)
                                .error(new TimeoutException("Callback timeout"))
                                .build();
                    }
                } catch (Exception e) {
                    // Try error handler
                    if (errorHandlers.containsKey(callback)) {
                        try {
                            Object errorResult = errorHandlers.get(callback)
                                    .apply(new ExceptionContext(e, context));
                            if (errorResult != null) {
                                context.getResults().add(errorResult);
                                executedCallbacks.add(callback);
                                break;
                            }
                        } catch (Exception handlerError) {
                            logger.error("Error handler failed: {}", handlerError.getMessage());
                        }
                    }

                    // Retry if attempts remaining
                    if (attempt < callbackInfo.getMaxRetries()) {
                        logger.info("Retrying {} (attempt {})",
                                callbackInfo.getCallbackDisplayName(), attempt + 1);
                        sleepRetryDelay(callbackInfo.getRetryDelay());
                        continue;
                    }

                    // Rollback on final failure
                    rollback(executedCallbacks, context);
                    return ChainResult.builder()
                            .action(ChainAction.ROLLBACK)
                            .context(context)
                            .error(e)
                            .build();
                }
            }
        }

        context.setCompleted(true);
        return ChainResult.builder()
                .action(ChainAction.CONTINUE)
                .result(context.getLastResult())
                .context(context)
                .build();
    }

    /**
     * Execute rollback handlers for executed callbacks in reverse order.
     */
    private void rollback(List<Function<Map<String, Object>, Object>> executedCallbacks,
                          ChainContext context) {
        context.setRolledBack(true);

        for (int i = executedCallbacks.size() - 1; i >= 0; i--) {
            Function<Map<String, Object>, Object> cb = executedCallbacks.get(i);
            Consumer<ChainContext> rollbackHandler = rollbackHandlers.get(cb);
            if (rollbackHandler != null) {
                try {
                    rollbackHandler.accept(context);
                } catch (Exception e) {
                    logger.error("Rollback failed for callback: {}", e.getMessage());
                }
            }
        }
    }

    private Object executeWithTimeout(Function<Map<String, Object>, Object> callback,
                                       Map<String, Object> kwargs,
                                       double timeoutSeconds) throws TimeoutException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<Object> task = () -> callback.apply(kwargs);
            Future<Object> future = executor.submit(task);
            return future.get((long) (timeoutSeconds * 1000), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Callback execution interrupted", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void sleepRetryDelay(double delaySeconds) {
        if (delaySeconds > 0) {
            try {
                Thread.sleep((long) (delaySeconds * 1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void mergeResultToContext(ChainContext context, Object result) {
        if (result instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) result;
            context.getInitialKwargs().putAll(resultMap);
        }
    }
}
