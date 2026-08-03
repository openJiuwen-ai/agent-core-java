/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ReasoningBankRetrievedMemory}.
 * {@code openjiuwen/extensions/context_evolver/schema/io_schema.py}.
 */
public class ReasoningBankRetrievedMemory {

    private String title;
    private String description;
    private String content;

    public ReasoningBankRetrievedMemory() {
        // Default constructor
    }

    public ReasoningBankRetrievedMemory(String title, String description, String content) {
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

    public static ReasoningBankRetrievedMemory fromVectorNode(VectorNode node) {
        ReasoningBankMemory memory = ReasoningBankMemory.fromVectorNode(node);
        List<ReasoningBankRetrievedMemory> items = memory.toRetrievedMemories();
        return items.isEmpty() ? new ReasoningBankRetrievedMemory("", "", "") : items.get(0);
    }

    public static ReasoningBankRetrievedMemory fromMap(Map<String, Object> data) {
        if (data.containsKey("memory")) {
            return fromVectorNode(new VectorNode("reasoning_bank", SchemaUtils.stringValue(data.get("query"), ""), null, data));
        }
        return new ReasoningBankRetrievedMemory(
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
