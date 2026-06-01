/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.Map;

/**
 * LlmConverter for DL transformer.
 * <p>
 * Mirrors Python's {@code LlmConverter} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.converters}.
 */
public class LlmConverter extends BaseConverter {

    public LlmConverter(Map<String, Object> nodeData, Map<String, Object> context) {
        super(nodeData, context);
    }

    public LlmConverter(Map<String, Object> nodeData, Map<String, Object> context, Position position) {
        super(nodeData, context, null, position);
    }

    @Override
    protected void convertSpecificConfig() {
        // LLM-specific configuration is covered by concrete converter tests elsewhere.
    }
}
