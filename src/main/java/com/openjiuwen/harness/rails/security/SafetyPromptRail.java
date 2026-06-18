/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.SafetySection;
import com.openjiuwen.harness.rails.CallbackContext;

/**
 * Rail that injects the safety prompt section into model-call context.
 *
 * <p>Mirrors Python's {@code SafetyPromptRail} in
 * {@code openjiuwen/harness/rails/security/prompt_security_rail.py}.</p>
 */
public class SafetyPromptRail extends BaseSecurityRail {

    public SafetyPromptRail() {
        setPriority(85);
        setSupportedEvents(java.util.Set.of(BEFORE_MODEL_CALL));
    }

    @Override
    protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
        CallbackContext ctx = securityCtx.callbackContext();
        String language = String.valueOf(ctx.getValues().getOrDefault("language", "cn"));
        PromptSection section = SafetySection.buildSafetySection(language);
        ctx.put("safety_section", section);
        return allow();
    }
}
