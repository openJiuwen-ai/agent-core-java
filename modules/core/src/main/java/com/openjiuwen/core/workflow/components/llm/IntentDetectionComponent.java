/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.IntentDetectionComponentImpl}
 * with support for both parent and local config types.
 * 
 * @since 0.1.7
 */
public class IntentDetectionComponent extends com.openjiuwen.core.workflow.component.llm.IntentDetectionComponentImpl {
    /**
     * IntentDetectionComponent.
     * 
     * @param config config
     * @since 0.1.7
     */
    public IntentDetectionComponent(com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig config) {
        super(config);
    }

    /**
     * IntentDetectionComponent.
     * 
     * @param config config
     * @since 0.1.7
     */
    public IntentDetectionComponent(IntentDetectionCompConfig config) {
        super(config);
    }
}
