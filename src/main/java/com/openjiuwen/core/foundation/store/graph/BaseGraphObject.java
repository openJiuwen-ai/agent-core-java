/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all graph objects with common properties.
 * 
 * @since 0.1.7
 */
public class BaseGraphObject {
    private String uuid = GraphUtils.getUuid();

    /**
     * GraphUtils.getCurrentUtcTimestamp.
     * 
     * @since 0.1.7
     */
    private int createdAt = GraphUtils.getCurrentUtcTimestamp();
    private String userId = "default_user";
    private String objType = "";
    private String language = "cn";

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String content = "";
    private List<Float> contentEmbedding;
    private List<Float> contentBm25;
    private final int version = 1;

    /**
     * getUuid.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * setUuid.
     * 
     * @param uuid uuid
     * @since 0.1.7
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * getCreatedAt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getCreatedAt() {
        return createdAt;
    }

    /**
     * setCreatedAt.
     * 
     * @param createdAt createdAt
     * @since 0.1.7
     */
    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * getUserId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getUserId() {
        return userId;
    }

    /**
     * setUserId.
     * 
     * @param userId userId
     * @since 0.1.7
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * getObjType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getObjType() {
        return objType;
    }

    /**
     * setObjType.
     * 
     * @param objType objType
     * @since 0.1.7
     */
    public void setObjType(String objType) {
        this.objType = objType;
    }

    /**
     * getLanguage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getLanguage() {
        return language;
    }

    /**
     * setLanguage.
     * 
     * @param language language
     * @since 0.1.7
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
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
     * setContent.
     * 
     * @param content content
     * @since 0.1.7
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * getContentEmbedding.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Float> getContentEmbedding() {
        return contentEmbedding;
    }

    /**
     * setContentEmbedding.
     * 
     * @param contentEmbedding contentEmbedding
     * @since 0.1.7
     */
    public void setContentEmbedding(List<Float> contentEmbedding) {
        this.contentEmbedding = contentEmbedding;
    }

    /**
     * getContentBm25.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Float> getContentBm25() {
        return contentBm25;
    }

    /**
     * setContentBm25.
     * 
     * @param contentBm25 contentBm25
     * @since 0.1.7
     */
    public void setContentBm25(List<Float> contentBm25) {
        this.contentBm25 = contentBm25;
    }

    /**
     * getVersion.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getVersion() {
        return version;
    }

    /**
     * fetchEmbedTask.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<GraphUtils.EmbedTask> fetchEmbedTask() {
        return List.of(new GraphUtils.EmbedTask(this, "contentEmbedding", content));
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
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
