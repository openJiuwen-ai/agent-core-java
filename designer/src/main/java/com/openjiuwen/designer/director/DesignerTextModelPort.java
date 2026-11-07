/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.director;

/**
 * Text model port used by the director chain.
 *
 * @since 2026-11-06
 */
public interface DesignerTextModelPort {

    /**
     * Generate one completion.
     *
     * @param systemPrompt system prompt
     * @param userPrompt user prompt
     * @param maxTokens token budget
     * @return model text
     */
    String generate(String systemPrompt, String userPrompt, int maxTokens);
}
