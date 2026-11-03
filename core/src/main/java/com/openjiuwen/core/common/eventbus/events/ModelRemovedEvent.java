/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus.events;

/**
 * Published when a model is removed from the registry.
 *
 * @since 0.1.15
 */
public class ModelRemovedEvent extends ModelChangeEvent {
    /**
     * Creates a removal event for the given model id.
     *
     * @param modelId the model id that was removed
     */
    public ModelRemovedEvent(String modelId) {
        super(modelId);
    }
}
