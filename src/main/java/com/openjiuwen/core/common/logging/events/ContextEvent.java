  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/** Context operation related event. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ContextEvent extends BaseLogEvent {
    private String messageType;
    private String messageContent;
    private String messageRole;
    private Integer contextSize;
    private Integer maxContextSize;

    public ContextEvent() {
        super();
        setModuleType(ModuleType.CONTEXT);
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "message_type", messageType);
        putIfNotNull(map, "message_content", messageContent);
        putIfNotNull(map, "message_role", messageRole);
        putIfNotNull(map, "context_size", contextSize);
        putIfNotNull(map, "max_context_size", maxContextSize);
    }
}
