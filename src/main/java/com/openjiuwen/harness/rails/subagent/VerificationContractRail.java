/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.Map;

/**
 * Rail that injects the verification gate into the parent DeepAgent.
 *
 * <p>Mirrors Python's {@code VerificationContractRail} in
 * {@code openjiuwen/harness/rails/subagent/verification_contract_rail.py}.</p>
 */
public class VerificationContractRail extends DeepAgentRail {

    private static final int CONTRACT_PRIORITY = 88;
    private static final String CONTRACT_EN = """
            ## Verification Gate

            After any non-trivial implementation turn, spawn the verification agent before reporting completion.
            Non-trivial means three or more file edits, backend/API/service changes, or infrastructure changes.
            On PASS, spot-check verifier commands before reporting completion. On FAIL, fix and re-verify.
            Only the verification agent can issue PASS, FAIL, or PARTIAL.
            """;
    private static final String CONTRACT_CN = CONTRACT_EN;

    public VerificationContractRail() {
        setPriority(CONTRACT_PRIORITY);
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        PromptSection section = new PromptSection(
                SectionName.VERIFICATION_CONTRACT,
                Map.of("en", CONTRACT_EN, "cn", CONTRACT_CN),
                CONTRACT_PRIORITY
        );
        ctx.put("verification_contract_section", section);
    }
}
