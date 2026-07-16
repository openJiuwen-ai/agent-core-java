/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Public class TaskTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TaskTool {
    private final DeepAgent parentAgent;

    /**
     * TaskTool.
     * 
     * @param parentAgent parentAgent
     * @since 0.1.7
     */
    public TaskTool(DeepAgent parentAgent) {
        this.parentAgent = parentAgent;
    }

    /**
     * delegate.
     * 
     * @param subagentType subagentType
     * @param taskDescription taskDescription
     * @param parentSessionId parentSessionId
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput delegate(String subagentType, String taskDescription, String parentSessionId) {
        String subSessionId = buildSubSessionId(parentSessionId, subagentType);
        try {
            DeepAgent subagent = parentAgent.createSubagent(subagentType, subSessionId);
            Map<String, Object> result =
                subagent.invoke(Map.of("query", taskDescription, "conversation_id", subSessionId));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agent_id", subagent.getCard().getId());
            payload.put("sub_session_id", subSessionId);
            payload.put("result", result);
            return ToolOutput.builder().success(true).data(payload).build();
        } catch (IllegalArgumentException ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agent_id", subagentType == null ? "subagent" : subagentType);
            payload.put("sub_session_id", subSessionId);
            payload.put("result",
                    Map.of("status", "skipped", "reason", ex.getMessage() == null ? "" : ex.getMessage()));
            return ToolOutput.builder().success(true).data(payload).build();
        } catch (Exception ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    static String buildSubSessionId(String parentSessionId, String subagentType) {
        String base = parentSessionId != null && !parentSessionId.isBlank() ? parentSessionId : "session";
        String normalized = subagentType != null ? subagentType.trim().toLowerCase(Locale.ROOT) : "subagent";
        return base + "_sub_" + normalized + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
