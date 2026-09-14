/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.QuestionerComponent}.
 * <p>
 * Mirrors Python's {@code QuestionerComponent} in
 * {@code openjiuwen/core/workflow/components/llm/questioner_comp.py}.
 */
public class QuestionerComponent
        extends com.openjiuwen.core.workflow.component.llm.QuestionerComponent {

    public QuestionerComponent(com.openjiuwen.core.workflow.component.llm.QuestionerConfig config) {
        super(config);
    }

    public QuestionerComponent(QuestionerConfig config) {
        super(config);
    }
}
