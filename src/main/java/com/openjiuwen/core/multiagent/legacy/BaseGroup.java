/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy;

import com.openjiuwen.core.session.AgentGroupSessionApi;

import java.util.Iterator;

/**
 * BaseGroup.
 * 
 * @since 0.1.7
 */
@Deprecated
public abstract class BaseGroup extends LegacyBaseGroup {
    /**
     * BaseGroup.
     * 
     * @param config config
     * @since 0.1.7
     */
    protected BaseGroup(AgentGroupConfig config) {
        super(config);
    }

    /**
     * invoke.
     * 
     * @param message message
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public abstract Object invoke(Object message, AgentGroupSessionApi session);

    /**
     * stream.
     * 
     * @param message message
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public abstract Iterator<Object> stream(Object message, AgentGroupSessionApi session);
}
