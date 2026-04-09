/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

/**
 * Markdown element type constants.
 */
public final class MarkdownElementType {

    public static final String HEADER = "header";
    public static final String CODE_BLOCK = "code_block";
    public static final String INLINE_CODE = "inline_code";
    public static final String LINK = "link";
    public static final String IMAGE = "image";
    public static final String TABLE = "table";
    public static final String LIST = "list";
    public static final String TEXT = "text";

    private MarkdownElementType() {
    }
}
