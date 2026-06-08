/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines;

import java.util.Map;

/**
 * Built-in pipeline name constants and legacy alias normalization.
 * <p>
 * Mirrors Python's module constants and helper in
 * {@code openjiuwen/auto_harness/pipelines/__init__.py}.
 */
public final class AutoHarnessPipelineNames {

    public static final String META_EVOLVE_PIPELINE = "meta_evolve_pipeline";
    public static final String EXTENDED_EVOLVE_PIPELINE = "extended_evolve_pipeline";

    private static final Map<String, String> PIPELINE_NAME_ALIASES = Map.of(
            "pr_pipeline", META_EVOLVE_PIPELINE,
            "extended_harness_pipeline", EXTENDED_EVOLVE_PIPELINE
    );

    private AutoHarnessPipelineNames() {
    }

    public static String normalizePipelineName(String name) {
        return PIPELINE_NAME_ALIASES.getOrDefault(name, name);
    }
}
