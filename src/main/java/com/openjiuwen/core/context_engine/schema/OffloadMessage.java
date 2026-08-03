/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.schema;

import java.util.Map;

/**
 * Mirrors Python's {@code OffloadMixin} in
 * {@code openjiuwen/core/context_engine/schema/messages.py}.
 */
public interface OffloadMessage extends com.openjiuwen.core.context.schema.OffloadMixin {
    String getOffloadType();

    void setOffloadType(String offloadType);

    String getOffloadHandle();

    void setOffloadHandle(String offloadHandle);

    Map<String, Object> modelDump();

    Map<String, Object> model_dump();
}
