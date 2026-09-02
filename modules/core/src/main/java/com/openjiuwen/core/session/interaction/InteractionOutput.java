/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.utils.SerializationUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Output payload for interaction events.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.interaction.interaction.InteractionOutput}.
 * 
 * @since 0.1.7
 */
public class InteractionOutput implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private Serializable value;

    /**
     * InteractionOutput.
     * 
     * @since 0.1.7
     */
    public InteractionOutput() {
    }

    /**
     * InteractionOutput.
     * 
     * @param id id
     * @param value value
     * @since 0.1.7
     */
    public InteractionOutput(String id, Object value) {
        this.id = id;
        if (value == null) {
            this.value = null;
        } else {
            this.value = SerializationUtils.requireSerializable(value, "value");
        }
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
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getValue() {
        return value;
    }

    /**
     * setValue.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setValue(Object value) {
        if (value == null) {
            this.value = null;
        } else {
            this.value = SerializationUtils.requireSerializable(value, "value");
        }
    }

    /**
     * equals.
     * 
     * @param o o
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InteractionOutput that)) {
            return false;
        }
        return Objects.equals(id, that.id) && Objects.equals(value, that.value);
    }

    /**
     * hashCode.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, value);
    }
}
