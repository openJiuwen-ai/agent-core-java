/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus.events;

/**
 * Base event for model registry lifecycle changes.
 *
 * @since 0.1.15
 */
public abstract class ModelChangeEvent {
    private final String modelId;

    /**
     * Creates an event for the given model id.
     *
     * @param modelId the affected model id
     */
    protected ModelChangeEvent(String modelId) {
        this.modelId = modelId;
    }

    /**
     * Returns the affected model id.
     *
     * @return the model id
     */
    public String getModelId() {
        return modelId;
    }
}
