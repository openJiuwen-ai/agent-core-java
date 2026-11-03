/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code BaseGraphObject} in
 * {@code openjiuwen/core/foundation/store/graph/graph_object.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BaseGraphObject {

    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("^[ce]n$");

    private String uuid;
    private long createdAt;
    private String userId;
    private String objType;
    private String language;
    private Map<String, Object> metadata;
    private String content;
    private List<Double> contentEmbedding;
    private List<Double> contentBm25;

    public BaseGraphObject() {
        this.uuid = GraphStoreUtils.getUuid();
        this.createdAt = GraphStoreUtils.getCurrentUtcTimestamp();
        this.userId = "default_user";
        this.objType = "";
        this.language = "cn";
        this.metadata = new LinkedHashMap<>();
        this.content = "";
    }

    @JsonIgnore
    public int getVersion() {
        return 1;
    }

    public List<EmbeddingTask> fetchEmbedTask() {
        return List.of(new EmbeddingTask(this, "content_embedding", content));
    }

    static List<String> serializeGraphObjectList(Collection<?> values) {
        TreeSet<String> unique = new TreeSet<>();
        if (values == null) {
            return List.of();
        }
        for (Object value : values) {
            if (value instanceof BaseGraphObject graphObject) {
                unique.add(graphObject.getUuid());
            } else if (value != null) {
                unique.add(String.valueOf(value));
            }
        }
        return List.copyOf(unique);
    }

    static String serializeGraphObjectReference(Object value) {
        if (value instanceof BaseGraphObject graphObject) {
            return graphObject.getUuid();
        }
        return value == null ? null : String.valueOf(value);
    }

    static List<Double> copyDoubles(List<Double> source) {
        return source == null ? null : List.copyOf(source);
    }

    static Map<String, Object> copyMetadata(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getObjType() {
        return objType;
    }

    public void setObjType(String objType) {
        this.objType = objType == null ? "" : objType;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        String normalized = language == null ? "" : language;
        if (!LANGUAGE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("language must match ^[ce]n$");
        }
        this.language = normalized;
    }

    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = copyMetadata(metadata);
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content == null ? "" : content;
    }

    public List<Double> getContentEmbedding() {
        return contentEmbedding == null ? null : new ArrayList<>(contentEmbedding);
    }

    public void setContentEmbedding(List<Double> contentEmbedding) {
        this.contentEmbedding = copyDoubles(contentEmbedding);
    }

    public List<Double> getContentBm25() {
        return contentBm25 == null ? null : new ArrayList<>(contentBm25);
    }

    public void setContentBm25(List<Double> contentBm25) {
        this.contentBm25 = copyDoubles(contentBm25);
    }

    /**
     * Tuple-style embedding task matching Python's {@code (self, attr, content)} tuples in
     * {@code openjiuwen/core/foundation/store/graph/graph_object.py}.
     */
    public record EmbeddingTask(BaseGraphObject graphObject, String attributeName, String contentToEmbed) {
    }
}
