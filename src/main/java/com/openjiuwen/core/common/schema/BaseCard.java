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

    @Override
    public String toString() {
        return "id=" + id + ",name=" + name;
    }
}
