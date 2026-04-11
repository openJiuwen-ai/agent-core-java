/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.config;

/**
 * Backward-compatible alias for {@link LegacyReActAgentConfig}.
 *
 * <p>Mirrors Python's {@code ReActAgentConfig = LegacyReActAgentConfig} alias
 * in {@code single_agent/legacy/config.py}.</p>
 *
 * @deprecated Use {@link LegacyReActAgentConfig} or the modern
 *             {@code openjiuwen.core.singleagent.agents.ReActAgentConfig} instead.
 */
@Deprecated(since = "0.1.7", forRemoval = true)
public class ReActAgentConfig extends LegacyReActAgentConfig {

    public ReActAgentConfig() {
        super();
        com.openjiuwen.core.singleagent.legacy.LegacyApi
                .emitDeprecationWarning("ReActAgentConfig",
                        "openjiuwen.core.singleagent.agents.ReActAgentConfig");
    }
}
