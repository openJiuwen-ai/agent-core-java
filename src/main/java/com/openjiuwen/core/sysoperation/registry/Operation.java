// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.registry;

import com.openjiuwen.core.sysoperation.base.OperationMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for registering a class as an operation in the OperationRegistry.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.registry.operation decorator
 * 
 * <p>Usage:
 * <pre>{@code
 * @Operation(name = "fs", mode = OperationMode.LOCAL, description = "File system operation")
 * public class FsOperation extends BaseOperation {
 *     // Static initializer to register the class
 *     static {
 *         OperationRegistry.register(FsOperation.class, "fs", OperationMode.LOCAL, "File system operation");
 *     }
 *     // ...
 * }
 * }</pre>
 * 
 * <p><strong>Note:</strong> In Java, annotations don't automatically execute code like Python decorators.
 * Classes annotated with {@code @Operation} must include a static initializer that calls
 * {@link OperationRegistry#register} to register themselves.
 * 
 * <p>This annotation serves as documentation and can be used by annotation processors
 * or runtime reflection to discover operation classes.
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Operation {

    /**
     * Unique identifier for the operation.
     * 
     * @return the operation name (e.g., "fs", "code", "shell")
     */
    String name();

    /**
     * Running mode associated with the operation.
     * 
     * @return the operation mode (LOCAL or SANDBOX)
     */
    OperationMode mode();

    /**
     * Human-readable description of the operation.
     * 
     * @return the description, defaults to empty string
     */
    String description() default "";
}

