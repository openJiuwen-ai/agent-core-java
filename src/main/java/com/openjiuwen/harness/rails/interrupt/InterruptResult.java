/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.single_agent.interrupt.InterruptRequest;

/**
 * Decision to interrupt and wait for user input.
 *
 * <p>Mirrors Python's {@code InterruptResult} in
 * {@code openjiuwen/harness/rails/interrupt/interrupt_base.py}.</p>
 *
 * @param request interrupt request payload
 */
public record InterruptResult(InterruptRequest request) implements InterruptDecision {
}
