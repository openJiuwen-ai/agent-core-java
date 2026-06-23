/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.singleagent.legacy.agent.BaseAgent;

/**
 * Root-package alias for the legacy base agent export.
 *
 * <p>Mirrors Python's {@code BaseAgent} re-exported as {@code LegacyBaseAgent} from
 * {@code openjiuwen/core/single_agent/legacy/agent.py}.</p>
 */
public abstract class LegacyBaseAgent extends BaseAgent {
    protected LegacyBaseAgent(Object agentConfig) {
        super(agentConfig);
    }
}
