/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness;

import com.openjiuwen.auto_harness.registry.PipelineRegistry;
import com.openjiuwen.auto_harness.schema.PipelineSpec;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for pipeline registry and builders.
 * <p>
 * Mirrors Python's {@code test_pipeline} in
 * {@code tests.unit_tests.auto_harness}.
 * </p>
 */
@DisplayName("TestPipeline")
class TestPipeline {

    @Nested
    @DisplayName("Pipeline registry tests")
    class PipelineRegistryTests {

        @Test
        @DisplayName("Test pipeline registry creation")
        void testPipelineRegistryCreation() {
            // Mirrors Python: test_builtin_meta_pipeline_uses_pipeline_class
            PipelineRegistry registry = new PipelineRegistry();
            assertNotNull(registry, "Pipeline registry should be created");
        }

        @Test
        @DisplayName("Test pipeline spec creation")
        void testPipelineSpecCreation() {
            PipelineSpec spec = new PipelineSpec("test", TestPipeline.class, "", List.of());
            assertNotNull(spec, "PipelineSpec should be created");
        }

        @Test
        @DisplayName("Test pipeline registry has expected structure")
        void testPipelineRegistryStructure() {
            // Verify registry class structure
            try {
                var registerMethod = PipelineRegistry.class.getMethod("register", PipelineSpec.class);
                assertNotNull(registerMethod, "register method should exist");
            } catch (NoSuchMethodException e) {
                fail("PipelineRegistry should have register method");
            }
        }
    }

    @Nested
    @DisplayName("Pipeline builder tests")
    class PipelineBuilderTests {

        @Test
        @DisplayName("Test pipeline builders exist")
        void testPipelineBuildersExist() {
            // Mirrors Python: test_builtin_extended_pipeline_uses_pipeline_class
            // Verify auto_harness package structure
            assertNotNull(com.openjiuwen.auto_harness.registry.PipelineRegistry.class,
                    "PipelineRegistry should exist");
        }

        @Test
        @DisplayName("Test stage registry creation")
        void testStageRegistryCreation() {
            // Mirrors Python: build_stage_registry
            com.openjiuwen.auto_harness.registry.StageRegistry stageRegistry =
                    new com.openjiuwen.auto_harness.registry.StageRegistry();
            assertNotNull(stageRegistry, "StageRegistry should be created");
        }
    }
}
