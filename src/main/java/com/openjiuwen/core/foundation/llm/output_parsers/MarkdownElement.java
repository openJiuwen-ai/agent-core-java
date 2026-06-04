/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Single Markdown element with positional metadata.
 *
 * <p>Mirrors Python's {@code MarkdownElement} in
 * {@code openjiuwen.core.foundation.llm.output_parsers.markdown_output_parser}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkdownElement {

    private String type;

    private Map<String, Object> content;

    private int startPos;

    private int endPos;

    private String raw;
}
