/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;

import java.util.concurrent.CompletableFuture;

/**
 * Dynamic agent-flow callback used by parallel MaTTS trajectory generation.
 *
 * <p>Mirrors Python's {@code context.agent_flow(traj_context)} callback in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/matts.py}.</p>
 */
@FunctionalInterface
public interface MattsAgentFlow {

    CompletableFuture<Void> run(RuntimeContext context);
}
