// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

/**
 * 模型配置Record类。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/mode_info.py - ModelConfig (dataclass)
 * 
 * @param modelProvider 模型提供商标识
 * @param modelInfo 模型信息配置
 */
public record ModelConfig(
    String modelProvider,
    BaseModelInfo modelInfo
) {}

