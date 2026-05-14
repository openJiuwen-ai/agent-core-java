package com.openjiuwen.auto_harness.registry;

import com.openjiuwen.auto_harness.schema.StageSpec;

/**
 * Mirrors Python's {@code StageRegistry} in {@code openjiuwen.auto_harness.registry.base}.
 */
public class StageRegistry extends BaseRegistry<StageSpec> {
    public StageRegistry() {
        super(StageSpec::getName);
    }
}
