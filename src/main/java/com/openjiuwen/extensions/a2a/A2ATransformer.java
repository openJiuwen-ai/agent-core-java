/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import java.util.*;

/**
 * A2A transformer — converts between openjiuwen and A2A formats.
 * <p>
 * Mirrors Python's {@code A2ATransformer} in
 * {@code openjiuwen.extensions.a2a.a2a_transformer}.
 */
public final class A2ATransformer {

    private A2ATransformer() {
    }

    /** Convert openjiuwen inputs to A2A request format. */
    public static Map<String, Object> toA2aRequest(Map<String, Object> inputs) {
        return new LinkedHashMap<>(inputs);
    }

    /** Convert A2A response to openjiuwen result format. */
    public static Map<String, Object> fromA2aResponse(Map<String, Object> response) {
        return new LinkedHashMap<>(response);
    }
}
