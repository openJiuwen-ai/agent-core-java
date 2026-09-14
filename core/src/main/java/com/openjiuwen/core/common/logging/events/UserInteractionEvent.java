/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * User interaction related event.
 *
 * <p>Mirrors Python's {@code UserInteractionEvent} in
 * {@code openjiuwen/core/common/logging/events.py}.</p>
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UserInteractionEvent extends BaseLogEvent {
    private String userId;
    private String inputContent;
    private String feedbackType;
    private String feedbackContent;

    public UserInteractionEvent() {
        super();
        setModuleType(ModuleType.USER);
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "user_id", userId);
        putIfNotNull(map, "input_content", inputContent);
        putIfNotNull(map, "feedback_type", feedbackType);
        putIfNotNull(map, "feedback_content", feedbackContent);
    }
}


