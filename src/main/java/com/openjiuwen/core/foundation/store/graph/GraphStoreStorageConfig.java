/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public GraphStoreStorageConfig() {
        this(builder());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getUuid() {
        return uuid;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getContent() {
        return content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getLanguage() {
        return language;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getUserId() {
        return userId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getEntities() {
        return entities;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getRelations() {
        return relations;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getEpisodes() {
        return episodes;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getObjType() {
        return objType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder uuid(int uuid) {
            this.uuid = uuid;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(int name) {
            this.name = name;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder content(int content) {
            this.content = content;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder language(int language) {
            this.language = language;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder userId(int userId) {
            this.userId = userId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder entities(int entities) {
            this.entities = entities;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder relations(int relations) {
            this.relations = relations;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder episodes(int episodes) {
            this.episodes = episodes;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder objType(int objType) {
            this.objType = objType;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public GraphStoreStorageConfig build() {
            return new GraphStoreStorageConfig(this);
        }
    }
}
