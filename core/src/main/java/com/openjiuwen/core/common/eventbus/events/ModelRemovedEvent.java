/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus.events;

/**
 * Event published when a model is removed from the registry.
 *
 * @since 0.1.16
 */
public class ModelRemovedEvent extends ModelChangeEvent {
    /**
     * ModelRemovedEvent.
     *
     * @param modelId the model ID that was removed
     */
    public ModelRemovedEvent(String modelId) {
        super(modelId);
    }
}
