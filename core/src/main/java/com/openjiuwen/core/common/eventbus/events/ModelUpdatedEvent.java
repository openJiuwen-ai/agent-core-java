/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus.events;

/**
 * Event published when a model is added or updated (overwritten) in the registry.
 *
 * @since 0.1.16
 */
public class ModelUpdatedEvent extends ModelChangeEvent {
    /**
     * ModelUpdatedEvent.
     *
     * @param modelId the model ID that was added or updated
     */
    public ModelUpdatedEvent(String modelId) {
        super(modelId);
    }
}
