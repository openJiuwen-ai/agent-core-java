/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Registry for state reinjection builders used by full context compaction.
 *
 * <p>Mirrors Python's {@code FullCompactStateReinjector} in
 * {@code openjiuwen/core/context_engine/processor/compressor/util.py}.</p>
 */
public class FullCompactStateReinjector {

    private final List<BuilderSpec> builders = new ArrayList<>();

    public void registerBuilder(String name, String label, Function<BuildContext, Object> builder) {
        BuilderSpec spec = new BuilderSpec(name, label, builder);
        for (int index = 0; index < builders.size(); index++) {
            if (builders.get(index).name().equals(name)) {
                builders.set(index, spec);
                return;
            }
        }
        builders.add(spec);
    }

    public List<BuilderSpec> iterBuilders() {
        return List.copyOf(builders);
    }

    /**
     * Mirrors Python's {@code ReinjectedStateBuilderSpec} in
     * {@code openjiuwen/core/context_engine/processor/compressor/util.py}.
     */
    public record BuilderSpec(String name, String label, Function<BuildContext, Object> builder) {
    }

    /**
     * Builder input corresponding to Python keyword arguments passed to reinjection builders.
     *
     * <p>Mirrors Python's builder call contract in
     * {@code openjiuwen/core/context_engine/processor/compressor/util.py}.</p>
     */
    public record BuildContext(FullCompactProcessor processor, SessionModelContext context,
                               List<BaseMessage> messages, List<BaseMessage> messagesToKeep) {
    }
}
