/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.toolcall.utils;

import java.util.List;
import java.util.Map;

/**
 * Compatibility facade for the renamed tool_call package.
 *
 * <p>Mirrors Python's {@code customized_pipeline} function in
 * {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.customized_pipline}.</p>
 */
public final class CustomizedPipeline {

    private CustomizedPipeline() {
    }

    public static List<Object> customizedPipeline(
            String stage,
            Map<String, Object> tool,
            Map<String, Object> config,
            Object toolCallable
    ) {
        return com.openjiuwen.agent_evolving.optimizer.tool_call.utils.CustomizedPipeline.customizedPipeline(
                stage,
                tool,
                config,
                toolCallable
        );
    }

    public static Object runPipeline(
            String stage,
            Map<String, Object> tool,
            Map<String, Object> config,
            Object toolCallable
    ) {
        return customizedPipeline(stage, tool, config, toolCallable);
    }

    public static Object runExamplePipeline(Map<String, Object> tool, Map<String, Object> config, Object toolCallable) {
        return runPipeline("example", tool, config, toolCallable);
    }

    public static Object runDescriptionPipeline(Map<String, Object> tool, Map<String, Object> config, Object toolCallable) {
        return runPipeline("description", tool, config, toolCallable);
    }
}
