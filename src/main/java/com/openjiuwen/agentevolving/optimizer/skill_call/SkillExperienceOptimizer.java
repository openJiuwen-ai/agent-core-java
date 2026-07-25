/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.skill_call;

/**
 * Legacy camelCase-package facade; prefer
 * {@code com.openjiuwen.agent_evolving.optimizer.skill_call.SkillExperienceOptimizer}.
 *
 * @since 1.0
 * @deprecated Use {@code com.openjiuwen.agent_evolving.optimizer.skill_call.SkillExperienceOptimizer} instead.
 */
@Deprecated
public class SkillExperienceOptimizer extends com.openjiuwen.agent_evolving.optimizer.skill_call.SkillExperienceOptimizer {

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillExperienceOptimizer(com.openjiuwen.core.foundation.llm.Model llm, String model) {
        super(llm, model);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillExperienceOptimizer(com.openjiuwen.core.foundation.llm.Model llm, String model, String language) {
        super(llm, model, language);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillExperienceOptimizer(
            com.openjiuwen.core.foundation.llm.Model llm,
            String model,
            String language,
            com.openjiuwen.agent_evolving.optimizer.LlmResilience.LLMInvokePolicy generateRecordsLlmPolicy) {
        super(llm, model, language, generateRecordsLlmPolicy);
    }
}
