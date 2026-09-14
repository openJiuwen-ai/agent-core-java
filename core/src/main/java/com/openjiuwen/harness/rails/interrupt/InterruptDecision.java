/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

/**
 * Base decision type for interrupt resume.
 *
 * <p>Mirrors Python's {@code InterruptDecision} in
 * {@code openjiuwen/harness/rails/interrupt/interrupt_base.py}.</p>
 */
public sealed interface InterruptDecision permits ApproveResult, RejectResult, InterruptResult {
}
