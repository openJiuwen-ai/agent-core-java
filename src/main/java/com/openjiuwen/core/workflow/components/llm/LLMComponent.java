/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.LLMComponent}
 * with additional constructors for test compatibility.
 * 
 * @since 0.1.7
 */
public class LLMComponent extends com.openjiuwen.core.workflow.component.llm.LLMComponent {
    /**
     * LLMComponent.
     * 
     * @param config config
     * @since 0.1.7
     */
    public LLMComponent(com.openjiuwen.core.workflow.component.llm.LLMCompConfig config) {
        super(config);
    }

    /**
     * Accept our local LLMCompConfig subclass too.
     * 
     * @param config config
     * @since 0.1.7
     */
    public LLMComponent(LLMCompConfig config) {
        super(config);
    }

    /**
     * No-arg constructor — creates LLMComponent with empty config.
     * 
     * @since 0.1.7
     */
    public LLMComponent() {
        super(new com.openjiuwen.core.workflow.component.llm.LLMCompConfig());
    }
}
