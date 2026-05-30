/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Structured representation of Markdown content.
 *
 * <p>Mirrors Python's {@code MarkdownContent} in
 * {@code openjiuwen.core.foundation.llm.output_parsers.markdown_output_parser}.</p>
 */
@Data
public class MarkdownContent {

    private String rawContent = "";

    private List<MarkdownElement> elements = new ArrayList<>();

    private List<Map<String, Object>> headers = new ArrayList<>();

    private List<Map<String, Object>> codeBlocks = new ArrayList<>();

    private List<Map<String, Object>> links = new ArrayList<>();

    private List<Map<String, Object>> images = new ArrayList<>();

    private List<String> tables = new ArrayList<>();

    private List<String> lists = new ArrayList<>();

    public MarkdownContent() {
    }

    public MarkdownContent(String rawContent) {
        this.rawContent = rawContent != null ? rawContent : "";
    }
}
