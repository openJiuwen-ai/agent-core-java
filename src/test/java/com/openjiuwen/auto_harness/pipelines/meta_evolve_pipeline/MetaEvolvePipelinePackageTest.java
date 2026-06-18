/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness.pipelines.meta_evolve_pipeline} in
 * {@code openjiuwen/auto_harness/pipelines/meta_evolve_pipeline/__init__.py}.
 */
class MetaEvolvePipelinePackageTest {

    @Test
    void exportsPipelineClassesInPythonAllOrder() {
        assertEquals(
                "openjiuwen/auto_harness/pipelines/meta_evolve_pipeline/__init__.py",
                MetaEvolvePipelinePackage.PYTHON_MODULE
        );
        assertEquals(List.of(
                "MetaEvolvePipeline",
                "PRTaskPipeline"
        ), MetaEvolvePipelinePackage.ALL);
    }

    @Test
    void exportsOnlyPythonAllSymbols() {
        assertTrue(MetaEvolvePipelinePackage.exports("MetaEvolvePipeline"));
        assertTrue(MetaEvolvePipelinePackage.exports("PRTaskPipeline"));
        assertFalse(MetaEvolvePipelinePackage.exports("ExtendedEvolvePipeline"));
    }
}
