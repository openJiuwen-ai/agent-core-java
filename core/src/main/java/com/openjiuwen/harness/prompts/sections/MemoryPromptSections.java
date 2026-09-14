/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;

/**
 * Compatibility facade. The production text lives in {@link MemorySection}.
 */
public final class MemoryPromptSections {

    private MemoryPromptSections() {
    }

    public static PromptSection buildMemorySection(
            String language, boolean isReadOnly, boolean isProactive) {
        return MemorySection.buildMemorySection(language, isReadOnly, isProactive);
    }
}
