// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.workflow.components.llm;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.IntentDetectionComponentImpl}
 * with support for both parent and local config types.
 */
public class IntentDetectionComponent
        extends com.openjiuwen.core.workflow.component.llm.IntentDetectionComponentImpl {

    public IntentDetectionComponent(
            com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig config) {
        super(config);
    }

    public IntentDetectionComponent(IntentDetectionCompConfig config) {
        super(config);
    }
}
