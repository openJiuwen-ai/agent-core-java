  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ReasoningBankMemory}.
 */
public class ReasoningBankMemory {

    private String workspaceId = "default";
    private String query;
    private List<ReasoningBankMemoryItem> memory = new ArrayList<>();
    private Boolean label;

    public ReasoningBankMemory() {
        // Default constructor
    }

    public VectorNode toVectorNode() {
        String title = memory.isEmpty() ? "" : memory.get(0).getTitle();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "reasoning_bank_memory");
        metadata.put("query", query);
        metadata.put("memory", memory.stream().map(ReasoningBankMemoryItem::toMap).toList());
        metadata.put("label", label);
        metadata.put("workspace_id", workspaceId);
        return new VectorNode(
            "reasoning_bank_" + workspaceId + "_" + SchemaUtils.md5Hex(query + "|" + title),
            query,
            null,
            metadata
        );
    }

    public List<ReasoningBankRetrievedMemory> toRetrievedMemories() {
        List<ReasoningBankRetrievedMemory> result = new ArrayList<>();
        for (ReasoningBankMemoryItem item : memory) {
            result.add(new ReasoningBankRetrievedMemory(item.getTitle(), item.getDescription(), item.getContent()));
        }
        return result;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("memory", memory.stream().map(ReasoningBankMemoryItem::toMap).toList());
        result.put("label", label);
        result.put("workspace_id", workspaceId);
        return result;
    }

    public static ReasoningBankMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    public static ReasoningBankMemory fromMap(Map<String, Object> data) {
        ReasoningBankMemory result = new ReasoningBankMemory();
        result.workspaceId = SchemaUtils.stringValue(data.get("workspace_id"), "default");
        result.query = SchemaUtils.stringValue(data.get("query"), "");
        result.label = SchemaUtils.booleanValue(data.get("label"));
        Object memoryValue = data.get("memory");
        if (memoryValue instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof ReasoningBankMemoryItem memoryItem) {
                    result.memory.add(memoryItem);
                } else if (item instanceof Map<?, ?>) {
                    result.memory.add(ReasoningBankMemoryItem.fromMap(SchemaUtils.mapValue(item)));
                }
            }
        }
        return result;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId != null && !workspaceId.isBlank() ? workspaceId : "default";
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<ReasoningBankMemoryItem> getMemory() {
        return new ArrayList<>(memory);
    }

    public void setMemory(List<ReasoningBankMemoryItem> memory) {
        this.memory = memory != null ? new ArrayList<>(memory) : new ArrayList<>();
    }

    public Boolean getLabel() {
        return label;
    }

    public void setLabel(Boolean label) {
        this.label = label;
    }
}
