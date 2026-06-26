/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import com.openjiuwen.harness.schema.task.ModelUsageRecord;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Module facade for DeepAgent schema definitions.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/schema/__init__.py}.</p>
 */
public final class HarnessSchemaPackage {

    public static final Class<AgentMode> AGENT_MODE = AgentMode.class;
    public static final Class<DeepAgentConfig> DEEP_AGENT_CONFIG = DeepAgentConfig.class;
    public static final Class<DeepAgentConfig.AudioModelConfig> AUDIO_MODEL_CONFIG =
            DeepAgentConfig.AudioModelConfig.class;
    public static final Class<DeepAgentConfig.VisionModelConfig> VISION_MODEL_CONFIG =
            DeepAgentConfig.VisionModelConfig.class;
    public static final Class<DeepAgentConfig.SubAgentConfig> SUB_AGENT_CONFIG =
            DeepAgentConfig.SubAgentConfig.class;
    public static final Class<DeepLoopEvent> DEEP_LOOP_EVENT = DeepLoopEvent.class;
    public static final Class<DeepLoopEventType> DEEP_LOOP_EVENT_TYPE = DeepLoopEventType.class;
    public static final Class<DeepAgentState> DEEP_AGENT_STATE = DeepAgentState.class;
    public static final Class<PlanModeState> PLAN_MODE_STATE = PlanModeState.class;
    public static final Class<ModelUsageRecord> MODEL_USAGE_RECORD = ModelUsageRecord.class;
    public static final Class<TaskPlan> TASK_PLAN = TaskPlan.class;
    public static final Class<TodoItem> TODO_ITEM = TodoItem.class;
    public static final Class<TodoStatus> TODO_STATUS = TodoStatus.class;
    public static final Map<TodoStatus, String> STATUS_ICONS = statusIcons();

    private HarnessSchemaPackage() {
    }

    public static List<Object> exports() {
        return List.of(
                AgentMode.class,
                DeepAgentConfig.class,
                DeepAgentConfig.AudioModelConfig.class,
                DeepAgentConfig.VisionModelConfig.class,
                DeepAgentConfig.SubAgentConfig.class,
                DeepLoopEvent.class,
                DeepLoopEventType.class,
                "create_loop_event",
                "default_event_priority",
                DeepAgentState.class,
                PlanModeState.class,
                ModelUsageRecord.class,
                STATUS_ICONS,
                TaskPlan.class,
                TodoItem.class,
                TodoStatus.class
        );
    }

    public static DeepLoopEvent createLoopEvent(int seq,
                                                DeepLoopEventType eventType,
                                                String content,
                                                String taskId,
                                                Map<String, Object> metadata,
                                                Integer priority) {
        return DeepLoopEvent.create(seq, eventType, content, taskId, metadata, priority);
    }

    public static int defaultEventPriority(DeepLoopEventType eventType) {
        return eventType == null ? DeepLoopEventType.FOLLOWUP.getDefaultPriority() : eventType.getDefaultPriority();
    }

    private static Map<TodoStatus, String> statusIcons() {
        Map<TodoStatus, String> icons = new LinkedHashMap<>();
        for (TodoStatus status : TodoStatus.values()) {
            icons.put(status, status.getStatusIcon());
        }
        return Map.copyOf(icons);
    }
}
