/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline;

import java.util.List;

/**
 * Package facade for the meta evolve pipeline exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.pipelines.meta_evolve_pipeline} in
 * {@code openjiuwen/auto_harness/pipelines/meta_evolve_pipeline/__init__.py}.</p>
 */
public final class MetaEvolvePipelinePackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/auto_harness/pipelines/meta_evolve_pipeline/__init__.py";
    public static final List<String> ALL = List.of(
            "MetaEvolvePipeline",
            "PRTaskPipeline"
    );

    private MetaEvolvePipelinePackage() {
    }

    public static boolean exports(String symbolName) {
        return ALL.contains(symbolName);
    }
}
