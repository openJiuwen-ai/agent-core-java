/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import java.util.LinkedHashMap;
import java.util.Map;

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
}
