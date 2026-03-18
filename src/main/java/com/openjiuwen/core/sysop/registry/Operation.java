/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.registry;

import com.openjiuwen.core.sysop.OperationMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for registering a class as an operation in the OperationRegistry.
 * <p>
 * Mirrors Python's {@code @operation} decorator in {@code sys_operation/registry.py}.
 *
 * <p>Usage:
 * <pre>
 * {@literal @}Operation(name = "shell", mode = OperationMode.LOCAL, description = "local shell operation")
 * public class LocalShellOperation extends BaseShellOperation { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Operation {

    /** Unique identifier for the operation (e.g., "fs", "shell", "code"). */
    String name();

    /** Running mode (LOCAL or SANDBOX). */
    OperationMode mode();

    /** Human-readable description. */
    String description() default "";
}
