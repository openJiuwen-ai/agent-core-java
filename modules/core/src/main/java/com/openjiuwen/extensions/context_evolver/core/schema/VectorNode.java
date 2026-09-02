/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.schema.vector_node.VectorNode}.
 * Vector node for storing in vector databases.
 * 
 * @since 0.1.7
 */
public class VectorNode {
    private final String id;
    private final String content;
    private List<Double> embedding;
    private final Map<String, Object> metadata;

    /**
     * VectorNode.
     * 
     * @param id id
     * @param content content
     * @since 0.1.7
     */
    public VectorNode(String id, String content) {
        this(id, content, null, new HashMap<>());
    }

    /**
     * VectorNode.
     * 
     * @param id id
     * @param content content
     * @param embedding embedding
     * @since 0.1.7
     */
    public VectorNode(String id, String content, List<Double> embedding) {
        this(id, content, embedding, new HashMap<>());
    }

    /**
     * VectorNode.
     * 
     * @param id id
     * @param content content
     * @param embedding embedding
     * @param metadata metadata
     * @since 0.1.7
     */
    public VectorNode(String id, String content, List<Double> embedding, Map<String, Object> metadata) {
        this.id = id;
        this.content = content;
        this.embedding = embedding;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * getId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getId() {
        return id;
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

    /**
     * getEmbedding.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Double> getEmbedding() {
        return embedding != null ? new ArrayList<>(embedding) : null;
    }

    /**
     * setEmbedding.
     * 
     * @param embedding embedding
     * @since 0.1.7
     */
    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding != null ? new ArrayList<>(embedding) : null;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    /**
     * setMetadata.
     * 
     * @param key key
     * @param value value
     * @since 0.1.7
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * getMetadata.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Convert to dictionary.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toDict() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("id", id);
        dict.put("content", content);
        dict.put("embedding", embedding);
        dict.put("metadata", new HashMap<>(metadata));
        return dict;
    }

    /**
     * fromDict.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static VectorNode fromDict(Map<String, Object> data) {
        String id = (String) data.get("id");
        String content = (String) data.get("content");
        List<Double> embedding = (List<Double>) data.get("embedding");
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        return new VectorNode(id, content, embedding, metadata);
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        String preview = content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content;
        return "VectorNode(id=" + id + ", content='" + preview + "')";
    }
}
