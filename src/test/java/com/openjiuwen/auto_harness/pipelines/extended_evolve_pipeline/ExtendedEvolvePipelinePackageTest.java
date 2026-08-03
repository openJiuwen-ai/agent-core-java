/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness.pipelines.extended_evolve_pipeline} in
 * {@code openjiuwen/auto_harness/pipelines/extended_evolve_pipeline/__init__.py}.
 */
class ExtendedEvolvePipelinePackageTest {

    @Test
    void exportsPipelineClassesInPythonAllOrder() {
        assertEquals(
                "openjiuwen/auto_harness/pipelines/extended_evolve_pipeline/__init__.py",
                ExtendedEvolvePipelinePackage.PYTHON_MODULE
        );
        assertEquals(List.of(
                "ExtendedEvolvePipeline",
                "ExtensionTaskPipeline"
        ), ExtendedEvolvePipelinePackage.ALL);
    }

    @Test
    void exportsOnlyPythonAllSymbols() {
        assertTrue(ExtendedEvolvePipelinePackage.exports("ExtendedEvolvePipeline"));
        assertTrue(ExtendedEvolvePipelinePackage.exports("ExtensionTaskPipeline"));
        assertFalse(ExtendedEvolvePipelinePackage.exports("MetaEvolvePipeline"));
    }
}
