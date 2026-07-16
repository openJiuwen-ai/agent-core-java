/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * Questioner workflow component (composable wrapper).
 * <p>
 * Creates a {@link QuestionerExecutable} with initial state.
 * <p>
 * Mirrors Python's {@code QuestionerComponent}.
 * 
 * @since 0.1.7
 */
public class QuestionerComponent implements ComponentComposable {
    private final QuestionerConfig config;

    /**
     * QuestionerComponent.
     * 
     * @param config config
     * @since 0.1.7
     */
    public QuestionerComponent(QuestionerConfig config) {
        this.config = config;
    }

    /**
     * toExecutable.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Executable<?, ?> toExecutable() {
        return new QuestionerExecutable(config).state(new QuestionerState());
    }
}
