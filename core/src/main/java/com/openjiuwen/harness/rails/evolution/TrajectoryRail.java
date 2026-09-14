/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.harness.rails.CallbackContext;

/**
 * Publishes completed trajectory snapshots.
 *
 * <p>Mirrors Python's {@code TrajectoryRail} in
 * {@code openjiuwen/harness/rails/evolution/trajectory_rail.py}.</p>
 */
public class TrajectoryRail extends EvolutionRail {

    @Override
    public void afterInvoke(CallbackContext ctx) {
        super.afterInvoke(ctx);
        ctx.put("trajectory", buildTrajectory());
    }
}
