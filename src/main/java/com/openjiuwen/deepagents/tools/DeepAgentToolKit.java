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

/** Deepagents-level facade for delegation and task/session inspection tools. */
public class DeepAgentToolKit {
  private final SessionToolkit sessionToolkit;
  private final TaskTool taskTool;
  private final SessionsListTool sessionsListTool;
  private final SessionsCancelTool sessionsCancelTool;

  /** Auto-generated for codecheck compliance. */
  public DeepAgentToolKit(DeepAgent parentAgent) {
    this(parentAgent, new SessionToolkit());
  }

  /** Auto-generated for codecheck compliance. */
  public DeepAgentToolKit(DeepAgent parentAgent, SessionToolkit sessionToolkit) {
    this.sessionToolkit = sessionToolkit;
    this.taskTool = new TaskTool(parentAgent);
    this.sessionsListTool = new SessionsListTool(sessionToolkit);
    this.sessionsCancelTool = new SessionsCancelTool(sessionToolkit);
  }

  /** Auto-generated for codecheck compliance. */
  public ToolOutput delegate(
      String taskId, String subagentType, String taskDescription, String parentSessionId) {
    ToolOutput output = taskTool.delegate(subagentType, taskDescription, parentSessionId);
    if (output.isSuccess()) {
      Object raw = output.getData();
      if (raw instanceof java.util.Map<?, ?> payload) {
        Object subSessionId = payload.get("sub_session_id");
        sessionToolkit.upsertRunning(
            taskId, subSessionId != null ? String.valueOf(subSessionId) : "", taskDescription);
        sessionToolkit.markCompleted(taskId, String.valueOf(payload.get("result")));
      }
    } else {
      sessionToolkit.upsertRunning(taskId, "", taskDescription);
      sessionToolkit.markFailed(taskId, output.getError());
    }
    return output;
  }

  /** Auto-generated for codecheck compliance. */
  public ToolOutput listSessions() {
    return sessionsListTool.list();
  }

  /** Auto-generated for codecheck compliance. */
  public ToolOutput cancelSession(String taskId) {
    return sessionsCancelTool.cancel(taskId);
  }

  /** Auto-generated for codecheck compliance. */
  public SessionTaskRow getTask(String taskId) {
    return sessionToolkit.get(taskId);
  }

  /** Auto-generated for codecheck compliance. */
  public List<SessionTaskRow> tasks() {
    return sessionToolkit.listAll();
  }

  /** Auto-generated for codecheck compliance. */
  public SessionToolkit sessionToolkit() {
    return sessionToolkit;
  }
}
