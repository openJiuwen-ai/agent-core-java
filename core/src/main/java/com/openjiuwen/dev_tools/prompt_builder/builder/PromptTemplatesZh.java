/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

/**
 * Legacy Chinese prompt template constants.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.prompt_zh} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/prompt_zh.py}.</p>
 */
public final class PromptTemplatesZh {
    public static final PromptTemplate PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE =
            PromptZh.PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE;

    public static final PromptTemplate PROMPT_BUILD_GENERAL_META_USER_TEMPLATE =
            PromptZh.PROMPT_BUILD_GENERAL_META_USER_TEMPLATE;

    public static final PromptTemplate PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE =
            PromptZh.PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE;

    public static final PromptTemplate PROMPT_BUILD_PLAN_META_USER_TEMPLATE =
            PromptZh.PROMPT_BUILD_PLAN_META_USER_TEMPLATE;

    public static final PromptTemplate PROMPT_FEEDBACK_INTENT_TEMPLATE =
            PromptZh.PROMPT_FEEDBACK_INTENT_TEMPLATE;

    public static final PromptTemplate PROMPT_FEEDBACK_GENERAL_TEMPLATE =
            PromptZh.PROMPT_FEEDBACK_GENERAL_TEMPLATE;

    public static final PromptTemplate PROMPT_FEEDBACK_SELECT_TEMPLATE =
            PromptZh.PROMPT_FEEDBACK_SELECT_TEMPLATE;

    public static final PromptTemplate PROMPT_FEEDBACK_INSERT_TEMPLATE =
            PromptZh.PROMPT_FEEDBACK_INSERT_TEMPLATE;

    public static final PromptTemplate PROMPT_BAD_CASE_ANALYZE_TEMPLATE =
            PromptZh.PROMPT_BAD_CASE_ANALYZE_TEMPLATE;

    public static final PromptTemplate PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE =
            PromptZh.PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE;

    public static final PromptTemplate FORMAT_BAD_CASE_TEMPLATE =
            PromptZh.FORMAT_BAD_CASE_TEMPLATE;

    private PromptTemplatesZh() {
    }
}
