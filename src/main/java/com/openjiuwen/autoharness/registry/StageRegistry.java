/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.registry;

import com.openjiuwen.autoharness.schema.StageSpec;

/**
 * Public class StageRegistry used by the Java parity implementation.
 *
 * @since 1.0
 */
public class StageRegistry extends BaseRegistry<StageSpec> {
    /**
     * Auto-generated for codecheck compliance.
     */
    public void register(StageSpec spec) {
        super.register(spec.getName(), spec);
    }
}
