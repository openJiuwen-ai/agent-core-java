/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Registry for state-reinjection builders used during full compaction.
 * <p>
 * Each registered builder produces content that should be reinjected into the
 * compacted context (e.g., plan state, task status, skill history).
 * <p>
 * Mirrors Python's {@code FullCompactStateReinjector} from
 * {@code context_engine/processor/compressor/util.py}.
 */
public class FullCompactStateReinjector {

    /**
     * Specification for a single state-reinjection builder.
     */
    public record BuilderSpec(
            String name,
            String label,
            Function<ReinjectContext, Object> builder
    ) {}

    /**
     * Context passed to builder functions.
     */
    public record ReinjectContext(
            Object processor,
            Object context,
            List<?> messages,
            List<?> messagesToKeep
    ) {}

    private final List<BuilderSpec> builders = new ArrayList<>();

    /**
     * Register or replace a builder by name.
     */
    public void registerBuilder(String name, String label, Function<ReinjectContext, Object> builder) {
        BuilderSpec spec = new BuilderSpec(name, label, builder);
        for (int i = 0; i < builders.size(); i++) {
            if (builders.get(i).name().equals(name)) {
                builders.set(i, spec);
                return;
            }
        }
        builders.add(spec);
    }

    /**
     * Return all registered builders as an immutable snapshot.
     */
    public List<BuilderSpec> iterBuilders() {
        return List.copyOf(builders);
    }
}
