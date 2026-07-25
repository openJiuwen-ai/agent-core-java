/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import java.util.List;

/**
 * Public experience package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.experience} in
 * {@code openjiuwen/agent_evolving/experience/__init__.py}.</p>
 */
public final class ExperiencePackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/experience/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "OnlineEvolutionContext",
            "OnlineEvolutionResult",
            "OnlineEvolutionStatus",
            "OnlineEvolutionOrchestrator",
            "ExperienceProposal",
            "ExperienceApprovalRequest",
            "ExperienceApplyResult",
            "PendingChange",
            "ExperienceManager",
            "ExperienceTracker",
            "ExperienceScorer"
    );

    private ExperiencePackage() {
    }
}
