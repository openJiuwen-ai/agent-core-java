/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verification-contract prompt section builder.
 * <p>
 * Mirrors Python's {@code verification_contract} in
 * {@code openjiuwen.harness.prompts.sections}.
 */
public final class VerificationContractSection {

    private VerificationContractSection() {
    }

    private static final String CN = "# 验证合约\n"
            + "\n"
            + "- 完成任务后必须验证结果\n"
            + "- 验证包括：功能正确性、边界条件、错误处理\n";

    private static final String EN = "# Verification Contract\n"
            + "\n"
            + "- Results must be verified after task completion\n"
            + "- Verification includes: functional correctness, boundary conditions, error handling\n";

    private static final Map<String, String> VERIFICATION = new LinkedHashMap<>();

    static {
        VERIFICATION.put("cn", CN);
        VERIFICATION.put("en", EN);
    }

    public static PromptSection build() {
        return new PromptSection(SectionName.VERIFICATION_CONTRACT, VERIFICATION, 92);
    }
}
