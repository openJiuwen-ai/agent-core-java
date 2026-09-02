/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import java.util.Map;

/**
 * Marker interface for messages that have been offloaded from the context window.
 * <p>
 * Mirrors Python's {@code OffloadMixin} from {@code context_engine/schema/messages.py}.
 * 
 * @since 0.1.7
 */
public interface OffloadMixin {
    /**
     * getOffloadType.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getOffloadType();

    /**
     * Unique handle to retrieve offloaded content.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getOffloadHandle();

    /**
     * Arbitrary metadata attached to the offloaded message.
     * 
     * @return the result
     * @since 0.1.7
     */
    Map<String, Object> getMetadata();
}
