/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent;

/**
 * Compatibility alias for Python's {@code TeamConfig}.
 *
 * <p>The existing Java {@link GroupConfig} already matches the Python
 * multi-agent runtime knobs, so this type keeps the Python naming surface
 * without introducing a second config model.</p>
 */
public class TeamConfig extends GroupConfig {
}
