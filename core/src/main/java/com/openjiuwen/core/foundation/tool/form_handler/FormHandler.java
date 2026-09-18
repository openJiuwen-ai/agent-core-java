/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.form_handler;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Abstract interface for form data processing.
 *
 * <p>Mirrors Python's {@code FormHandler} in
 * {@code openjiuwen/core/foundation/tool/form_handler/form_handler_manager.py}.</p>
 */
public interface FormHandler {

    default CompletionStage<ToolFormData> handle(ToolFormData form, Map<String, Object> formData) {
        return handle(form, formData, Map.of());
    }

    CompletionStage<ToolFormData> handle(
            ToolFormData form,
            Map<String, Object> formData,
            Map<String, Object> kwargs
    );

    static CompletionStage<ToolFormData> completed(ToolFormData form) {
        return CompletableFuture.completedFuture(form);
    }
}
