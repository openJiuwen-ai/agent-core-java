/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ReasoningBankMemoryItem}.
 */
public class ReasoningBankMemoryItem {

    private String title;
    private String description;
    private String content;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ReasoningBankMemoryItem() {
        // Default constructor
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ReasoningBankMemoryItem(String title, String description, String content) {
        this.title = title;
        this.description = description;
        this.content = content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", title);
        result.put("description", description);
        result.put("content", content);
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static ReasoningBankMemoryItem fromMap(Map<String, Object> data) {
        return new ReasoningBankMemoryItem(
            SchemaUtils.stringValue(data.get("title"), ""),
            SchemaUtils.stringValue(data.get("description"), ""),
            SchemaUtils.stringValue(data.get("content"), "")
        );
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getContent() {
        return content;
    }
}
