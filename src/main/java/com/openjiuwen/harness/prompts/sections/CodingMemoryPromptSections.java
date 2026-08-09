/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;

/**
 * Compatibility facade. The production text lives in {@link CodingMemorySection}.
 */
public final class CodingMemoryPromptSections {

    private CodingMemoryPromptSections() {
    }

    public static PromptSection buildCodingMemorySection(String language, boolean isReadOnly, String memoryDir) {
        String lang = language == null || language.isBlank()
                ? SystemPromptBuilder.DEFAULT_LANGUAGE : language;
        String dir = memoryDir == null || memoryDir.isBlank() ? "coding_memory/" : memoryDir;
        return CodingMemorySection.buildCodingMemorySection(lang, isReadOnly, dir);
    }
}
