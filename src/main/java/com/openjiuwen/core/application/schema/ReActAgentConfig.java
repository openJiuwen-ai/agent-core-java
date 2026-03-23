/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.schema;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Backward-compatible alias for {@link LlmAgentConfig}.
 */
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReActAgentConfig extends LlmAgentConfig {
}
