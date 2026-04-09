/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/** Tool call related event. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ToolEvent extends BaseLogEvent {
    private String toolName;
    private String toolType;
    private String toolDescription;
    private Map<String, Object> arguments;
    private Object result;
    private Double executionTimeMs;
    private String toolCallId;

    public ToolEvent() {
        super();
        setModuleType(ModuleType.TOOL);
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "tool_name", toolName);
        putIfNotNull(map, "tool_type", toolType);
        putIfNotNull(map, "tool_description", toolDescription);
        putIfNotNull(map, "arguments", arguments);
        putIfNotNull(map, "result", result);
        putIfNotNull(map, "execution_time_ms", executionTimeMs);
        putIfNotNull(map, "tool_call_id", toolCallId);
    }
}
