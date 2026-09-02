/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.deepagents.tools;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.tools.SessionTaskRow;
import com.openjiuwen.harness.tools.SessionToolkit;
import com.openjiuwen.harness.tools.SessionsCancelTool;
import com.openjiuwen.harness.tools.SessionsListTool;
import com.openjiuwen.harness.tools.TaskTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.List;

/**
 * Deepagents-level facade for delegation and task/session inspection tools.
 * 
 * @since 0.1.7
 */
public class DeepAgentToolKit {
    private final SessionToolkit sessionToolkit;
    private final TaskTool taskTool;
    private final SessionsListTool sessionsListTool;
    private final SessionsCancelTool sessionsCancelTool;

    /**
     * DeepAgentToolKit.
     * 
     * @param parentAgent parentAgent
     * @since 0.1.7
     */
    public DeepAgentToolKit(DeepAgent parentAgent) {
        this(parentAgent, new SessionToolkit());
    }

    /**
     * DeepAgentToolKit.
     * 
     * @param parentAgent parentAgent
     * @param sessionToolkit sessionToolkit
     * @since 0.1.7
     */
    public DeepAgentToolKit(DeepAgent parentAgent, SessionToolkit sessionToolkit) {
        this.sessionToolkit = sessionToolkit;
        this.taskTool = new TaskTool(parentAgent);
        this.sessionsListTool = new SessionsListTool(sessionToolkit);
        this.sessionsCancelTool = new SessionsCancelTool(sessionToolkit);
    }

    /**
     * delegate.
     * 
     * @param taskId taskId
     * @param subagentType subagentType
     * @param taskDescription taskDescription
     * @param parentSessionId parentSessionId
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput delegate(String taskId, String subagentType, String taskDescription, String parentSessionId) {
        ToolOutput output = taskTool.delegate(subagentType, taskDescription, parentSessionId);
        if (output.isSuccess()) {
            Object raw = output.getData();
            if (raw instanceof java.util.Map<?, ?> payload) {
                Object subSessionId = payload.get("sub_session_id");
                sessionToolkit.upsertRunning(taskId, subSessionId != null ? String.valueOf(subSessionId) : "",
                        taskDescription);
                sessionToolkit.markCompleted(taskId, String.valueOf(payload.get("result")));
            }
        } else {
            sessionToolkit.upsertRunning(taskId, "", taskDescription);
            sessionToolkit.markFailed(taskId, output.getError());
        }
        return output;
    }

    /**
     * listSessions.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput listSessions() {
        return sessionsListTool.list();
    }

    /**
     * cancelSession.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput cancelSession(String taskId) {
        return sessionsCancelTool.cancel(taskId);
    }

    /**
     * getTask.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public SessionTaskRow getTask(String taskId) {
        return sessionToolkit.get(taskId);
    }

    /**
     * tasks.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<SessionTaskRow> tasks() {
        return sessionToolkit.listAll();
    }

    /**
     * sessionToolkit.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SessionToolkit sessionToolkit() {
        return sessionToolkit;
    }
}
