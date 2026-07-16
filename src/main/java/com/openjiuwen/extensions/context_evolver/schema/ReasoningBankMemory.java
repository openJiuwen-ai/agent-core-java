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
 * 
 * @since 0.1.7
 */
public class ReasoningBankMemory {
    private String workspaceId = "default";
    private String query;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<ReasoningBankMemoryItem> memory = new ArrayList<>();
    private Boolean label;

    /**
     * ReasoningBankMemory.
     * 
     * @since 0.1.7
     */
    public ReasoningBankMemory() {
        // Default constructor
    }

    /**
     * toVectorNode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public VectorNode toVectorNode() {
        String title = memory.isEmpty() ? "" : memory.get(0).getTitle();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "reasoning_bank_memory");
        metadata.put("query", query);
        metadata.put("memory", memory.stream().map(ReasoningBankMemoryItem::toMap).toList());
        metadata.put("label", label);
        metadata.put("workspace_id", workspaceId);
        return new VectorNode("reasoning_bank_" + workspaceId + "_" + SchemaUtils.md5Hex(query + "|" + title), query,
                null, metadata);
    }

    /**
     * toRetrievedMemories.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<ReasoningBankRetrievedMemory> toRetrievedMemories() {
        List<ReasoningBankRetrievedMemory> result = new ArrayList<>();
        for (ReasoningBankMemoryItem item : memory) {
            result.add(new ReasoningBankRetrievedMemory(item.getTitle(), item.getDescription(), item.getContent()));
        }
        return result;
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("memory", memory.stream().map(ReasoningBankMemoryItem::toMap).toList());
        result.put("label", label);
        result.put("workspace_id", workspaceId);
        return result;
    }

    /**
     * fromVectorNode.
     * 
     * @param node node
     * @return the result
     * @since 0.1.7
     */
    public static ReasoningBankMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    /**
     * fromMap.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
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
                } else {
                    // no-op
                }
            }
        }
        return result;
    }

    /**
     * getWorkspaceId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getWorkspaceId() {
        return workspaceId;
    }

    /**
     * setWorkspaceId.
     * 
     * @param workspaceId workspaceId
     * @since 0.1.7
     */
    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId != null && !workspaceId.isBlank() ? workspaceId : "default";
    }

    /**
     * getQuery.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getQuery() {
        return query;
    }

    /**
     * setQuery.
     * 
     * @param query query
     * @since 0.1.7
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * getMemory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<ReasoningBankMemoryItem> getMemory() {
        return new ArrayList<>(memory);
    }

    /**
     * setMemory.
     * 
     * @param memory memory
     * @since 0.1.7
     */
    public void setMemory(List<ReasoningBankMemoryItem> memory) {
        this.memory = memory != null ? new ArrayList<>(memory) : new ArrayList<>();
    }

    /**
     * getLabel.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Boolean getLabel() {
        return label;
    }

    /**
     * setLabel.
     * 
     * @param label label
     * @since 0.1.7
     */
    public void setLabel(Boolean label) {
        this.label = label;
    }
}
