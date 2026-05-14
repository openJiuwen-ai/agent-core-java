package com.openjiuwen.auto_harness.pipelines;

import java.util.List;

public class ExtendedEvolvePipeline extends BasePipeline {
    @Override public String name() { return AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE; }
    @Override public String description() { return "Extended evolve generation pipeline."; }
    @Override public List<String> expectedOutputs() { return List.of("package_result"); }
}
