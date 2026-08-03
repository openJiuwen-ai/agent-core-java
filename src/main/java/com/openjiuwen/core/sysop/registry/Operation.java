/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.registry;

import com.openjiuwen.core.sysop.OperationMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for declaring an operation class with its registration metadata.
 *
 * <p>Mirrors Python's {@code Operation} decorator in
 * {@code openjiuwen/core/sys_operation/registry.py}.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Operation {

    /**
     * Operation name (e.g., "fs", "shell", "code").
     */
    String name();

    /**
     * Operation mode (LOCAL or SANDBOX).
     */
    OperationMode mode();

    /**
     * Human-readable description of the operation.
     */
    String description() default "";
}
