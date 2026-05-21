/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

/**
 * Auto-harness context rail.
 *
 * <p>Context processor rail without workspace/context prompt injection.</p>
 *
 * <p>Mirrors Python's {@code AutoHarnessContextRail} in {@code openjiuwen.auto_harness.rails.context_rail}.</p>
 */
public class ContextRail {

    /**
     * Do not inject workspace/tools/context prompt sections.
     *
     * @param ctx the agent callback context
     */
    public void beforeModelCall(Object ctx) {
        // No injection
    }

    /**
     * Do not mutate system prompt sections on teardown.
     *
     * @param agent the agent
     */
    public void uninit(Object agent) {
        // No mutation
    }
}