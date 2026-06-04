package com.openjiuwen.auto_harness.pipelines;

import java.util.List;

/**
 * Mirrors Python's {@code ExtendedEvolvePipeline} in
 * {@code openjiuwen.auto_harness.pipelines.extended_evolve_pipeline.extended_evolve_pipeline}.
 */
public class ExtendedEvolvePipeline extends BasePipeline {
    @Override public String name() { return AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE; }
    @Override public String description() { return "Extended evolve generation pipeline."; }
    @Override public List<String> expectedOutputs() { return List.of("package_result"); }
}
