/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.DeepAgent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Executes DeepAgent task-plan tasks through the task-loop.
 *
 * <p>Mirrors Python's {@code TaskLoopEventExecutor} in
 * {@code openjiuwen/harness/task_loop/task_loop_event_executor.py}.</p>
 */
public class TaskLoopEventExecutor extends TaskExecutor {

    public static final String DEEP_TASK_TYPE = "deep_agent_task";

    private final DeepAgent deepAgent;

    public TaskLoopEventExecutor(TaskExecutorDependencies dependencies, DeepAgent deepAgent) {
        super(dependencies);
        this.deepAgent = deepAgent;
    }

    @Override
    public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
        String message = "Executed deep-agent task: " + taskId;
        ControllerOutputPayload payload = new ControllerOutputPayload(
                ControllerOutputPayload.TASK_PROCESSING,
                List.of(new DataFrame.TextDataFrame(message)),
                Map.of("task_id", taskId, "agent", deepAgent == null ? "" : deepAgent.getCard().getName())
        );
        return List.of(new ControllerOutputChunk(0, payload, true)).iterator();
    }

    @Override
    public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
        return new PauseCheckResult(true, null);
    }

    @Override
    public boolean pause(String taskId, AgentSessionApi session) {
        return true;
    }

    @Override
    public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
        return new CancelCheckResult(true, null);
    }

    @Override
    public boolean cancel(String taskId, AgentSessionApi session) {
        if (deepAgent != null) {
            deepAgent.abort(session);
        }
        return true;
    }

    public static TaskLoopEventExecutor buildDeepExecutor(
            TaskExecutorDependencies dependencies,
            DeepAgent deepAgent
    ) {
        return new TaskLoopEventExecutor(dependencies, deepAgent);
    }
}
