/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.sysop.SysOperation;
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
    private Model model;
    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelRequestConfig;
    private String systemPrompt = "";
    private int maxIterations = 15;
    private boolean enableTaskLoop = false;
    private String sysOperationId;
    private SysOperation sysOperation;
    private Workspace workspace;
    private List<ToolCard> tools = new ArrayList<>();
    private List<AgentRail> rails = new ArrayList<>();
    private List<DeepAgent> subagents = new ArrayList<>();
    private List<McpServerConfig> mcps = new ArrayList<>();
    private List<String> skills = new ArrayList<>();
    private boolean addGeneralPurposeAgent = false;
    private SessionToolkit sessionToolkit;
    private Map<String, Object> permissions = Map.of();

    public AgentCard getCard() {
        return card;
    }

    public void setCard(AgentCard card) {
        this.card = card;
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
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

    public boolean isEnableTaskLoop() {
        return enableTaskLoop;
    }

    public boolean getEnableTaskLoop() {
        return enableTaskLoop;
    }

    public void setEnableTaskLoop(boolean enableTaskLoop) {
        this.enableTaskLoop = enableTaskLoop;
    }

    public String getSysOperationId() {
        return sysOperationId;
    }

    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    public SysOperation getSysOperation() {
        return sysOperation;
    }

    public void setSysOperation(SysOperation sysOperation) {
        this.sysOperation = sysOperation;
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

    public List<McpServerConfig> getMcps() {
        return mcps;
    }

    public void setMcps(List<McpServerConfig> mcps) {
        this.mcps = mcps != null ? new ArrayList<>(mcps) : new ArrayList<>();
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills != null ? new ArrayList<>(skills) : new ArrayList<>();
    }

    public boolean isAddGeneralPurposeAgent() {
        return addGeneralPurposeAgent;
    }

    public boolean getAddGeneralPurposeAgent() {
        return addGeneralPurposeAgent;
    }

    public void setAddGeneralPurposeAgent(boolean addGeneralPurposeAgent) {
        this.addGeneralPurposeAgent = addGeneralPurposeAgent;
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
            if (hasStatus(taskId, "canceled")) {
                return;
            }
            mutateTask(taskId, "completed", "result", result);
        }

        public synchronized void failTask(String taskId, String error) {
            if (hasStatus(taskId, "canceled")) {
                return;
            }
            mutateTask(taskId, "error", "error", error);
        }

        public synchronized void cancelTask(String taskId) {
            if (hasStatus(taskId, "completed")) {
                return;
            }
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

        private boolean hasStatus(String taskId, String status) {
            for (Map<String, Object> row : tasks) {
                if (taskId.equals(row.get("task_id")) && status.equals(row.get("status"))) {
                    return true;
                }
            }
            return false;
        }
    }

    public interface SubagentInvoker {
        Object invoke(DeepAgent parentAgent, ToolCall toolCall, Map<String, Object> toolArgs, AgentSessionApi session);
    }
}
