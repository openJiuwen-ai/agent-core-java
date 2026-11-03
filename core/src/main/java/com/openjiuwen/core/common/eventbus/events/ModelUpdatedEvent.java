/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus.events;

/**
 * Published when a model is added or overwritten in the registry.
 *
 * @since 0.1.15
 */
public class ModelUpdatedEvent extends ModelChangeEvent {
    /**
     * Creates an update event for the given model id.
     *
     * @param modelId the model id that was added or updated
     */
    public ModelUpdatedEvent(String modelId) {
        super(modelId);
    }
}
