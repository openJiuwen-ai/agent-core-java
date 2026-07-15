/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

/**
 * Graph Database Storage Limits.
 * <p>
 * Mirrors Python's {@code GraphStoreStorageConfig}.
 * 
 * @since 0.1.7
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
    /**
     * GraphStoreStorageConfig.
     * 
     * @param builder builder
     * @since 0.1.7
     */
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
     * GraphStoreStorageConfig.
     * 
     * @since 0.1.7
     */
    public GraphStoreStorageConfig() {
        this(builder());
    }

    /**
     * getUuid.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getUuid() {
        return uuid;
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getName() {
        return name;
    }

    /**
     * getContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getContent() {
        return content;
    }

    /**
     * getLanguage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getLanguage() {
        return language;
    }

    /**
     * getUserId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getUserId() {
        return userId;
    }

    /**
     * getEntities.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getEntities() {
        return entities;
    }

    /**
     * getRelations.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getRelations() {
        return relations;
    }

    /**
     * getEpisodes.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getEpisodes() {
        return episodes;
    }

    /**
     * getObjType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getObjType() {
        return objType;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
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
         * uuid.
         * 
         * @param uuid uuid
         * @return the result
         * @since 0.1.7
         */
        public Builder uuid(int uuid) {
            this.uuid = uuid;
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        public Builder name(int name) {
            this.name = name;
            return this;
        }

        /**
         * content.
         * 
         * @param content content
         * @return the result
         * @since 0.1.7
         */
        public Builder content(int content) {
            this.content = content;
            return this;
        }

        /**
         * language.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        public Builder language(int language) {
            this.language = language;
            return this;
        }

        /**
         * userId.
         * 
         * @param userId userId
         * @return the result
         * @since 0.1.7
         */
        public Builder userId(int userId) {
            this.userId = userId;
            return this;
        }

        /**
         * entities.
         * 
         * @param entities entities
         * @return the result
         * @since 0.1.7
         */
        public Builder entities(int entities) {
            this.entities = entities;
            return this;
        }

        /**
         * relations.
         * 
         * @param relations relations
         * @return the result
         * @since 0.1.7
         */
        public Builder relations(int relations) {
            this.relations = relations;
            return this;
        }

        /**
         * episodes.
         * 
         * @param episodes episodes
         * @return the result
         * @since 0.1.7
         */
        public Builder episodes(int episodes) {
            this.episodes = episodes;
            return this;
        }

        /**
         * objType.
         * 
         * @param objType objType
         * @return the result
         * @since 0.1.7
         */
        public Builder objType(int objType) {
            this.objType = objType;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public GraphStoreStorageConfig build() {
            return new GraphStoreStorageConfig(this);
        }
    }
}
