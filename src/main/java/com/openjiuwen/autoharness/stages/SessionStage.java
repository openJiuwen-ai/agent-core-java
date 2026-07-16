/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.stages;

/**
 * SessionStage.
 * 
 * @since 0.1.7
 */
public abstract class SessionStage extends BaseStage {
    /**
     * scope.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String scope() {
        return "session";
    }
}
