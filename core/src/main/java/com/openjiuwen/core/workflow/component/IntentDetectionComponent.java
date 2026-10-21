/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

/**
 * Intent-detection component with branch-router access.
 *
 * <p>Mirrors Python's {@code IntentDetectionComponent} in
 * {@code openjiuwen/core/workflow/components/llm/intent_detection_comp.py}.</p>
 */
public class IntentDetectionComponent extends BranchComponent {
    /**
     * Construct an intent component whose executable owns its trace span.
     *
     * @since 0.1.17
     */
    public IntentDetectionComponent() {
        // The executable already owns the intent span; routing must not emit a duplicate.
        super(false);
    }
}
