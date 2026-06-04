package com.openjiuwen.auto_harness.registry;

import com.openjiuwen.auto_harness.schema.PipelineSpec;

/**
 * Mirrors Python's {@code PipelineRegistry} in {@code openjiuwen.auto_harness.registry.base}.
 */
public class PipelineRegistry extends BaseRegistry<PipelineSpec> {
    public PipelineRegistry() {
        super(PipelineSpec::getName);
    }

    public void register(PipelineSpec spec) {
        super.register(spec);
    }

    @Override
    public PipelineSpec get(String name) {
        return super.get(name);
    }

    @Override
    public PipelineSpec require(String name) {
        return super.require(name);
    }
}
