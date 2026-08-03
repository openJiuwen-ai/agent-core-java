/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external.cli_agent;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

/**
 * Writes a unit of text into a running external agent's input channel.
 *
 * <p>Mirrors Python's {@code Injector} Protocol in
 * {@code openjiuwen/agent_teams/external/cli_agent/injector.py}.</p>
 *
 * <p>Python exposes this as a {@code @runtime_checkable Protocol}. Java mirrors the same
 * shape-based acceptance check with {@link #isInjector(Object)}.</p>
 */
public interface Injector {

    CompletionStage<Void> write(String text);

    CompletionStage<Void> aclose();

    static boolean isInjector(Object candidate) {
        if (candidate == null) {
            return false;
        }
        Class<?> type = candidate.getClass();
        return matches(type, "write", String.class) && matches(type, "aclose");
    }

    private static boolean matches(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(methodName, parameterTypes);
            return CompletionStage.class.isAssignableFrom(method.getReturnType());
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}
