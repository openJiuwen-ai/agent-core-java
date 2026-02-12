// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.outputparsers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Markdown内容的结构化表示。
 * 对应 Python: markdown_output_parser.py - MarkdownContent (dataclass)
 */
public class MarkdownContent {
    private String rawContent = "";
    private List<MarkdownElement> elements = new ArrayList<>();
    private List<Map<String, String>> headers = new ArrayList<>();
    private List<Map<String, String>> codeBlocks = new ArrayList<>();
    private List<Map<String, String>> links = new ArrayList<>();
    private List<Map<String, String>> images = new ArrayList<>();
    private List<String> tables = new ArrayList<>();
    private List<String> lists = new ArrayList<>();

    public MarkdownContent() {
    }

    public MarkdownContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public List<MarkdownElement> getElements() {
        return elements;
    }

    public void setElements(List<MarkdownElement> elements) {
        this.elements = elements;
    }

    public List<Map<String, String>> getHeaders() {
        return headers;
    }

    public void setHeaders(List<Map<String, String>> headers) {
        this.headers = headers;
    }

    public List<Map<String, String>> getCodeBlocks() {
        return codeBlocks;
    }

    public void setCodeBlocks(List<Map<String, String>> codeBlocks) {
        this.codeBlocks = codeBlocks;
    }

    public List<Map<String, String>> getLinks() {
        return links;
    }

    public void setLinks(List<Map<String, String>> links) {
        this.links = links;
    }

    public List<Map<String, String>> getImages() {
        return images;
    }

    public void setImages(List<Map<String, String>> images) {
        this.images = images;
    }

    public List<String> getTables() {
        return tables;
    }

    public void setTables(List<String> tables) {
        this.tables = tables;
    }

    public List<String> getLists() {
        return lists;
    }

    public void setLists(List<String> lists) {
        this.lists = lists;
    }
}

