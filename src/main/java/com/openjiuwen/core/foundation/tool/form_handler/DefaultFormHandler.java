/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.form_handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic form handler for simple key-value form data.
 * <p>
 * Mirrors Python's {@code DefaultFormHandler} class from
 * <code>foundation/tool/form_handler/form_handler_manager.py</code>.
 *
 * <p>Converts form data entries into multipart form fields.
 * Null values are skipped.
 */
public class DefaultFormHandler extends FormHandler<List<Map.Entry<String, String>>> {

    @Override
    public List<Map.Entry<String, String>> handle(
            List<Map.Entry<String, String>> form,
            Map<String, Object> formData,
            Map<String, Object> kwargs) {
        if (form == null) {
            form = new ArrayList<>();
        }
        if (formData == null) {
            return form;
        }
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            Map.Entry<String, String> field = Map.entry(entry.getKey(), String.valueOf(entry.getValue()));
            form.add(field);
        }
        return form;
    }
}
