/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.form_handler;

import java.util.Map;

/**
 * Abstract interface for form data processing.
 * <p>
 * Mirrors Python's {@code FormHandler} ABC from
 * <code>foundation/tool/form_handler/form_handler_manager.py</code>.
 *
 * @param <T> the form builder type
 */
public abstract class FormHandler<T> {

    /**
     * Process form data and add it to the form builder.
     *
     * @param form      the form builder to add fields to
     * @param formData  the data to add to the form
     * @param kwargs    additional parameters
     * @return the updated form builder
     */
    public abstract T handle(T form, Map<String, Object> formData, Map<String, Object> kwargs);
}
