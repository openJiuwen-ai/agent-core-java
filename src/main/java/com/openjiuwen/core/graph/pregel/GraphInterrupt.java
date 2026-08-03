/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mirrors Python's {@code GraphInterrupt} in
 * {@code openjiuwen/core/graph/pregel/base.py}.
 */
public class GraphInterrupt extends Exception {

    private final Object value;

    public GraphInterrupt() {
        this(null);
    }

    public GraphInterrupt(Object value) {
        super(String.valueOf(normalizeWorkflowInterruptPayload(value)));
        this.value = normalizeWorkflowInterruptPayload(value);
    }

    public Object getValue() {
        return value;
    }

    private static Object normalizeWorkflowInterruptPayload(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return value;
        }
        List<Object> normalized = new ArrayList<>(list.size());
        boolean changed = false;
        for (Object item : list) {
            if (item instanceof Interrupt interrupt && isWorkflowOutputSchema(interrupt.getValue())) {
                normalized.add(new Interrupt(normalizeWorkflowOutputSchema(interrupt.getValue())));
                changed = true;
            } else {
                normalized.add(item);
            }
        }
        return changed ? normalized : value;
    }

    private static boolean isWorkflowOutputSchema(Object value) {
        return value instanceof OutputSchema;
    }

    private static OutputSchema normalizeWorkflowOutputSchema(Object value) {
        OutputSchema output = (OutputSchema) value;
        Object payload = output.getPayload();
        if (!(payload instanceof InteractionOutput interactionOutput)
                || payload instanceof ComparableInteractionOutput) {
            return output;
        }
        output.setPayload(new ComparableInteractionOutput(interactionOutput));
        return output;
    }

    private static final class ComparableInteractionOutput extends InteractionOutput {

        private ComparableInteractionOutput(InteractionOutput source) {
            super(source.getId(), source.getValue());
            getMetadata().putAll(source.getMetadata());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof InteractionOutput that)) {
                return false;
            }
            return Objects.equals(getId(), that.getId())
                    && Objects.equals(getValue(), that.getValue())
                    && Objects.equals(getMetadata(), that.getMetadata());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getId(), getValue(), getMetadata());
        }

        @Override
        public String toString() {
            return "InteractionOutput{"
                    + "id='" + getId() + '\''
                    + ", value=" + getValue()
                    + ", metadata=" + getMetadata()
                    + '}';
        }
    }
}
