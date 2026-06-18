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
 * Executes session-spawn task requests.
 *
 * <p>Mirrors Python's {@code SessionSpawnExecutor} in
 * {@code openjiuwen/harness/task_loop/session_spawn_executor.py}.</p>
 */
public class SessionSpawnExecutor extends TaskExecutor {

    public static final String SESSION_SPAWN_TASK_TYPE = "session_spawn";

    private final DeepAgent deepAgent;

    public SessionSpawnExecutor(TaskExecutorDependencies dependencies, DeepAgent deepAgent) {
        super(dependencies);
        this.deepAgent = deepAgent;
    }

    @Override
    public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
        ControllerOutputPayload payload = new ControllerOutputPayload(
                ControllerOutputPayload.TASK_PROCESSING,
                List.of(new DataFrame.TextDataFrame("Spawned sub-session for task: " + taskId)),
                Map.of("task_id", taskId, "agent", deepAgent == null ? "" : deepAgent.getCard().getName())
        );
        return List.of(new ControllerOutputChunk(0, payload, true)).iterator();
    }

    @Override
    public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
        return new PauseCheckResult(false, "session spawn cannot be paused");
    }

    @Override
    public boolean pause(String taskId, AgentSessionApi session) {
        return false;
    }

    @Override
    public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
        return new CancelCheckResult(true, null);
    }

    @Override
    public boolean cancel(String taskId, AgentSessionApi session) {
        return true;
    }

    public static SessionSpawnExecutor buildSessionSpawnExecutor(
            TaskExecutorDependencies dependencies,
            DeepAgent deepAgent
    ) {
        return new SessionSpawnExecutor(dependencies, deepAgent);
    }
}
