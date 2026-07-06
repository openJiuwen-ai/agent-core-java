/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Interaction output payload.
 *
 * <p>Mirrors Python's {@code InteractionOutput} in
 * {@code openjiuwen/core/session/interaction/interaction.py}.</p>
 */
public class InteractionOutput {

    private String id;
    private Object value;
    private final Map<String, Object> metadata = new LinkedHashMap<>();

    public InteractionOutput() {
    }

    public InteractionOutput(Object value) {
        this.value = value;
    }

    public InteractionOutput(String id, Object value) {
        this.id = id;
        this.value = value;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InteractionOutput that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(value, that.value)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, value, metadata);
    }

    @Override
    public String toString() {
        return "InteractionOutput{"
                + "id='" + id + '\''
                + ", value=" + value
                + ", metadata=" + metadata
                + '}';
    }
}
