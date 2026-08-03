/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

/**
 * Mirrors Python's {@code OnlineEvolutionContext} alias in
 * {@code openjiuwen/agent_evolving/experience/types.py}.
 */
public class OnlineEvolutionContext extends EvolutionContext {

    public OnlineEvolutionContext() {
        super();
    }

    public OnlineEvolutionContext(EvolutionContext context) {
        super(
                context.getSkillName(),
                context.getSignals(),
                context.getSkillContent(),
                context.getMessages(),
                context.getExistingDescRecords(),
                context.getExistingBodyRecords(),
                context.getUserQuery(),
                context.getTrajectory(),
                context.getExistingScriptRecords(),
                context.getMetadata()
        );
    }
}
