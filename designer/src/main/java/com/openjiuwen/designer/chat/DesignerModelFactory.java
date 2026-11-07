/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.chat;

import com.openjiuwen.designer.director.DesignerTextModelPort;

/**
 * Creates a director text model for one session.
 *
 * @since 2026-11-06
 */
@FunctionalInterface
public interface DesignerModelFactory {

    /**
     * Resolve the text model for a session.
     *
     * @param modelName selected model name
     * @return text model port
     */
    DesignerTextModelPort create(String modelName);
}
