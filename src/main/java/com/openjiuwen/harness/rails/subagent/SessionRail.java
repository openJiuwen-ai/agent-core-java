/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deprecated shim for async subagent mode.
 *
 * <p>Mirrors Python's {@code SessionRail} in
 * {@code openjiuwen/harness/rails/subagent/session_rail.py}.</p>
 */
public class SessionRail extends SubagentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionRail.class);

    public SessionRail() {
        super(true);
        LOGGER.warn("SessionRail is deprecated; use SubagentRail(enable_async_subagent=True).");
    }
}
