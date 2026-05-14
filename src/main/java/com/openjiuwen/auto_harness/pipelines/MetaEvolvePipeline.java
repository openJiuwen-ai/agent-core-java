package com.openjiuwen.auto_harness.pipelines;

import java.util.List;

public class MetaEvolvePipeline extends BasePipeline {
    @Override public String name() { return AutoHarnessPipelineNames.META_EVOLVE_PIPELINE; }
    @Override public String description() { return "Default meta evolve pipeline."; }
    @Override public List<String> expectedOutputs() { return List.of("session_results"); }
}
