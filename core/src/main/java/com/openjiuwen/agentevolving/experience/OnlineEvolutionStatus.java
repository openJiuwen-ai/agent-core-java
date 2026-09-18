/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import java.util.Set;

/**
 * Mirrors Python's {@code OnlineEvolutionStatus} literals in
 * {@code openjiuwen/agent_evolving/experience/types.py}.
 */
public final class OnlineEvolutionStatus {

    public static final String STAGED = "staged";
    public static final String AUTO_APPROVED = "auto_approved";
    public static final String NO_EVOLUTION_NO_RECORDS = "no_evolution_no_records";
    public static final String SKIPPED_NO_INPUT = "skipped_no_input";
    public static final String SKIPPED_SKILL_NOT_FOUND = "skipped_skill_not_found";
    public static final String SKIPPED_SKILL_DEFINITION_NOT_FOUND = "skipped_skill_definition_not_found";
    public static final String PERSISTENCE_FAILED = "persistence_failed";
    public static final String GENERATION_FAILED = "generation_failed";

    public static final Set<String> OUTCOME_STATUSES = Set.of(
            NO_EVOLUTION_NO_RECORDS,
            GENERATION_FAILED,
            PERSISTENCE_FAILED,
            SKIPPED_SKILL_DEFINITION_NOT_FOUND
    );

    private OnlineEvolutionStatus() {
    }
}
