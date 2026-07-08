/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.singleagent.schema.AgentCard;

/**
 * Root-package facade for the current ReAct agent implementation.
 *
 * <p>Mirrors Python's {@code ReActAgent} in
 * {@code openjiuwen/core/single_agent/agents/react_agent.py}.</p>
 */
public class ReActAgent extends com.openjiuwen.core.singleagent.agents.ReActAgent {

    public ReActAgent(AgentCard card) {
        super(card);
    }
}
