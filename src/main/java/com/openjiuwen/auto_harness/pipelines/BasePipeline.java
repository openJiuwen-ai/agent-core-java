package com.openjiuwen.auto_harness.pipelines;

import com.openjiuwen.auto_harness.schema.PipelineSpec;

import java.util.List;

/**
 * Mirrors Python's {@code BasePipeline} in {@code openjiuwen.auto_harness.pipelines.base}.
 */
public abstract class BasePipeline {

    public abstract String name();

    public String description() {
        return "";
    }

    public List<String> expectedOutputs() {
        return List.of();
    }

    public PipelineSpec spec() {
        return new PipelineSpec(name(), getClass(), description(), expectedOutputs());
    }
}
