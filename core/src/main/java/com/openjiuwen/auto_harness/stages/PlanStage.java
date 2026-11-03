/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;

import java.util.Iterator;
import java.util.List;

/**
 * Abstract base for all plan-family stages.
 *
 * <p>Mirrors Python's {@code PlanStage} in
 * {@code openjiuwen/auto_harness/stages/plan.py}.</p>
 */
public abstract class PlanStage extends SessionStage {

    @Override
    public String name() {
        return "plan";
    }

    @Override
    public String slot() {
        return "plan";
    }

    @Override
    public String displayName() {
        return "制定优化计划";
    }

    @Override
    public String description() {
        return "Plan optimization tasks.";
    }

    @Override
    public List<String> produces() {
        return List.of("task_plan");
    }

    @Override
    public abstract Iterator<Object> stream(BaseExecutionContext ctx);
}
