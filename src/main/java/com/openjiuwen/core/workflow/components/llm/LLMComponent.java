/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.components.llm;

import com.openjiuwen.core.workflow.component.llm.LLMExecutable;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.LLMComponent}
 * with additional constructors for test compatibility.
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
