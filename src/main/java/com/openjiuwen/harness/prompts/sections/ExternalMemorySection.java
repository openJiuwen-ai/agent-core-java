/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * External-memory prompt section builder.
 * <p>
 * Mirrors Python's {@code external_memory} in
 * {@code openjiuwen.harness.prompts.sections.external_memory}.
 */
public final class ExternalMemorySection {

    private ExternalMemorySection() {
    }

    private static final String CN = "# 外部记忆\n"
            + "\n"
            + "- 使用外部记忆工具存储和检索跨会话信息\n"
            + "- 合理组织记忆内容，便于后续检索\n";

    private static final String EN = "# External Memory\n"
            + "\n"
            + "- Use external memory tools to store and retrieve cross-session information\n"
            + "- Organize memory content for efficient retrieval\n";

    private static final Map<String, String> EXTERNAL_MEMORY = new LinkedHashMap<>();

    static {
        EXTERNAL_MEMORY.put("cn", CN);
        EXTERNAL_MEMORY.put("en", EN);
    }

    public static PromptSection buildExternalMemorySection(String promptBlock, String language) {
        if (promptBlock == null || promptBlock.isBlank()) {
            return null;
        }
        String resolvedLanguage = language == null || language.isBlank() ? "cn" : language;
        return new PromptSection(
                SectionName.EXTERNAL_MEMORY,
                Map.of(resolvedLanguage, promptBlock),
                55
        );
    }

    public static PromptSection build() {
        return new PromptSection(SectionName.EXTERNAL_MEMORY, EXTERNAL_MEMORY, 88);
    }
}
