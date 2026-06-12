/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.form_handler;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Generic form handler for simple key-value form data.
 *
 * <p>Mirrors Python's {@code DefaultFormHandler} in
 * {@code openjiuwen/core/foundation/tool/form_handler/form_handler_manager.py}.</p>
 */
public final class DefaultFormHandler implements FormHandler {

    @Override
    public CompletionStage<ToolFormData> handle(
            ToolFormData form,
            Map<String, Object> formData,
            Map<String, Object> kwargs
    ) {
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            form.addField(entry.getKey(), String.valueOf(value));
        }
        return FormHandler.completed(form);
    }
}
