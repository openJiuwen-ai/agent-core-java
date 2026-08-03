/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the signal package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.signal} in
 * {@code openjiuwen/agent_evolving/signal/__init__.py}.</p>
 */
class SignalPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals("openjiuwen/agent_evolving/signal/__init__.py", SignalPackage.PYTHON_MODULE);
        assertEquals("Signal module: evolution signal detection and conversion.", SignalPackage.DESCRIPTION);
        assertEquals(List.of(
                "EvolutionSignal",
                "EvolutionCategory",
                "EvolutionTarget",
                "get_signal_source",
                "make_evolution_signal",
                "make_signal_fingerprint",
                "ConversationSignalDetector",
                "SignalDetector",
                "TeamSignalDetector",
                "TeamSignalType",
                "TrajectoryIssue",
                "UserIntent",
                "build_team_trajectory_summary",
                "get_team_signal_skill_content",
                "get_team_trajectory_issues",
                "from_evaluated_case",
                "from_evaluated_cases",
                "make_team_trajectory_signal",
                "make_team_user_intent_signal",
                "parse_team_model_json"
        ), SignalPackage.EXPORTED_SYMBOLS);
    }
}
