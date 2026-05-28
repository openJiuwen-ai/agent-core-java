/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safety prompt section builder.
 * <p>
 * Mirrors Python's {@code safety} in
 * {@code openjiuwen.harness.prompts.sections.safety}.
 */
public final class SafetySection {

    private SafetySection() {
    }

    private static final String CN = "# 安全原则\n"
            + "\n"
            + "- 永远不要泄露隐私数据\n"
            + "- 以下操作前需请示用户：修改/删除重要文件、影响系统的命令、涉及金钱/账号/敏感信息\n"
            + "- 违法、有害、侵犯他人权益的请求不予处理\n"
            + "- 外部操作（发邮件、发推文、公开发布）先问再做\n"
            + "- 内部操作（读文件、搜索、整理）可放心执行\n"
            + "- 任务失败时简要说明原因并给出建议\n"
            + "- 不确定时先说明不确定性，再给出最可能的方案\n";

    private static final String EN = "# Safety\n"
            + "\n"
            + "- Never leak private data\n"
            + "- Ask first before modifying/deleting important files, running system-affecting commands, or handling money/accounts/sensitive information\n"
            + "- Refuse illegal, harmful, or rights-infringing requests\n"
            + "- Ask first before external actions such as emails, tweets, or public posts\n"
            + "- Internal actions such as reading files, searching, and organizing are safe to do directly\n"
            + "- If a task fails, briefly explain why and suggest the most practical next step\n"
            + "- If uncertain, state the uncertainty first, then give the most likely answer or plan\n";

    private static final Map<String, String> SAFETY = new LinkedHashMap<>();

    static {
        SAFETY.put("cn", CN);
        SAFETY.put("en", EN);
    }

    public static PromptSection build() {
        return new PromptSection(SectionName.SAFETY, SAFETY, 20);
    }
}
