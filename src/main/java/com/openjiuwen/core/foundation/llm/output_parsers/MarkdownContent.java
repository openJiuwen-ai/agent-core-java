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
 * @since 0.1.7
 */
@Data
public class MarkdownContent {
    private String rawContent = "";

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<MarkdownElement> elements = new ArrayList<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Map<String, Object>> headers = new ArrayList<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Map<String, Object>> codeBlocks = new ArrayList<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Map<String, Object>> links = new ArrayList<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Map<String, Object>> images = new ArrayList<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> tables = new ArrayList<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> lists = new ArrayList<>();

    /**
     * MarkdownContent.
     * 
     * @since 0.1.7
     */
    public MarkdownContent() {
    }

    /**
     * MarkdownContent.
     * 
     * @param rawContent rawContent
     * @since 0.1.7
     */
    public MarkdownContent(String rawContent) {
        this.rawContent = rawContent != null ? rawContent : "";
    }
}
