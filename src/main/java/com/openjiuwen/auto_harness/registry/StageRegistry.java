package com.openjiuwen.auto_harness.registry;

import com.openjiuwen.auto_harness.schema.StageSpec;

/**
 * Mirrors Python's {@code StageRegistry} in {@code openjiuwen.auto_harness.registry.base}.
 */
public class StageRegistry extends BaseRegistry<StageSpec> {
    public StageRegistry() {
        super(StageSpec::getName);
    }

    public void register(StageSpec spec) {
        super.register(spec);
    }

    @Override
    public StageSpec get(String name) {
        return super.get(name);
    }

    @Override
    public StageSpec require(String name) {
        return super.require(name);
    }
}
