/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.registry;

import com.openjiuwen.autoharness.schema.StageSpec;

/**
 * Public class StageRegistry used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class StageRegistry extends BaseRegistry<StageSpec> {
    /**
     * register.
     * 
     * @param spec spec
     * @since 0.1.7
     */
    public void register(StageSpec spec) {
        super.register(spec.getName(), spec);
    }
}
