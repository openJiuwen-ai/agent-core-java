/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

/**
 * Markdown element type constants.
 * 
 * @since 0.1.7
 */
public final class MarkdownElementType {
    /**
     * HEADER.
     * 
     * @since 0.1.7
     */
    public static final String HEADER = "header";

    /**
     * CODE_BLOCK.
     * 
     * @since 0.1.7
     */
    public static final String CODE_BLOCK = "code_block";

    /**
     * INLINE_CODE.
     * 
     * @since 0.1.7
     */
    public static final String INLINE_CODE = "inline_code";

    /**
     * LINK.
     * 
     * @since 0.1.7
     */
    public static final String LINK = "link";

    /**
     * IMAGE.
     * 
     * @since 0.1.7
     */
    public static final String IMAGE = "image";

    /**
     * TABLE.
     * 
     * @since 0.1.7
     */
    public static final String TABLE = "table";

    /**
     * LIST.
     * 
     * @since 0.1.7
     */
    public static final String LIST = "list";

    /**
     * TEXT.
     * 
     * @since 0.1.7
     */
    public static final String TEXT = "text";

    /**
     * MarkdownElementType.
     * 
     * @since 0.1.7
     */
    private MarkdownElementType() {
    }
}
