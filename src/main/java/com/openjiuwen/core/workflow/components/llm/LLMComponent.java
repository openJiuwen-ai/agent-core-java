/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.LLMComponent}
 * with additional constructors for test compatibility.
 *
 * <p>Mirrors Python's {@code LLMComponent} in
 * {@code openjiuwen/core/workflow/components/llm/llm_comp.py}.</p>
 */
public class LLMComponent extends com.openjiuwen.core.workflow.component.llm.LLMComponent {

    public LLMComponent(com.openjiuwen.core.workflow.component.llm.LLMCompConfig config) {
        super(config);
    }

    /** Accept our local LLMCompConfig subclass too. */
    public LLMComponent(LLMCompConfig config) {
        super(config);
    }

    /** No-arg constructor — creates LLMComponent with empty config. */
    public LLMComponent() {
        super(new com.openjiuwen.core.workflow.component.llm.LLMCompConfig());
    }
}
