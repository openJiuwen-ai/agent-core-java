/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.session.state.WorkflowCommitState;

/**
 * Narrow session surface used by {@link ActorManager}.
 *
 * <p>Mirrors Python's duck-typed {@code session} dependency used by
 * {@code ActorManager} in {@code openjiuwen/core/graph/stream_actor/manager.py}.</p>
 */
public interface ActorManagerSession {

    /**
     * Returns the workflow configuration accessor.
     *
     * @return configuration accessor
     */
    ConfigView config();

    /**
     * Returns workflow state with workflow-level commit helpers.
     *
     * @return workflow commit state
     */
    WorkflowCommitState state();

    /**
     * Configuration access used by the Python manager's {@code session.config().get_env(...)} call.
     *
     * <p>Mirrors Python's config dependency used by {@code ActorManager} in
     * {@code openjiuwen/core/graph/stream_actor/manager.py}.</p>
     */
    interface ConfigView {

        /**
         * Gets an environment value.
         *
         * @param key config key
         * @return configured value or {@code null}
         */
        Object getEnv(String key);
    }
}
