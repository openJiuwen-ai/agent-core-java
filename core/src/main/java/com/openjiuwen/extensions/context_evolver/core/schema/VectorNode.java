/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code VectorNode} in
 * {@code openjiuwen/extensions/context_evolver/core/schema/vector_node.py}.
 */
public class VectorNode {

    private String id;
    private String content;
    private List<Double> embedding;
    private Map<String, Object> metadata;

    public VectorNode(String id, String content) {
        this(id, content, null, null);
    }

    public VectorNode(String id, String content, List<Double> embedding) {
        this(id, content, embedding, null);
    }

    public VectorNode(String id, String content, List<Double> embedding, Map<String, Object> metadata) {
        this.id = id;
        this.content = content;
        this.embedding = embedding != null ? new ArrayList<>(embedding) : null;
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding != null ? new ArrayList<>(embedding) : null;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }

    public Map<String, Object> toDict() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("content", content);
        data.put("embedding", embedding != null ? new ArrayList<>(embedding) : null);
        data.put("metadata", new LinkedHashMap<>(metadata));
        return data;
    }

    @SuppressWarnings("unchecked")
    public static VectorNode fromDict(Map<String, Object> data) {
        return new VectorNode(
            (String) data.get("id"),
            (String) data.get("content"),
            (List<Double>) data.get("embedding"),
            (Map<String, Object>) data.get("metadata")
        );
    }

    @Override
    public String toString() {
        String preview = content != null && content.length() > 50
            ? content.substring(0, 50) + "..."
            : content;
        return "VectorNode(id=" + id + ", content='" + preview + "')";
    }
}
