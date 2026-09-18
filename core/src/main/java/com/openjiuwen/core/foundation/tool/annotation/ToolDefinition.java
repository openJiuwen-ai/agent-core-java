/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation-based Java equivalent of Python's {@code @tool} decorator.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolDefinition {
    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    String name() default "";

    /**
     * description.
     * 
     * @return the result
     * @since 0.1.7
     */
    String description() default "";

    /**
     * autoExtract.
     * 
     * @return the result
     * @since 0.1.7
     */
    boolean autoExtract() default true;
}
