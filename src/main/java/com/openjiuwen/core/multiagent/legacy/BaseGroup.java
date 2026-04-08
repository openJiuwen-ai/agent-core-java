/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy;

import com.openjiuwen.core.session.AgentGroupSessionApi;

import java.util.Iterator;

/**
 * Legacy package-level alias for {@link LegacyBaseGroup}.
 * <p>
 * Keeps the same import name as Python's deprecated
 * {@code openjiuwen.core.multi_agent.legacy.BaseGroup}.
 *
 * @deprecated Use {@link com.openjiuwen.core.multiagent.BaseGroup}.
 */
@Deprecated
public abstract class BaseGroup extends LegacyBaseGroup {

    protected BaseGroup(AgentGroupConfig config) {
        super(config);
    }

    @Override
    public abstract Object invoke(Object message, AgentGroupSessionApi session);

    @Override
    public abstract Iterator<Object> stream(Object message, AgentGroupSessionApi session);
}
