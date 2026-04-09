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
 */
public class QuestionerComponent implements ComponentComposable {

    private final QuestionerConfig config;

    public QuestionerComponent(QuestionerConfig config) {
        this.config = config;
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return new QuestionerExecutable(config).state(new QuestionerState());
    }
}
