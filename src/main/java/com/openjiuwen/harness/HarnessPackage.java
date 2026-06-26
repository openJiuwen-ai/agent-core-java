/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.task_loop.TaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.List;
import java.util.Map;

/**
 * Module facade for the public DeepAgents harness API.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness} module in
 * {@code openjiuwen/harness/__init__.py}.</p>
 */
public final class HarnessPackage {

    public static final String PYTHON_MODULE = "openjiuwen/harness/__init__.py";
    public static final Class<DeepAgent> DEEP_AGENT = DeepAgent.class;
    public static final Class<TaskLoopEventHandler> TASK_LOOP_EVENT_HANDLER = TaskLoopEventHandler.class;
    public static final Class<TaskLoopEventExecutor> TASK_LOOP_EVENT_EXECUTOR = TaskLoopEventExecutor.class;
    public static final Class<DeepAgentConfig> DEEP_AGENT_CONFIG = DeepAgentConfig.class;
    public static final Class<DeepAgentConfig.AudioModelConfig> AUDIO_MODEL_CONFIG =
            DeepAgentConfig.AudioModelConfig.class;
    public static final Class<DeepAgentConfig.VisionModelConfig> VISION_MODEL_CONFIG =
            DeepAgentConfig.VisionModelConfig.class;
    public static final Class<Workspace> WORKSPACE = Workspace.class;

    private HarnessPackage() {
    }

    public static List<Object> exports() {
        return List.of(
                DeepAgent.class,
                TaskLoopEventHandler.class,
                TaskLoopEventExecutor.class,
                DeepAgentConfig.class,
                DeepAgentConfig.AudioModelConfig.class,
                DeepAgentConfig.VisionModelConfig.class,
                "create_deep_agent",
                Workspace.class
        );
    }

    public static Object getAttribute(String name) {
        return switch (name) {
            case "DeepAgent" -> DeepAgent.class;
            case "TaskLoopEventHandler" -> TaskLoopEventHandler.class;
            case "TaskLoopEventExecutor" -> TaskLoopEventExecutor.class;
            case "DeepAgentConfig" -> DeepAgentConfig.class;
            case "AudioModelConfig" -> DeepAgentConfig.AudioModelConfig.class;
            case "VisionModelConfig" -> DeepAgentConfig.VisionModelConfig.class;
            case "create_deep_agent" -> "create_deep_agent";
            case "Workspace" -> Workspace.class;
            default -> throw new IllegalArgumentException(
                    "module 'openjiuwen.harness' has no attribute '" + name + "'"
            );
        };
    }

    public static DeepAgent createDeepAgent(Object model) {
        return DeepAgentFactory.createDeepAgent(model);
    }

    public static DeepAgent createDeepAgent(
            Object model,
            List<Tool> tools,
            Map<String, DeepAgentConfig.SubAgentConfig> subagents
    ) {
        return DeepAgentFactory.createDeepAgent(model, tools, subagents);
    }
}
