/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.registry;

import com.openjiuwen.autoharness.schema.PipelineSpec;

/**
 * Public class PipelineRegistry used by the Java parity implementation.
 *
 * @since 1.0
 */
public class PipelineRegistry extends BaseRegistry<PipelineSpec> {
    /**
     * Auto-generated for codecheck compliance.
     */
    public void register(PipelineSpec spec) {
        super.register(spec.getName(), spec);
    }
}
