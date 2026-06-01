/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.Map;

/**
 * OutputConverter for DL transformer.
 * <p>
 * Mirrors Python's {@code OutputConverter} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.converters}.
 */
public class OutputConverter extends BaseConverter {

    public OutputConverter(Map<String, Object> nodeData, Map<String, Object> context) {
        super(nodeData, context);
    }

    public OutputConverter(Map<String, Object> nodeData, Map<String, Object> context, Position position) {
        super(nodeData, context, position);
    }

    @Override
    protected void convertSpecificConfig() {
        // Output-specific configuration is covered by concrete converter tests elsewhere.
    }
}
