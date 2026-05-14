/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal Java configuration for the first `harness` port.
 *
 * <p>Mirrors Python's {@code DeepAgentConfig} in
 * {@code openjiuwen.harness.deep_agent}.
 *
 * <p>Keeps the Java surface intentionally small and focused on the migrated
 * runtime entrypoints.
 */
public class DeepAgentConfig {

    private AgentCard card;
    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelRequestConfig;
    private String systemPrompt = "";
    private int maxIterations = 15;
    private String sysOperationId;
    private Workspace workspace;
    private List<ToolCard> tools = new ArrayList<>();
    private List<AgentRail> rails = new ArrayList<>();
    private List<DeepAgent> subagents = new ArrayList<>();
    private SessionToolkit sessionToolkit;
    private Map<String, Object> permissions = Map.of();

    public AgentCard getCard() {
        return card;
    }

    public void setCard(AgentCard card) {
        this.card = card;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    public void setModelClientConfig(ModelClientConfig modelClientConfig) {
        this.modelClientConfig = modelClientConfig;
    }

    public ModelRequestConfig getModelRequestConfig() {
        return modelRequestConfig;
    }

    public void setModelRequestConfig(ModelRequestConfig modelRequestConfig) {
        this.modelRequestConfig = modelRequestConfig;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public String getSysOperationId() {
        return sysOperationId;
    }

    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public List<ToolCard> getTools() {
        return tools;
    }

    public void setTools(List<ToolCard> tools) {
        this.tools = tools != null ? new ArrayList<>(tools) : new ArrayList<>();
    }

    public List<AgentRail> getRails() {
        return rails;
    }

    public void setRails(List<AgentRail> rails) {
        this.rails = rails != null ? new ArrayList<>(rails) : new ArrayList<>();
    }

    public List<DeepAgent> getSubagents() {
        return subagents;
    }

    public void setSubagents(List<DeepAgent> subagents) {
        this.subagents = subagents != null ? new ArrayList<>(subagents) : new ArrayList<>();
    }

    public SessionToolkit getSessionToolkit() {
        return sessionToolkit;
    }

    public void setSessionToolkit(SessionToolkit sessionToolkit) {
        this.sessionToolkit = sessionToolkit;
    }

    public Map<String, Object> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Object> permissions) {
        this.permissions = permissions != null ? permissions : Map.of();
    }

    public static final class SessionToolkit {
        private final List<Map<String, Object>> sessions = new ArrayList<>();
        private final List<Map<String, Object>> tasks = new ArrayList<>();

        public synchronized void register(AgentSessionApi session, String agentName, String summary) {
            sessions.add(Map.of(
                    "session_id", session != null ? session.getSessionId() : "",
                    "agent_name", agentName != null ? agentName : "",
                    "summary", summary != null ? summary : ""
            ));
        }

        public synchronized void upsertTask(String taskId, String subSessionId, String description, String status) {
            Map<String, Object> existing = null;
            for (Map<String, Object> row : tasks) {
                if (taskId.equals(row.get("task_id"))) {
                    existing = row;
                    break;
                }
            }
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("task_id", taskId != null ? taskId : "");
            row.put("sub_session_id", subSessionId != null ? subSessionId : "");
            row.put("description", description != null ? description : "");
            row.put("status", status != null ? status : "running");
            if (existing != null) {
                tasks.remove(existing);
            }
            tasks.add(row);
        }

        public synchronized void completeTask(String taskId, String result) {
            mutateTask(taskId, "completed", "result", result);
        }

        public synchronized void failTask(String taskId, String error) {
            mutateTask(taskId, "error", "error", error);
        }

        public synchronized void cancelTask(String taskId) {
            mutateTask(taskId, "canceled", null, null);
        }

        public synchronized List<Map<String, Object>> listTasks() {
            List<Map<String, Object>> copies = new ArrayList<>();
            for (Map<String, Object> row : tasks) {
                copies.add(new java.util.LinkedHashMap<>(row));
            }
            return copies;
        }

        public synchronized List<Map<String, Object>> listSessions() {
            return new ArrayList<>(sessions);
        }

        private void mutateTask(String taskId, String status, String extraKey, String extraValue) {
            for (Map<String, Object> row : tasks) {
                if (taskId.equals(row.get("task_id"))) {
                    row.put("status", status);
                    if (extraKey != null) {
                        row.put(extraKey, extraValue != null ? extraValue : "");
                    }
                    return;
                }
            }
        }
    }

    public interface SubagentInvoker {
        Object invoke(DeepAgent parentAgent, ToolCall toolCall, Map<String, Object> toolArgs, AgentSessionApi session);
    }
}
