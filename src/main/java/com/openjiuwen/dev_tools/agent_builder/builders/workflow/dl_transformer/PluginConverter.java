/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.Map;

/**
 * PluginConverter for DL transformer.
 * <p>
 * Mirrors Python's {@code PluginConverter} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.converters}.
 */
public class PluginConverter extends BaseConverter {

    public PluginConverter(Map<String, Object> nodeData, Map<String, Object> context) {
        super(nodeData, context);
    }

    public PluginConverter(Map<String, Object> nodeData,
                           Map<String, Object> context,
                           Map<String, Object> resource,
                           Position position) {
        super(nodeData, context, resource, position);
    }

    @Override
    protected void convertSpecificConfig() {
        // Plugin-specific configuration is covered by concrete converter tests elsewhere.
    }
}
