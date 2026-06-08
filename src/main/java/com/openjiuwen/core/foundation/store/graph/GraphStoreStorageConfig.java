/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.Map;

/**
 * Graph Database Storage Limits.
 * <p>
 * Mirrors Python's {@code GraphStoreStorageConfig} in
 * {@code openjiuwen/core/foundation/store/graph/database_config.py}.
 */
public class GraphStoreStorageConfig {

    private int uuid;
    private int name;
    private int content;
    private int language;
    private int userId;
    private int entities;
    private int relations;
    private int episodes;
    private int objType;

    public GraphStoreStorageConfig() {
        this(builder());
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public GraphStoreStorageConfig(
            int uuid,
            int name,
            int content,
            int language,
            int userId,
            int entities,
            int relations,
            int episodes,
            int objType) {
        this(builder()
                .uuid(uuid)
                .name(name)
                .content(content)
                .language(language)
                .userId(userId)
                .entities(entities)
                .relations(relations)
                .episodes(episodes)
                .objType(objType));
    }

    private GraphStoreStorageConfig(Builder builder) {
        setUuid(builder.uuid);
        setName(builder.name);
        setContent(builder.content);
        setLanguage(builder.language);
        setUserId(builder.userId);
        setEntities(builder.entities);
        setRelations(builder.relations);
        setEpisodes(builder.episodes);
        setObjType(builder.objType);
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getUuid() {
        return uuid;
    }

    public void setUuid(int uuid) {
        validateLimit("uuid", uuid, GraphStoreConstants.VARCHAR_LIMIT);
        this.uuid = uuid;
    }

    public int getName() {
        return name;
    }

    public void setName(int name) {
        validateLimit("name", name, GraphStoreConstants.VARCHAR_LIMIT);
        this.name = name;
    }

    public int getContent() {
        return content;
    }

    public void setContent(int content) {
        validateLimit("content", content, GraphStoreConstants.VARCHAR_LIMIT);
        this.content = content;
    }

    public int getLanguage() {
        return language;
    }

    public void setLanguage(int language) {
        validateLimit("language", language, GraphStoreConstants.VARCHAR_LIMIT);
        this.language = language;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        validateLimit("userId", userId, GraphStoreConstants.VARCHAR_LIMIT);
        this.userId = userId;
    }

    public int getEntities() {
        return entities;
    }

    public void setEntities(int entities) {
        validateLimit("entities", entities, GraphStoreConstants.ARRAY_LIMIT);
        this.entities = entities;
    }

    public int getRelations() {
        return relations;
    }

    public void setRelations(int relations) {
        validateLimit("relations", relations, GraphStoreConstants.ARRAY_LIMIT);
        this.relations = relations;
    }

    public int getEpisodes() {
        return episodes;
    }

    public void setEpisodes(int episodes) {
        validateLimit("episodes", episodes, GraphStoreConstants.ARRAY_LIMIT);
        this.episodes = episodes;
    }

    public int getObjType() {
        return objType;
    }

    public void setObjType(int objType) {
        validateLimit("objType", objType, GraphStoreConstants.VARCHAR_LIMIT);
        this.objType = objType;
    }

    public static final class Builder {

        private int uuid = 32;
        private int name = 500;
        private int content = 65535;
        private int language = 10;
        private int userId = 32;
        private int entities = 4096;
        private int relations = 4096;
        private int episodes = 4096;
        private int objType = 20;

        public Builder uuid(int uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder name(int name) {
            this.name = name;
            return this;
        }

        public Builder content(int content) {
            this.content = content;
            return this;
        }

        public Builder language(int language) {
            this.language = language;
            return this;
        }

        public Builder userId(int userId) {
            this.userId = userId;
            return this;
        }

        public Builder entities(int entities) {
            this.entities = entities;
            return this;
        }

        public Builder relations(int relations) {
            this.relations = relations;
            return this;
        }

        public Builder episodes(int episodes) {
            this.episodes = episodes;
            return this;
        }

        public Builder objType(int objType) {
            this.objType = objType;
            return this;
        }

        public GraphStoreStorageConfig build() {
            return new GraphStoreStorageConfig(this);
        }
    }

    private static void validateLimit(String field, int value, Map<String, Integer> limit) {
        int greaterThan = limit.getOrDefault("gt", Integer.MIN_VALUE);
        int lessThanOrEqual = limit.getOrDefault("le", Integer.MAX_VALUE);
        if (value <= greaterThan || value > lessThanOrEqual) {
            throw new IllegalArgumentException(
                    field + " must be in (" + greaterThan + ", " + lessThanOrEqual + "], got " + value);
        }
    }
}
