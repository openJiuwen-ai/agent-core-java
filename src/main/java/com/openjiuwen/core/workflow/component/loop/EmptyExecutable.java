/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.session.BaseSession;

/**
 * No-op executable used as a placeholder node in the loop graph.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.EmptyExecutable}.
 */
public class EmptyExecutable extends Executable<Object, Object> {

    @Override
    public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
        return null;
    }

    @Override
    public boolean skipTrace() {
        return true;
    }
}
