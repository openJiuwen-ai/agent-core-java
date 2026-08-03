/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

/**
 * Mirrors Python's {@code StatusCodeSpec} in
 * {@code openjiuwen/core/common/exception/code_template.py}.
 *
 * @param name enum member name
 * @param code numeric status code
 * @param message rendered message template
 */
public record StatusCodeSpec(String name, int code, String message) {

    public static StatusCodeSpec generateStatusCodeSpec(StatusCodeTemplate template, int code) {
        return new StatusCodeSpec(template.name(), code, template.messageTemplate());
    }

    public static String renderEnumMember(StatusCodeSpec spec) {
        return spec.renderEnumMember();
    }

    public String renderEnumMember() {
        return "    " + name + " = (" + code + ", \"" + message + "\")";
    }
}
