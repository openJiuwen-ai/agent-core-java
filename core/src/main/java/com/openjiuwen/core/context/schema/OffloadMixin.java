/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import java.util.Map;

/**
 * Backward-compatible marker and accessor contract for offloaded messages.
 *
 * <p>Mirrors Python's {@code OffloadMixin} in
 * {@code openjiuwen/core/context_engine/schema/messages.py}.</p>
 */
public interface OffloadMixin {

    String getOffloadType();

    void setOffloadType(String offloadType);

    String getOffloadHandle();

    void setOffloadHandle(String offloadHandle);

    Map<String, Object> modelDump();

    Map<String, Object> model_dump();
}
