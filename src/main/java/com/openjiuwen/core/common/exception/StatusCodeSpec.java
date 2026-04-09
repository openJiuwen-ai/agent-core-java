  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.common.exception;

/**
 * A fully-specified status code entry generated from a {@link StatusCodeTemplate}.
 *
 * @param name    the enum member name
 * @param code    the integer status code
 * @param message the error message template
 */
public record StatusCodeSpec(String name, int code, String message) {

    /**
     * Create a spec by combining a template with a concrete code value.
     */
    public static StatusCodeSpec fromTemplate(StatusCodeTemplate template, int code) {
        return new StatusCodeSpec(template.name(), code, template.messageTemplate());
    }

    /**
     * Render as a Java enum member declaration string (for codegen).
     */
    public String renderEnumMember() {
        return "    " + name + "(" + code + ", \"" + message + "\")";
    }
}
