/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

/**
 * A tool result with an explicit model-facing representation independent of its structured payload.
 *
 * @since 0.1.17
 */
public interface ModelTextResult {
    /**
     * Return the complete text sent to the model, including relevant status and metadata.
     *
     * @return model-facing text, never null
     * @since 0.1.17
     */
    String modelText();
}
