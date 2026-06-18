/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

/**
 * Deprecated shim for async subagent mode.
 *
 * <p>Mirrors Python's {@code SessionRail} in
 * {@code openjiuwen/harness/rails/subagent/session_rail.py}.</p>
 */
public class SessionRail extends SubagentRail {
    public SessionRail() {
        super(true);
    }
}
