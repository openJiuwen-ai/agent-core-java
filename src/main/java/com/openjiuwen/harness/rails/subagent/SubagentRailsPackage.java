/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.List;

/**
 * Module facade for subagent rails.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/rails/subagent/__init__.py}.</p>
 */
public final class SubagentRailsPackage {

    private SubagentRailsPackage() {
    }

    public static List<Class<? extends DeepAgentRail>> exportedRails() {
        return List.of(SubagentRail.class, SessionRail.class, VerificationRail.class, VerificationContractRail.class);
    }
}
