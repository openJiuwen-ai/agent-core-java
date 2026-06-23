/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.core.foundation.llm.Model;

/**
 * Legacy compatibility alias for the team skill experience optimizer.
 *
 * <p>Mirrors Python's {@code TeamSkillOptimizer} alias in
 * {@code openjiuwen/agent_evolving/optimizer/skill_call/team_skill_experience_optimizer.py}.</p>
 */
public class TeamSkillOptimizer extends TeamSkillExperienceOptimizer {

    public TeamSkillOptimizer(Model llm, String model) {
        super(llm, model);
    }

    public TeamSkillOptimizer(Model llm, String model, String language) {
        super(llm, model, language);
    }

    public TeamSkillOptimizer(
            Model llm,
            String model,
            String language,
            String debugDir,
            LlmResilience.LLMInvokePolicy recordLlmPolicy,
            EvolutionStore evolutionStore
    ) {
        super(llm, model, language, debugDir, recordLlmPolicy, evolutionStore);
    }
}
