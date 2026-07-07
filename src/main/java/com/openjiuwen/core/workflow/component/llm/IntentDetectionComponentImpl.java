/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.workflow.component.IntentDetectionComponent;

/**
 * Intent-detection component implementation with router access.
 *
 * <p>Mirrors Python's {@code IntentDetectionComponent} in
 * {@code openjiuwen/core/workflow/components/llm/intent_detection_comp.py}.</p>
 */
public class IntentDetectionComponentImpl extends IntentDetectionComponent {

    private final IntentDetectionCompConfig config;

    public IntentDetectionComponentImpl() {
        this(new IntentDetectionCompConfig());
    }

    public IntentDetectionComponentImpl(IntentDetectionCompConfig config) {
        this.config = config == null ? new IntentDetectionCompConfig() : config;
    }

    public IntentDetectionCompConfig getConfig() {
        return config;
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return new IntentDetectionExecutable(config).setRouter(router());
    }
}
