/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Canvas layout of one designer node.
 *
 * @param x horizontal position
 * @param y vertical position
 * @param width node width
 * @param height node height
 * @since 2026-11-06
 */
public record DesignerLayout(
        @JsonProperty("x") double x,
        @JsonProperty("y") double y,
        @JsonProperty("width") double width,
        @JsonProperty("height") double height) {

    /** Default layout used when a node has no layout. */
    public static final DesignerLayout DEFAULT = new DesignerLayout(0.0d, 0.0d, 0.0d, 0.0d);
}
