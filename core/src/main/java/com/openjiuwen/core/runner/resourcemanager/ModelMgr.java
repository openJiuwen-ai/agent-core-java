/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * Compatible alias for {@link ModelManager}.
 *
 * @since 0.1.12
 */
public class ModelMgr extends ModelManager {

    /**
     * Creates a ModelMgr that uses the shared EventBus.
     */
    public ModelMgr() {
        super();
    }

    /**
     * Creates a ModelMgr bound to a specific EventBus.
     *
     * @param eventBus event bus for lifecycle events
     * @since 0.1.15
     */
    public ModelMgr(com.openjiuwen.core.common.eventbus.EventBus eventBus) {
        super(eventBus);
    }

    public String kind() {
        return "model";
    }
}
