/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

/**
 * Optional temperature contract for MaTTS parallel scaling.
 *
 * <p>Mirrors Python's optional {@code llm.temperature} attribute handling in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/matts.py}.</p>
 */
public interface MattsTemperatureAware {

    double getTemperature();

    void setTemperature(double temperature);
}
