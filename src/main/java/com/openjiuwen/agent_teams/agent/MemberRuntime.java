/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * The runtime surface a team member's coordination layer drives.
 *
 * <p>Mirrors Python's {@code MemberRuntime} in
 * {@code openjiuwen/agent_teams/agent/member_runtime.py}.</p>
 *
 * <p>Python exposes this as a {@code @runtime_checkable Protocol}. Java mirrors the same
 * shape-check behavior with {@link #isRuntime(Object)} so callers can validate runtime-like
 * objects without forcing inheritance.</p>
 */
public interface MemberRuntime {

    Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId);

    CompletionStage<Void> steer(String content);

    CompletionStage<Void> followUp(String content);

    CompletionStage<Void> abort();

    void initCwdForRound();

    boolean hasPendingInterrupt();

    boolean isPendingInterruptResumeValid(Object userInput);

    List<Object> findRails(Class<?> railType);

    CompletionStage<Void> registerRail(Object rail);

    CompletionStage<Void> unregisterRail(Object rail);

    void registerMemberTools(Object memoryManager);

    CompletionStage<Void> injectMemberMemory(Object memoryManager, String query);

    void runAgentCustomizer(AgentCustomizer customizer);

    Object workspace();

    Object sysOperation();

    static boolean isRuntime(Object candidate) {
        if (candidate == null) {
            return false;
        }
        Class<?> type = candidate.getClass();
        return returns(type, "runStreaming", Iterator.class, Map.class, String.class)
                && returns(type, "steer", CompletionStage.class, String.class)
                && returns(type, "followUp", CompletionStage.class, String.class)
                && returns(type, "abort", CompletionStage.class)
                && returns(type, "initCwdForRound", Void.TYPE)
                && returns(type, "hasPendingInterrupt", Boolean.TYPE)
                && returns(type, "isPendingInterruptResumeValid", Boolean.TYPE, Object.class)
                && returns(type, "findRails", List.class, Class.class)
                && returns(type, "registerRail", CompletionStage.class, Object.class)
                && returns(type, "unregisterRail", CompletionStage.class, Object.class)
                && returns(type, "registerMemberTools", Void.TYPE, Object.class)
                && returns(type, "injectMemberMemory", CompletionStage.class, Object.class, String.class)
                && returns(type, "runAgentCustomizer", Void.TYPE, AgentCustomizer.class)
                && hasMethod(type, "workspace")
                && hasMethod(type, "sysOperation");
    }

    private static boolean returns(
            Class<?> type,
            String methodName,
            Class<?> returnType,
            Class<?>... parameterTypes
    ) {
        try {
            Method method = type.getMethod(methodName, parameterTypes);
            if (returnType.isPrimitive()) {
                return method.getReturnType() == returnType;
            }
            return returnType.isAssignableFrom(method.getReturnType());
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static boolean hasMethod(Class<?> type, String methodName) {
        try {
            type.getMethod(methodName);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}
