/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

/**
 * Re-export of {@link com.openjiuwen.core.workflow.component.ComponentAbility}.
 * Provides the enum values at the top-level workflow package for test compatibility.
 */
public enum ComponentAbility {
    INVOKE, STREAM, COLLECT, TRANSFORM;

    /**
     * Convert to the internal {@link com.openjiuwen.core.workflow.component.ComponentAbility}.
     */
    public com.openjiuwen.core.workflow.component.ComponentAbility toInternal() {
        return com.openjiuwen.core.workflow.component.ComponentAbility.valueOf(this.name());
    }

    /**
     * Convert from the internal enum.
     */
    public static ComponentAbility fromInternal(com.openjiuwen.core.workflow.component.ComponentAbility internal) {
        return ComponentAbility.valueOf(internal.name());
    }
}
