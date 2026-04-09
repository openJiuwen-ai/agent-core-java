/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.workflow.Workflow;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Legacy compatibility facade for the single-agent module.
 *
 * <p>Mirrors Python's {@code single_agent.legacy.__init__} package-level exports,
 * including {@code workflow_provider(...)}, {@code create_react_agent_config(...)},
 * and the {@code ReActAgentConfig} alias.</p>
 *
 * <p>All factory methods in this class are deprecated and will be removed in v1.0.0.</p>
 *
 * @deprecated Use the modern single-agent API instead.
 */
@Deprecated(since = "0.1.7", forRemoval = true)
public final class LegacyApi {

    private LegacyApi() {
        // static-only facade
    }

    // ======================== workflow_provider ========================

    /**
     * Create a {@link WorkflowFactory} from a factory supplier (mirrors Python's
     * {@code @workflow_provider} decorator).
     *
     * <p>Usage:
     * <pre>{@code
     * WorkflowFactory wf = LegacyApi.workflowProvider(
     *         "weather_workflow", "1.0",
     *         "Weather", "Fetches weather",
     *         null, () -> buildWorkflow());
     * agent.addWorkflows(List.of(wf));
     * }</pre>
     *
     * @param workflowId          workflow ID for registration
     * @param workflowVersion     workflow version for registration
     * @param workflowName        workflow display name (optional)
     * @param workflowDescription workflow description (optional)
     * @param inputSchema         input schema (optional)
     * @param factory             factory function returning a new Workflow each call
     * @return a concurrency-safe {@link WorkflowFactory}
     */
    @Deprecated(since = "0.1.7", forRemoval = true)
    public static WorkflowFactory workflowProvider(String workflowId,
                                                   String workflowVersion,
                                                   String workflowName,
                                                   String workflowDescription,
                                                   Object inputSchema,
                                                   Supplier<Workflow> factory) {
        Loggers.AGENT.warning("workflowProvider() is deprecated and will be removed in v1.0.0. "
                + "Use Workflow class directly.");
        return new WorkflowFactory(workflowId, workflowVersion, factory,
                workflowName, workflowDescription, inputSchema);
    }

    /**
     * Shorthand without optional metadata.
     */
    @Deprecated(since = "0.1.7", forRemoval = true)
    public static WorkflowFactory workflowProvider(String workflowId,
                                                   String workflowVersion,
                                                   Supplier<Workflow> factory) {
        return workflowProvider(workflowId, workflowVersion, "", "", null, factory);
    }

    // ======================== create_react_agent_config ========================

    /**
     * Package-level factory for {@link LegacyReActAgentConfig} (mirrors Python's
     * {@code create_react_agent_config(...)}).
     *
     * @param agentId        agent identifier
     * @param agentVersion   agent version
     * @param description    agent description
     * @param model          model configuration
     * @param promptTemplate prompt template entries
     * @return a configured {@link LegacyReActAgentConfig}
     */
    @Deprecated(since = "0.1.7", forRemoval = true)
    public static LegacyReActAgentConfig createReActAgentConfig(String agentId,
                                                                String agentVersion,
                                                                String description,
                                                                ModelConfig model,
                                                                List<Map<String, String>> promptTemplate) {
        Loggers.AGENT.warning("createReActAgentConfig() is deprecated. "
                + "Use ReActAgentConfig from openjiuwen.core.singleagent.agents instead.");
        return LegacyReActAgent.createReActAgentConfig(agentId, agentVersion, description, model, promptTemplate);
    }

    // ======================== Deprecation helper ========================

    /**
     * Issue a runtime deprecation warning via the agent logger.
     *
     * @param className   the legacy class being instantiated
     * @param alternative the recommended replacement
     */
    public static void emitDeprecationWarning(String className, String alternative) {
        Loggers.AGENT.warning(className + " is deprecated and will be removed in v1.0.0. "
                + "Please use " + alternative + " instead.");
    }
}
