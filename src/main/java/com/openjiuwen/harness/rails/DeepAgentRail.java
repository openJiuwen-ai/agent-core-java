/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.workspace.Workspace;

/**
 * Base rail for harness-specific integrations.
 *
 * <p>Java-side supporting abstraction for Python harness rails such as those
 * under {@code openjiuwen.harness.rails}.
 */
public abstract class DeepAgentRail extends AgentRail {

    protected SysOperation sysOperation;
    protected Workspace workspace;

    public void setSysOperation(SysOperation sysOperation) {
        this.sysOperation = sysOperation;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public void init(Object agent) {
        // optional override
    }

    public void uninit(Object agent) {
        // optional override
    }
}
