/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ReasoningBankMemoryItem}.
 * 
 * @since 0.1.7
 */
public class ReasoningBankMemoryItem {
    private String title;
    private String description;
    private String content;

    /**
     * ReasoningBankMemoryItem.
     * 
     * @since 0.1.7
     */
    public ReasoningBankMemoryItem() {
        // Default constructor
    }

    /**
     * ReasoningBankMemoryItem.
     * 
     * @param title title
     * @param description description
     * @param content content
     * @since 0.1.7
     */
    public ReasoningBankMemoryItem(String title, String description, String content) {
        this.title = title;
        this.description = description;
        this.content = content;
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", title);
        result.put("description", description);
        result.put("content", content);
        return result;
    }

    /**
     * fromMap.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    public static ReasoningBankMemoryItem fromMap(Map<String, Object> data) {
        return new ReasoningBankMemoryItem(SchemaUtils.stringValue(data.get("title"), ""),
                SchemaUtils.stringValue(data.get("description"), ""), SchemaUtils.stringValue(data.get("content"), ""));
    }

    /**
     * getTitle.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTitle() {
        return title;
    }

    /**
     * getDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDescription() {
        return description;
    }

    /**
     * getContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getContent() {
        return content;
    }
}
