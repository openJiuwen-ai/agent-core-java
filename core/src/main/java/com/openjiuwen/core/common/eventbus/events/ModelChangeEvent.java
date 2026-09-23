/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus.events;

/**
 * Base class for model lifecycle change events.
 * <p>
 * Carries the modelId that was affected.
 *
 * @since 0.1.16
 */
public abstract class ModelChangeEvent {
    private final String modelId;

    /**
     * ModelChangeEvent.
     *
     * @param modelId the model ID that changed
     */
    protected ModelChangeEvent(String modelId) {
        this.modelId = modelId;
    }

    /**
     * Get the model ID that changed.
     *
     * @return the model ID
     */
    public String getModelId() {
        return modelId;
    }
}
