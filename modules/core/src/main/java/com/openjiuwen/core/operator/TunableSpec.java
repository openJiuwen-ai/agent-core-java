/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

/**
 * Describes a single tunable parameter of an operator.
 * 
 * @since 0.1.7
 */
public record TunableSpec(String name, String kind, String path, Object constraint) {
    public TunableSpec(String name, String kind, String path) {
        this(name, kind, path, null);
    }
}
