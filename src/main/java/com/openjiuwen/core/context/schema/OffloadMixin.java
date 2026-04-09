  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.context.schema;

import java.util.Map;

/**
 * Marker interface for messages that have been offloaded from the context window.
 * <p>
 * Mirrors Python's {@code OffloadMixin} from {@code context_engine/schema/messages.py}.
 */
public interface OffloadMixin {

    /** Storage type (e.g., "in_memory"). */
    String getOffloadType();

    /** Unique handle to retrieve offloaded content. */
    String getOffloadHandle();

    /** Arbitrary metadata attached to the offloaded message. */
    Map<String, Object> getMetadata();
}
