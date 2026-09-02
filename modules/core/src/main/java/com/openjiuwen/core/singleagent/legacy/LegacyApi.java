/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.workflow.Workflow;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * LegacyApi.
 * 
 * @since 0.1.7
 */
@Deprecated(since = "0.1.7", forRemoval = true)
public final class LegacyApi {
    /**
     * LegacyApi.
     * 
     * @since 0.1.7
     */
    private LegacyApi() {
        // static-only facade
    }

    /**
     * workflowProvider.
     * 
     * @param workflowId workflowId
     * @param workflowVersion workflowVersion
     * @param workflowName workflowName
     * @param workflowDescription workflowDescription
     * @param inputSchema inputSchema
     * @param factory factory
     * @return the result
     * @since 0.1.7
     */
    @Deprecated(since = "0.1.7", forRemoval = true)
    public static WorkflowFactory workflowProvider(String workflowId, String workflowVersion, String workflowName,
            String workflowDescription, Object inputSchema, Supplier<Workflow> factory) {
        Loggers.AGENT.warning(
                "workflowProvider() is deprecated and will be removed in v1.0.0. " + "Use Workflow class directly.");
        return new WorkflowFactory(workflowId, workflowVersion, factory, workflowName, workflowDescription,
                inputSchema);
    }

    /**
     * workflowProvider.
     * 
     * @param workflowId workflowId
     * @param workflowVersion workflowVersion
     * @param factory factory
     * @return the result
     * @since 0.1.7
     */
    @Deprecated(since = "0.1.7", forRemoval = true)
    public static WorkflowFactory workflowProvider(String workflowId, String workflowVersion,
            Supplier<Workflow> factory) {
        return workflowProvider(workflowId, workflowVersion, "", "", null, factory);
    }

    /**
     * createReActAgentConfig.
     * 
     * @param agentId agentId
     * @param agentVersion agentVersion
     * @param description description
     * @param model model
     * @param promptTemplate promptTemplate
     * @return the result
     * @since 0.1.7
     */
    @Deprecated(since = "0.1.7", forRemoval = true)
    public static LegacyReActAgentConfig createReActAgentConfig(String agentId, String agentVersion, String description,
            ModelConfig model, List<Map<String, String>> promptTemplate) {
        Loggers.AGENT.warning("createReActAgentConfig() is deprecated. "
                + "Use ReActAgentConfig from openjiuwen.core.singleagent.agents instead.");
        return LegacyReActAgent.createReActAgentConfig(agentId, agentVersion, description, model, promptTemplate);
    }

    // ======================== Deprecation helper ========================

    /**
     * Issue a runtime deprecation warning via the agent logger.
     * 
     * @param className the legacy class being instantiated
     * @param alternative the recommended replacement
     * @since 0.1.7
     */
    public static void emitDeprecationWarning(String className, String alternative) {
        Loggers.AGENT.warning(className + " is deprecated and will be removed in v1.0.0. " + "Please use " + alternative
                + " instead.");
    }
}
