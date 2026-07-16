/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.team;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * TeamModelConfig.
 * 
 * @since 0.1.7
 */
public record TeamModelConfig(ModelClientConfig modelClientConfig, ModelRequestConfig modelRequestConfig) {
}
