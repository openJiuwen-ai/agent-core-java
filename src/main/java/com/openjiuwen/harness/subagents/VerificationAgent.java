/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

/**
 * Verification agent configuration and factory.
 * <p>
 * Mirrors Python's {@code verification_agent} in
 * {@code openjiuwen.harness.subagents.verification_agent}.
 */
public final class VerificationAgent {

    private VerificationAgent() {
    }

    public static final String FACTORY_NAME = "verification_agent";

    private static final String SYSTEM_PROMPT_CN = "你是一个验证助手，负责检查代码质量、测试覆盖和需求符合度。";
    private static final String SYSTEM_PROMPT_EN = "You are a verification agent, responsible for checking code quality, test coverage, and requirement compliance.";

    private static final String DESCRIPTION_CN = "验证代理。负责代码审查、测试验证和需求一致性检查。";
    private static final String DESCRIPTION_EN = "Verification agent. Responsible for code review, test verification, and requirement consistency checking.";

    public static String getSystemPrompt(String language) {
        return "en".equals(language) ? SYSTEM_PROMPT_EN : SYSTEM_PROMPT_CN;
    }

    public static String getDescription(String language) {
        return "en".equals(language) ? DESCRIPTION_EN : DESCRIPTION_CN;
    }
}
