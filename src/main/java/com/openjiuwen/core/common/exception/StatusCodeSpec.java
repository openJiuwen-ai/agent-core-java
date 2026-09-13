/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

/**
 * A fully-specified status code entry generated from a {@link StatusCodeTemplate}.
 * @since 0.1.7
 */
public record StatusCodeSpec(String name, int code, String message) {
    /**
     * fromTemplate.
     * @param template template
     * @param code code
     * @return the result
     * @since 0.1.7
     */
    public static StatusCodeSpec fromTemplate(StatusCodeTemplate template, int code) {
        return new StatusCodeSpec(template.name(), code, template.messageTemplate());
    }

    /**
     * renderEnumMember.
     * @return the result
     * @since 0.1.7
     */
    public String renderEnumMember() {
        return "    " + name + "(" + code + ", \"" + message + "\")";
    }
}
