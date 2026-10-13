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
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.kvcache.KVCacheSubagentLifecycle;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Executes session-spawn task requests.
 *
 * <p>Mirrors Python's {@code SessionSpawnExecutor} in
 * {@code openjiuwen/harness/task_loop/session_spawn_executor.py}: each spawned
 * sub-session shares the parent's application KVC runtime (prepare-on-use is
 * owned by the model-call hook), and is evicted unconditionally once the task
 * settles so a finished spawn never pins device cache.</p>
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
        java.util.Optional<AgentSessionApi> childSession = buildChildSession(taskId, session);
        try {
            ControllerOutputPayload payload = new ControllerOutputPayload(
                    ControllerOutputPayload.TASK_PROCESSING,
                    List.of(new DataFrame.TextDataFrame("Spawned sub-session for task: " + taskId)),
                    Map.of("task_id", taskId, "agent", deepAgent == null ? "" : deepAgent.getCard().getName())
            );
            return List.of(new ControllerOutputChunk(0, payload, true)).iterator();
        } finally {
            childSession.ifPresent(sessionToEvict ->
                    KVCacheSubagentLifecycle.evictSubagent(sessionToEvict).join());
        }
    }

    /**
     * Build the spawned child session sharing the parent's KVC runtime.
     *
     * <p>Mirrors Python's {@code resolve_sub_session_id} +
     * {@code create_subagent_session}: the metadata-declared sub-session id
     * wins, and the child binds to the parent's provider-facing cache id.</p>
     *
     * @param taskId spawning task id
     * @param session parent session
     * @return child session, empty when the parent has no runtime
     */
    private java.util.Optional<AgentSessionApi> buildChildSession(String taskId, AgentSessionApi session) {
        if (session == null || session.getKvCacheRuntime().isEmpty()) {
            return java.util.Optional.empty();
        }
        String parentSessionId = session.getSessionId();
        String subSessionId = KVCacheSubagentLifecycle
                .resolveSubSessionId(taskId, parentSessionId, null);
        String parentCacheId = KVCacheSubagentLifecycle
                .resolveSubagentParentCacheId(session);
        return java.util.Optional.of(KVCacheSubagentLifecycle
                .createSubagentSession(session, subSessionId, parentCacheId, null));
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
