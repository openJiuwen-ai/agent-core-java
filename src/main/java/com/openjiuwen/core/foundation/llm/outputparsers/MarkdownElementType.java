// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.outputparsers;

/**
 * Markdown元素类型常量。
 * 对应 Python: markdown_output_parser.py - MarkdownElementType
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
        // 常量类，禁止实例化
    }
}

