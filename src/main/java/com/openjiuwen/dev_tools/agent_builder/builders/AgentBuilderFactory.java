/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * Agent builder factory — creates the appropriate builder based on agent type.
 * <p>
 * Mirrors Python's {@code factory} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.factory}.
 */
public final class AgentBuilderFactory {

    private static final Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> BUILDERS =
            new LinkedHashMap<>();

    private AgentBuilderFactory() {
    }

    /** Create a builder for the given agent type. */
    public static BaseAgentBuilder create(AgentBuilderEnums.AgentType type) {
        return create(type, null);
    }

    public static BaseAgentBuilder create(AgentBuilderEnums.AgentType type,
                                           ProgressReporter progressReporter) {
        if (type == null) {
            throw new IllegalArgumentException("Unsupported agent type: null");
        }
        ensureRegistered();
        Class<? extends BaseAgentBuilder> builderClass = BUILDERS.get(type);
        if (builderClass == null) {
            throw new IllegalArgumentException("Unsupported agent type: " + type);
        }
        return instantiateWithProgressReporter(builderClass, progressReporter);
    }

    public static BaseAgentBuilder create(AgentBuilderEnums.AgentType type,
                                          Object llm,
                                          HistoryManager historyManager) {
        if (type == null) {
            throw new IllegalArgumentException("Unsupported agent type: null");
        }
        ensureRegistered();
        Class<? extends BaseAgentBuilder> builderClass = BUILDERS.get(type);
        if (builderClass == null) {
            throw new IllegalArgumentException("Unsupported agent type: " + type);
        }
        return instantiateWithRuntime(builderClass, llm, historyManager);
    }

    public static void register(AgentBuilderEnums.AgentType type, Class<?> builderClass) {
        if (type == null || builderClass == null) {
            throw new IllegalArgumentException("Agent type and builder class are required");
        }
        if (!BaseAgentBuilder.class.isAssignableFrom(builderClass)) {
            throw new IllegalArgumentException(
                    "Builder class must inherit from BaseAgentBuilder, got: " + builderClass.getSimpleName());
        }
        BUILDERS.put(type, builderClass.asSubclass(BaseAgentBuilder.class));
    }

    public static List<AgentBuilderEnums.AgentType> getSupportedTypes() {
        return new ArrayList<>(BUILDERS.keySet());
    }

    public static void clearRegistry() {
        BUILDERS.clear();
    }

    public static Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> getRegisteredBuilders() {
        return new LinkedHashMap<>(BUILDERS);
    }

    private static void ensureRegistered() {
        if (!BUILDERS.isEmpty()) {
            return;
        }
        BUILDERS.put(AgentBuilderEnums.AgentType.LLM_AGENT, LlmAgentBuilder.class);
        BUILDERS.put(AgentBuilderEnums.AgentType.WORKFLOW, WorkflowBuilder.class);
    }

    private static BaseAgentBuilder instantiateWithProgressReporter(
            Class<? extends BaseAgentBuilder> builderClass,
            ProgressReporter progressReporter) {
        try {
            Constructor<? extends BaseAgentBuilder> constructor =
                    builderClass.getDeclaredConstructor(ProgressReporter.class);
            constructor.setAccessible(true);
            return constructor.newInstance(progressReporter);
        } catch (ReflectiveOperationException ignored) {
            return instantiateWithRuntime(builderClass, null, null);
        }
    }

    private static BaseAgentBuilder instantiateWithRuntime(
            Class<? extends BaseAgentBuilder> builderClass,
            Object llm,
            HistoryManager historyManager) {
        try {
            Constructor<? extends BaseAgentBuilder> constructor =
                    builderClass.getDeclaredConstructor(Object.class, HistoryManager.class);
            constructor.setAccessible(true);
            return constructor.newInstance(llm, historyManager);
        } catch (ReflectiveOperationException first) {
            try {
                Constructor<? extends BaseAgentBuilder> constructor =
                        builderClass.getDeclaredConstructor(Object.class, Object.class);
                constructor.setAccessible(true);
                return constructor.newInstance(llm, historyManager);
            } catch (ReflectiveOperationException second) {
                throw new IllegalStateException(
                        "Unable to create builder instance: " + builderClass.getSimpleName(), second);
            }
        }
    }
}
