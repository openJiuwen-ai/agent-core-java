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
 * 
 * @since 0.1.7
 */
public class BaseCard {
    private String id = UUID.randomUUID().toString().replace("-", "");

    /** Name — also serves as the unique identifier in a namespace. */
    private String name = "";

    /** Description of functionality, applicable scenarios, etc. */
    private String description = "";

    /**
     * BaseCard.
     * 
     * @since 0.1.7
     */
    public BaseCard() {
    }

    /**
     * BaseCard.
     * 
     * @param id id
     * @param name name
     * @param description description
     * @since 0.1.7
     */
    public BaseCard(String id, String name, String description) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
    }

    /**
     * getId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getId() {
        return id;
    }

    /**
     * setId.
     * 
     * @param id id
     * @since 0.1.7
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * setName.
     * 
     * @param name name
     * @since 0.1.7
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * getDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDescription() {
        return description;
    }

    /**
     * setDescription.
     * 
     * @param description description
     * @since 0.1.7
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Override in subclasses to provide tool-specific information.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object toolInfo() {
        return null;
    }

    /**
     * Create a shallow copy of this card.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BaseCard copy() {
        return BaseCard.builder().id(this.id).name(this.name).description(this.description).build();
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "id=" + id + ",name=" + name;
    }

    /**
     * equals.
     * 
     * @param other other
     * @return the result
     * @since 0.1.7
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
        return Objects.equals(id, baseCard.id) && Objects.equals(name, baseCard.name)
                && Objects.equals(description, baseCard.description);
    }

    /**
     * hashCode.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, name, description);
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
        /**
         * id.
         * 
         * @since 0.1.7
         */
        protected String id;

        /**
         * name.
         * 
         * @since 0.1.7
         */
        protected String name = "";

        /**
         * description.
         * 
         * @since 0.1.7
         */
        protected String description = "";

        /**
         * id.
         * 
         * @param id id
         * @return the result
         * @since 0.1.7
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * description.
         * 
         * @param description description
         * @return the result
         * @since 0.1.7
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public BaseCard build() {
            return new BaseCard(id, name, description);
        }
    }
}
