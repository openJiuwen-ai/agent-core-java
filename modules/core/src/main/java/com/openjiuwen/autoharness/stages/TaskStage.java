/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.stages;

/**
 * TaskStage.
 * 
 * @since 0.1.7
 */
public abstract class TaskStage extends BaseStage {
    /**
     * scope.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String scope() {
        return "task";
    }
}
