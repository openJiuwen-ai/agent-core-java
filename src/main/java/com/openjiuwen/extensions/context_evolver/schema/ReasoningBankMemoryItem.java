/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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

    public ReasoningBankMemoryItem() {
        // Default constructor
    }

    public ReasoningBankMemoryItem(String title, String description, String content) {
        this.title = title;
        this.description = description;
        this.content = content;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", title);
        result.put("description", description);
        result.put("content", content);
        return result;
    }

    public static ReasoningBankMemoryItem fromMap(Map<String, Object> data) {
        return new ReasoningBankMemoryItem(
            SchemaUtils.stringValue(data.get("title"), ""),
            SchemaUtils.stringValue(data.get("description"), ""),
            SchemaUtils.stringValue(data.get("content"), "")
        );
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getContent() {
        return content;
    }
}
