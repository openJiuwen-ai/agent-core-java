/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.Map;

/**
 * Mirrors Python's {@code State} in
 * {@code openjiuwen/core/session/state/base.py}.
 */
public interface State extends RecoverableState, SessionStateAccess {
    String IO_STATE_KEY = "io_state";
    String IO_STATE_UPDATES_KEY = "io_state_updates";
    String GLOBAL_STATE_KEY = "global_state";
    String GLOBAL_STATE_UPDATES_KEY = "global_state_updates";
    String COMP_STATE_KEY = "comp_state";
    String WORKFLOW_STATE_KEY = "workflow_state";
    String AGENT_STATE_KEY = "agent_state";
    String COMP_STATE_UPDATES_KEY = "comp_state_updates";
    String WORKFLOW_STATE_UPDATES_KEY = "workflow_state_updates";
    String DEFAULT_NODE_ID = "default";
    String DEFAULT_WORKFLOW_ID = "workflow";

    Object getGlobal(Object key);

    void updateGlobal(Map<String, Object> data);

    void updateTrace(Object span);

    void update(Map<String, Object> data);

    Object get(Object key);

    Map<String, Object> getState();

    void setState(Map<String, Object> state);

    Map<String, Object> dump();
}
