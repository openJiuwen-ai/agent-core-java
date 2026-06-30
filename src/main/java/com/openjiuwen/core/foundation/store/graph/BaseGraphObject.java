/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all graph objects with common properties.
 */
public class BaseGraphObject {
    private String uuid = GraphUtils.getUuid();
    private int createdAt = GraphUtils.getCurrentUtcTimestamp();
    private String userId = "default_user";
    private String objType = "";
    private String language = "cn";
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String content = "";
    private List<Float> contentEmbedding;
    private List<Float> contentBm25;
    private final int version = 1;

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getCreatedAt() {
        return createdAt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getObjType() {
        return objType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setObjType(String objType) {
        this.objType = objType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getContent() {
        return content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> getContentEmbedding() {
        return contentEmbedding;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setContentEmbedding(List<Float> contentEmbedding) {
        this.contentEmbedding = contentEmbedding;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> getContentBm25() {
        return contentBm25;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setContentBm25(List<Float> contentBm25) {
        this.contentBm25 = contentBm25;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getVersion() {
        return version;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<GraphUtils.EmbedTask> fetchEmbedTask() {
        return List.of(new GraphUtils.EmbedTask(this, "contentEmbedding", content));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", uuid);
        result.put("created_at", createdAt);
        result.put("user_id", userId);
        result.put("obj_type", objType);
        result.put("language", language);
        result.put("metadata", metadata);
        result.put("content", content);
        result.put("content_embedding", contentEmbedding);
        result.put("content_bm25", contentBm25);
        return result;
    }
}
