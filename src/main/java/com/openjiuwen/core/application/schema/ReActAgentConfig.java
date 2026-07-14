/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.schema;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Backward-compatible alias for {@link LlmAgentConfig}.
 *
 * <p>Mirrors Python's {@code LegacyReActAgentConfig} application schema in
 * {@code openjiuwen/core/single_agent/legacy/config.py}.</p>
 */
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReActAgentConfig extends LlmAgentConfig {
}
