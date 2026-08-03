/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

/**
 * Describes one tunable operator parameter.
 *
 * <p>Mirrors Python's {@code TunableSpec} in
 * {@code openjiuwen/core/operator/base.py}.
 *
 * @param name parameter name
 * @param kind tunable kind
 * @param path parameter path inside the operator
 * @param constraint optional parameter constraint metadata
 */
public record TunableSpec(String name, String kind, String path, Object constraint) {

    public TunableSpec(String name, String kind, String path) {
        this(name, kind, path, null);
    }
}
