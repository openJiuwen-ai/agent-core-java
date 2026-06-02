/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all graph objects with common properties.
 * <p>
 * Mirrors Python's {@code BaseGraphObject} model from
 * <code>foundation/store/graph/graph_object.py</code>.
 */
public class BaseGraphObject {

    private String uuid;
    private long createdAt;
    private String userId;
    private String objType;
    private String language;
    private Map<String, Object> metadata;
    private String content;
    private float[] contentEmbedding;
    private float[] contentBm25;
    private int version = 1;

    public BaseGraphObject() {
        this.uuid = generateUuid();
        this.createdAt = Instant.now().getEpochSecond();
        this.userId = "default_user";
        this.objType = "";
        this.language = "cn";
        this.metadata = new HashMap<>();
        this.content = "";
    }

    private static String generateUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // --- Getters and Setters ---

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getObjType() { return objType; }
    public void setObjType(String objType) { this.objType = objType; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata != null ? metadata : new HashMap<>(); }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public float[] getContentEmbedding() { return contentEmbedding; }
    public void setContentEmbedding(float[] contentEmbedding) { this.contentEmbedding = contentEmbedding; }

    public float[] getContentBm25() { return contentBm25; }
    public void setContentBm25(float[] contentBm25) { this.contentBm25 = contentBm25; }

    public int getVersion() { return version; }

    /**
     * Fetch embedding task tuples (self, attribute_name, content_to_embed).
     */
    public List<EmbedTask> fetchEmbedTask() {
        List<EmbedTask> tasks = new ArrayList<>();
        tasks.add(new EmbedTask(this, "content_embedding", content));
        return tasks;
    }

    /**
     * Embedding task tuple.
     */
    public static class EmbedTask {
        private final BaseGraphObject object;
        private final String attributeName;
        private final String contentToEmbed;

        public EmbedTask(BaseGraphObject object, String attributeName, String contentToEmbed) {
            this.object = object;
            this.attributeName = attributeName;
            this.contentToEmbed = contentToEmbed;
        }

        public BaseGraphObject getObject() { return object; }
        public String getAttributeName() { return attributeName; }
        public String getContentToEmbed() { return contentToEmbed; }
    }
}
