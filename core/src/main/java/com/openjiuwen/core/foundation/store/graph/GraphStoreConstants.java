/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.Map;

/**
 * Graph store constants that must remain aligned with the Python module.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.foundation.store.graph.constants} in
 * {@code openjiuwen/core/foundation/store/graph/constants.py}.
 */
public final class GraphStoreConstants {

    public static final String ENTITY_COLLECTION = "ENTITY_COLLECTION";
    public static final String RELATION_COLLECTION = "RELATION_COLLECTION";
    public static final String EPISODE_COLLECTION = "EPISODE_COLLECTION";
    public static final Map<String, Integer> VARCHAR_LIMIT = Map.of("gt", 1, "le", 65535);
    public static final Map<String, Integer> ARRAY_LIMIT = Map.of("gt", 1, "le", 4096);
    public static final int DEFAULT_WORKER_NUM = 10;

    private GraphStoreConstants() {
    }
}
