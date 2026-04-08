/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

/**
 * Graph Database Storage Limits.
 * <p>
 * Mirrors Python's {@code GraphStoreStorageConfig}.
 */
public class GraphStoreStorageConfig {

    private final int uuid;
    private final int name;
    private final int content;
    private final int language;
    private final int userId;
    private final int entities;
    private final int relations;
    private final int episodes;
    private final int objType;

    @SuppressWarnings("checkstyle:ParameterNumber")
    private GraphStoreStorageConfig(Builder builder) {
        this.uuid = builder.uuid;
        this.name = builder.name;
        this.content = builder.content;
        this.language = builder.language;
        this.userId = builder.userId;
        this.entities = builder.entities;
        this.relations = builder.relations;
        this.episodes = builder.episodes;
        this.objType = builder.objType;
    }

    public GraphStoreStorageConfig() {
        this(builder());
    }

    public int getUuid() { return uuid; }
    public int getName() { return name; }
    public int getContent() { return content; }
    public int getLanguage() { return language; }
    public int getUserId() { return userId; }
    public int getEntities() { return entities; }
    public int getRelations() { return relations; }
    public int getEpisodes() { return episodes; }
    public int getObjType() { return objType; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int uuid = 32;
        private int name = 500;
        private int content = 65535;
        private int language = 10;
        private int userId = 32;
        private int entities = 4096;
        private int relations = 4096;
        private int episodes = 4096;
        private int objType = 20;

        public Builder uuid(int uuid) { this.uuid = uuid; return this; }
        public Builder name(int name) { this.name = name; return this; }
        public Builder content(int content) { this.content = content; return this; }
        public Builder language(int language) { this.language = language; return this; }
        public Builder userId(int userId) { this.userId = userId; return this; }
        public Builder entities(int entities) { this.entities = entities; return this; }
        public Builder relations(int relations) { this.relations = relations; return this; }
        public Builder episodes(int episodes) { this.episodes = episodes; return this; }
        public Builder objType(int objType) { this.objType = objType; return this; }
        public GraphStoreStorageConfig build() { return new GraphStoreStorageConfig(this); }
    }
}
