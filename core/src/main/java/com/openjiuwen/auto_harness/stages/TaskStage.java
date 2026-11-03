/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

/**
 * Base class for task-scoped stages.
 *
 * <p>Mirrors Python's {@code TaskStage} in
 * {@code openjiuwen/auto_harness/stages/base.py}.</p>
 */
public abstract class TaskStage extends BaseStage {

    @Override
    public String scope() {
        return "task";
    }
}
