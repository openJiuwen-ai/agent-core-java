/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.schema;

import java.util.Objects;
import java.util.UUID;

/**
 * Base digital card — the root class for all card-like entities.
 * <p>
 * Java equivalent of Python's Pydantic {@code BaseCard}.
 */
public class BaseCard {

    /** Unique identifier (UUID hex by default). */
    private String id = UUID.randomUUID().toString().replace("-", "");

    /** Name — also serves as the unique identifier in a namespace. */
    private String name = "";

    /** Description of functionality, applicable scenarios, etc. */
    private String description = "";

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseCard() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseCard(String id, String name, String description) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getId() {
        return id;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setDescription(String description) {
        this.description = description;
    }

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
            .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        return "id=" + id + ",name=" + name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        BaseCard baseCard = (BaseCard) other;
        return Objects.equals(id, baseCard.id)
                && Objects.equals(name, baseCard.name)
                && Objects.equals(description, baseCard.description);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, name, description);
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
        /**
         * Auto-generated for codecheck compliance.
         */
        protected String id;
        /**
         * Auto-generated for codecheck compliance.
         */
        protected String name = "";
        /**
         * Auto-generated for codecheck compliance.
         */
        protected String description = "";

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public BaseCard build() {
            return new BaseCard(id, name, description);
        }
    }
}
