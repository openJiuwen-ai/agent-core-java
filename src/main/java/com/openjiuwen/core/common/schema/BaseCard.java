/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Base digital card — the root class for all card-like entities.
 * <p>
 * Java equivalent of Python's Pydantic {@code BaseCard}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BaseCard {

    /** Unique identifier (UUID hex by default). */
    @Builder.Default
    private String id = UUID.randomUUID().toString().replace("-", "");

    /** Name — also serves as the unique identifier in a namespace. */
    @Builder.Default
    private String name = "";

    /** Description of functionality, applicable scenarios, etc. */
    @Builder.Default
    private String description = "";

    /** Semantic/content version for compatibility aliases and factories. */
    @Builder.Default
    private String version = "";

    /** Optional input schema/params surface used by workflow-like cards. */
    private Object inputParams;

    /**
     * Override in subclasses to provide tool-specific information.
     */
    public Object toolInfo() {
        return null;
    }

    /**
     * Create a shallow copy of this card.
     */
    public BaseCard copy() {
        return BaseCard.builder()
            .id(this.id)
            .name(this.name)
            .description(this.description)
            .version(this.version)
            .build();
    }

    public static BaseCardBuilder builder() {
        return new BaseCardBuilder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Object getInputParams() {
        return inputParams;
    }

    public void setInputParams(Object inputParams) {
        this.inputParams = inputParams;
    }

    public static class BaseCardBuilder {
        private String id = UUID.randomUUID().toString().replace("-", "");
        private String name = "";
        private String description = "";
        private String version = "";
        private Object inputParams;

        public BaseCardBuilder id(String id) {
            this.id = id;
            return this;
        }

        public BaseCardBuilder name(String name) {
            this.name = name;
            return this;
        }

        public BaseCardBuilder description(String description) {
            this.description = description;
            return this;
        }

        public BaseCardBuilder version(String version) {
            this.version = version;
            return this;
        }

        public BaseCardBuilder inputParams(Object inputParams) {
            this.inputParams = inputParams;
            return this;
        }

        public BaseCard build() {
            BaseCard card = new BaseCard();
            card.setId(id);
            card.setName(name);
            card.setDescription(description);
            card.setVersion(version);
            card.setInputParams(inputParams);
            return card;
        }
    }

    @Override
    public String toString() {
        return "id=" + id + ",name=" + name;
    }
}
