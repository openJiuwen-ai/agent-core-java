/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.dev_tools.agent_builder.builders.llm_agent.LlmAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.WorkflowBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent builder factory for creating builder instances.
 *
 * <p>Mirrors Python's {@code AgentBuilderFactory} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/factory.py}.</p>
 */
public final class AgentBuilderFactory {

    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");
    private static final Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> BUILDERS =
            new LinkedHashMap<>();

    private AgentBuilderFactory() {
    }

    public static synchronized BaseAgentBuilder create(AgentBuilderEnums.AgentType agentType,
                                                       Model llm,
                                                       HistoryManager historyManager) {
        ensureDefaultBuilders();
        Class<? extends BaseAgentBuilder> builderClass = BUILDERS.get(agentType);
        if (builderClass == null) {
            String errorMessage = "Unsupported agent type: " + agentType;
            LOGGER.error("Unsupported agent type agent_type={}", agentType == null ? null : agentType.getValue());
            throw new IllegalArgumentException(errorMessage);
        }

        LOGGER.debug(
                "Creating builder instance agent_type={} builder_class={}",
                agentType.getValue(),
                builderClass.getSimpleName());
        return instantiate(builderClass, llm, historyManager);
    }

    public static synchronized void register(AgentBuilderEnums.AgentType agentType, Class<?> builderClass) {
        Objects.requireNonNull(agentType, "agentType");
        Objects.requireNonNull(builderClass, "builderClass");
        if (!BaseAgentBuilder.class.isAssignableFrom(builderClass)) {
            throw new IllegalArgumentException(
                    "Builder class must inherit from BaseAgentBuilder, got: " + builderClass.getSimpleName());
        }

        @SuppressWarnings("unchecked")
        Class<? extends BaseAgentBuilder> typedBuilderClass = (Class<? extends BaseAgentBuilder>) builderClass;
        BUILDERS.put(agentType, typedBuilderClass);
        LOGGER.info(
                "Registered new builder type agent_type={} builder_class={}",
                agentType.getValue(),
                builderClass.getSimpleName());
    }

    public static synchronized List<AgentBuilderEnums.AgentType> getSupportedTypes() {
        return List.copyOf(BUILDERS.keySet());
    }

    public static synchronized void clearRegistry() {
        BUILDERS.clear();
    }

    public static synchronized Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>>
    getRegisteredBuilders() {
        return new LinkedHashMap<>(BUILDERS);
    }

    private static void ensureDefaultBuilders() {
        if (!BUILDERS.isEmpty()) {
            return;
        }
        BUILDERS.put(AgentBuilderEnums.AgentType.LLM_AGENT, LlmAgentBuilder.class);
        BUILDERS.put(AgentBuilderEnums.AgentType.WORKFLOW, WorkflowBuilder.class);
    }

    private static BaseAgentBuilder instantiate(Class<? extends BaseAgentBuilder> builderClass,
                                                Model llm,
                                                HistoryManager historyManager) {
        try {
            Constructor<? extends BaseAgentBuilder> constructor =
                    builderClass.getConstructor(Model.class, HistoryManager.class);
            return constructor.newInstance(llm, historyManager);
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException(
                    "Builder class must provide a (Model, HistoryManager) constructor: "
                            + builderClass.getSimpleName(),
                    exception);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to create builder instance: " + builderClass.getSimpleName(),
                    exception);
        }
    }
}
