/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.Map;

/**
 * External memory prompt section helpers.
 *
 * <p>Mirrors Python's {@code build_external_memory_section} in
 * {@code openjiuwen/harness/prompts/sections/external_memory.py}.
 */
public final class ExternalMemorySection {

    private ExternalMemorySection() {
    }

    public static PromptSection buildExternalMemorySection(String promptBlock, String language) {
        if (promptBlock == null || promptBlock.isEmpty()) {
            return null;
        }
        return new PromptSection(
                SectionName.EXTERNAL_MEMORY,
                Map.of(language, promptBlock),
                55
        );
    }
}
