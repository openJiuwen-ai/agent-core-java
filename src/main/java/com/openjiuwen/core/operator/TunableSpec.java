/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.operator;

/**
 * Describes a single tunable parameter of an operator.
 *
 * @param name parameter name
 * @param kind tunable kind, for example prompt or discrete
 * @param path path of the parameter inside the operator
 * @param constraint optional constraint metadata
 */
public record TunableSpec(String name, String kind, String path, Object constraint) {

    public TunableSpec(String name, String kind, String path) {
        this(name, kind, path, null);
    }
}
