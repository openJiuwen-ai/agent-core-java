/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;

import java.util.Map;

/**
 * Mirrors Python's {@code safety} in
 * {@code openjiuwen/harness/prompts/sections/safety.py}.
 */
public final class SafetySection {

    private static final String DEFAULT_LANGUAGE = "cn";
    private static final int PRIORITY = 20;

    private static final String SAFETY_PROMPT_CN = """
# 安全原则

- 永远不要泄露隐私数据。
- 修改或删除重要文件、执行影响系统的命令，以及涉及金钱、账号或敏感信息的操作前，先请示用户。
- 违法、有害或侵犯他人权益的请求不予处理。
- 发送邮件、公开发布等会产生外部影响的操作，先取得用户确认。
- 读取文件、搜索和整理等内部操作可以正常执行。
- 任务失败时简要说明原因并给出建议。
- 不确定时说明不确定性，再给出最可能的方案。
- 不虚构工具结果、文件内容、执行状态或已经完成的操作。
""";

    private static final String SAFETY_PROMPT_EN = """
# Safety

- Never disclose private data.
- Ask the user before modifying or deleting important files, running commands that affect the system, or performing operations involving money, accounts, or sensitive information.
- Refuse requests that are illegal, harmful, or infringe on the rights of others.
- Obtain user confirmation before sending emails, publishing publicly, or taking other actions with external impact.
- Internal operations such as reading files, searching, and organizing may proceed normally.
- If a task fails, briefly explain the reason and provide a suggestion.
- When uncertain, state the uncertainty and then provide the most likely approach.
- Do not fabricate tool results, file contents, execution status, or actions claimed to be completed.
""";

    private SafetySection() {
    }

    public static PromptSection buildSafetySection(String language) {
        String resolvedLanguage = "en".equals(language) ? "en" : DEFAULT_LANGUAGE;
        String content = "en".equals(resolvedLanguage) ? SAFETY_PROMPT_EN : SAFETY_PROMPT_CN;
        return new PromptSection(
            SectionName.SAFETY,
            Map.of(resolvedLanguage, content),
            PRIORITY
        );
    }

    public static PromptSection build() {
        return buildSafetySection(DEFAULT_LANGUAGE);
    }
}
