/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * BrowserRuntimeAgentCompat.
 * @since 0.1.7
 */
public final class BrowserRuntimeAgentCompat {
    /**
     * BrowserRuntimeAgentCompat.
     * @since 0.1.7
     */
    private BrowserRuntimeAgentCompat() {
    }

    /**
     * ensureExecuteSignatureCompat.
     * @param agent agent
     * @param timeoutMillis timeoutMillis
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static void ensureExecuteSignatureCompat(Object agent, long timeoutMillis) {
        try {
            Field field = agent.getClass().getDeclaredField("ability_manager");
            field.setAccessible(true);
            Object abilityManager = field.get(agent);
            Method method =
                abilityManager.getClass().getMethod("execute", Object.class, Object.class, Object.class, Object.class);
            Object original = java.lang.reflect.Proxy.newProxyInstance(abilityManager.getClass().getClassLoader(),
                    abilityManager.getClass().getInterfaces(),
                    (proxy, invokedMethod, args) -> invokedMethod.invoke(abilityManager, args));
            field.set(agent, new Object() {
                public Object execute(Object ctx, Object toolCall, Object session, Object tag) throws Exception {
                    CompletableFuture<Object> future = OpenJiuwenExecutors.supplyBackgroundAsync(() -> {
                        try {
                            return method.invoke(abilityManager, ctx, toolCall, session, tag);
                        } catch (IllegalAccessException | InvocationTargetException ex) {
                            throw new IllegalStateException("failed to invoke browser execute", ex);
                        }
                    });
                    try {
                        return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
                    } catch (java.util.concurrent.TimeoutException ex) {
                        throw new IllegalStateException("tool execution timeout after " + timeoutMillis + "ms", ex);
                    } catch (InterruptedException ex) {
                        throw new IllegalStateException("tool execution interrupted", ex);
                    } catch (ExecutionException ex) {
                        throw new IllegalStateException("tool execution failed", ex);
                    }
                }
            });
        } catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException | SecurityException ex) {
            // Best-effort compatibility shim for future browser-agent wiring.
        }
    }
}
