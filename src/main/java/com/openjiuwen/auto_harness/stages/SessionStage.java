/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

/**
 * Base class for session-scoped stages.
 *
 * <p>Mirrors Python's {@code SessionStage} in
 * {@code openjiuwen/auto_harness/stages/base.py}.</p>
 */
public abstract class SessionStage extends BaseStage {

    @Override
    public String scope() {
        return "session";
    }
}
