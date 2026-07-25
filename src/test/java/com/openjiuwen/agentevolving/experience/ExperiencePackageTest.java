/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the experience package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.experience} in
 * {@code openjiuwen/agent_evolving/experience/__init__.py}.</p>
 */
class ExperiencePackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals("openjiuwen/agent_evolving/experience/__init__.py", ExperiencePackage.PYTHON_MODULE);
        assertEquals(List.of(
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
        ), ExperiencePackage.EXPORTED_SYMBOLS);
    }
}
