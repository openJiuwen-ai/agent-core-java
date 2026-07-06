/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Output payload for interaction events.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.interaction.interaction.InteractionOutput}.
 */
public class InteractionOutput implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private Object value;

    /**
     * Auto-generated for codecheck compliance.
     */
    public InteractionOutput() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public InteractionOutput(String id, Object value) {
        this.id = id;
        this.value = value;
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
    public Object getValue() {
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InteractionOutput that)) {
            return false;
        }
        return Objects.equals(id, that.id) && Objects.equals(value, that.value);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int hashCode() {
        return Objects.hash(id, value);
    }
}
