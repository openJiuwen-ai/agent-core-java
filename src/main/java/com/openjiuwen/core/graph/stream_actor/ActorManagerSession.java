/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.session.config.SessionConfigAccess;
import com.openjiuwen.core.session.state.WorkflowCommitState;

/**
 * Narrow session surface used by {@link ActorManager}.
 *
 * <p>Mirrors Python's duck-typed {@code session} dependency used by
 * {@code ActorManager} in {@code openjiuwen/core/graph/stream_actor/manager.py}.
 * Python calls {@code session.config().get_env(...)}; Java uses the existing
 * session {@link SessionConfigAccess} rather than a second config type.</p>
 */
public interface ActorManagerSession {

    /**
     * Returns the workflow configuration accessor.
     *
     * @return session config (same surface as Python {@code session.config()})
     */
    SessionConfigAccess config();

    /**
     * Returns workflow state with workflow-level commit helpers.
     *
     * @return workflow commit state
     */
    WorkflowCommitState state();
}
