/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Session management related event.
 * 
 * @since 0.1.7
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SessionEvent extends BaseLogEvent {
    private String sessionType;
    private String userId;
    private String agentId;
    private String workflowId;
    private Map<String, Object> sessionConfig;
    private Integer messageCount;

    /**
     * SessionEvent.
     * 
     * @since 0.1.7
     */
    public SessionEvent() {
        super();
        setModuleType(ModuleType.SESSION);
    }

    /**
     * addFieldsToMap.
     * 
     * @param map map
     * @since 0.1.7
     */
    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "session_type", sessionType);
        putIfNotNull(map, "user_id", userId);
        putIfNotNull(map, "agent_id", agentId);
        putIfNotNull(map, "workflow_id", workflowId);
        putIfNotNull(map, "session_config", sessionConfig);
        putIfNotNull(map, "message_count", messageCount);
    }
}
