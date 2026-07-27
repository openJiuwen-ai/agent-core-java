/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.signal;

import java.util.List;

/**
 * Public signal package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.signal} in
 * {@code openjiuwen/agent_evolving/signal/__init__.py}.</p>
 */
public final class SignalPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/signal/__init__.py";
    public static final String DESCRIPTION = "Signal module: evolution signal detection and conversion.";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
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
    );

    private SignalPackage() {
    }
}
