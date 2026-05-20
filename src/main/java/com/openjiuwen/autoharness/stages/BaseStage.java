/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.stages;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.schema.StageResult;

import java.util.List;

/**
 * Auto-generated for codecheck compliance.
 */
public abstract class BaseStage {
    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract String name();
    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract String description();

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> consumes() {
        return List.of();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> produces() {
        return List.of();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String scope() {
        return "session";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public StageResult run(BaseExecutionContext ctx) {
        return StageResult.builder().build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> stream(BaseExecutionContext ctx) {
        return List.of(run(ctx));
    }
}
