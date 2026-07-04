/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.react_agent.LegacyReActAgentFactory;
import com.openjiuwen.core.workflow.Workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Root-package compatibility facade for the legacy single-agent API.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.single_agent.legacy} exports in
 * {@code openjiuwen/core/single_agent/legacy/__init__.py}.</p>
 */
@Deprecated(since = "0.1.7", forRemoval = true)
public final class LegacyApi {
    private LegacyApi() {
    }

    public static WorkflowFactory workflowProvider(String workflowId, String workflowVersion,
                                                   Supplier<Workflow> factory) {
        return workflowProvider(workflowId, workflowVersion, "", "", null, factory);
    }

    public static WorkflowFactory workflowProvider(String workflowId,
                                                   String workflowVersion,
                                                   String workflowName,
                                                   String workflowDescription,
                                                   Object inputSchema,
                                                   Supplier<Workflow> factory) {
        emitDeprecationWarning("workflowProvider()", "Workflow class directly");
        return new WorkflowFactory(
                workflowId,
                workflowVersion,
                factory,
                workflowName,
                workflowDescription,
                inputSchema);
    }

    public static WorkflowFactory workflow_provider(String workflowId,
                                                    String workflowVersion,
                                                    String workflowName,
                                                    String workflowDescription,
                                                    Object inputSchema,
                                                    Supplier<Workflow> factory) {
        return workflowProvider(workflowId, workflowVersion, workflowName, workflowDescription, inputSchema, factory);
    }

    public static LegacyReActAgentConfig createReActAgentConfig(String agentId,
                                                                String agentVersion,
                                                                String description,
                                                                ModelConfig model,
                                                                List<Map<String, String>> promptTemplate) {
        emitDeprecationWarning("createReActAgentConfig()", "ReActAgentConfig from the modern single-agent API");
        return LegacyReActAgentFactory.createReactAgentConfig(
                agentId,
                agentVersion,
                description,
                model,
                copyPromptTemplate(promptTemplate));
    }

    public static LegacyReActAgentConfig create_react_agent_config(String agentId,
                                                                   String agentVersion,
                                                                   String description,
                                                                   ModelConfig model,
                                                                   List<Map<String, String>> promptTemplate) {
        return createReActAgentConfig(agentId, agentVersion, description, model, promptTemplate);
    }

    public static void emitDeprecationWarning(String className, String alternative) {
        Loggers.AGENT.warning(className + " is deprecated and will be removed in v1.0.0. "
                + "Please use " + alternative + " instead.");
    }

    private static List<Map<String, Object>> copyPromptTemplate(List<Map<String, String>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Map<String, String> item : source) {
                copy.add(item == null ? new LinkedHashMap<>() : new LinkedHashMap<>(item));
            }
        }
        return copy;
    }
}
