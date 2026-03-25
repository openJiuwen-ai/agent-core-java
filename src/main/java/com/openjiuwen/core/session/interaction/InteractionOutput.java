/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import java.util.Objects;

/**
 * Output payload for interaction events.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.interaction.interaction.InteractionOutput}.
 */
public class InteractionOutput {

    private String id;
    private Object value;

    public InteractionOutput() {
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

    @Override
    public int hashCode() {
        return Objects.hash(id, value);
    }
}
