/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

/**
 * Legacy English prompt template constants.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.prompt_en} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/prompt_en.py}.</p>
 */
public final class PromptTemplatesEn {
    public static final PromptTemplate PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE =
            PromptEn.PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE;

    public static final PromptTemplate PROMPT_BUILD_GENERAL_META_USER_TEMPLATE =
            PromptEn.PROMPT_BUILD_GENERAL_META_USER_TEMPLATE;

    public static final PromptTemplate PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE =
            PromptEn.PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE;

    public static final PromptTemplate PROMPT_BUILD_PLAN_META_USER_TEMPLATE =
            PromptEn.PROMPT_BUILD_PLAN_META_USER_TEMPLATE;

    public static final PromptTemplate PROMPT_FEEDBACK_INTENT_TEMPLATE =
            PromptEn.PROMPT_FEEDBACK_INTENT_TEMPLATE;

    public static final PromptTemplate PROMPT_FEEDBACK_GENERAL_TEMPLATE =
            PromptEn.PROMPT_FEEDBACK_GENERAL_TEMPLATE;

    public static final PromptTemplate PROMPT_FEEDBACK_SELECT_TEMPLATE =
            PromptEn.PROMPT_FEEDBACK_SELECT_TEMPLATE;

    public static final PromptTemplate PROMPT_FEEDBACK_INSERT_TEMPLATE =
            PromptEn.PROMPT_FEEDBACK_INSERT_TEMPLATE;

    public static final PromptTemplate PROMPT_BAD_CASE_ANALYZE_TEMPLATE =
            PromptEn.PROMPT_BAD_CASE_ANALYZE_TEMPLATE;

    public static final PromptTemplate PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE =
            PromptEn.PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE;

    public static final PromptTemplate FORMAT_BAD_CASE_TEMPLATE =
            PromptEn.FORMAT_BAD_CASE_TEMPLATE;

    private PromptTemplatesEn() {
    }
}
