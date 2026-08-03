/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Pure-text protocol adapter to a remote independent agent.
 *
 * <p>Mirrors Python's {@code BridgeProtocolAdapter} in
 * {@code openjiuwen/agent_teams/interaction/bridge_protocol.py}.</p>
 *
 * <p>Python exposes this as a {@code @runtime_checkable Protocol}. Java has no direct structural
 * runtime protocol support, so the mirror provides {@link #isAdapter(Object)} to preserve the
 * "shape-only" acceptance check used by the Python tests.</p>
 */
public interface BridgeProtocolAdapter {

    CompletionStage<Void> connect(
            String memberName,
            Map<String, Object> adapterConfig,
            String bridgePersona,
            String teamOverview
    );

    CompletionStage<String> relay(String memberName, String text);

    CompletionStage<Void> close();

    static boolean isAdapter(Object candidate) {
        if (candidate == null) {
            return false;
        }
        Class<?> type = candidate.getClass();
        return matches(type, "connect", String.class, Map.class, String.class, String.class)
                && matches(type, "relay", String.class, String.class)
                && matches(type, "close");
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
