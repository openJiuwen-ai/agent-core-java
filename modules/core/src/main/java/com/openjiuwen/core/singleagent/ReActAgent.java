/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.singleagent.schema.AgentCard;

/**
 * Top-level alias mirroring Python's {@code openjiuwen.core.single_agent.ReActAgent} export.
 * 
 * @since 0.1.7
 */
public class ReActAgent extends com.openjiuwen.core.singleagent.agents.ReActAgent {
    /**
     * ReActAgent.
     * 
     * @param card card
     * @since 0.1.7
     */
    public ReActAgent(AgentCard card) {
        super(card);
    }
}
