/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.store.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryGraphStoreOrderTest {
    @Test
    void equalScoreTopKPreservesInsertionOrderAcrossReadsAndUpserts() {
        InMemoryGraphStore store = new InMemoryGraphStore(
                GraphConfig.builder().backend("in_memory").uri("memory-order-test").build());
        try {
            store.addData("episodes", List.of(row("z"), row("a"), row("m")), false, false, Map.of()).join();
            for (int attempt = 0; attempt < 20; attempt++) {
                assertEquals(List.of("z", "a"), firstIds(store));
            }
            store.addData("episodes", List.of(row("z")), false, true, Map.of()).join();
            assertEquals(List.of("a", "m"), firstIds(store));
        } finally {
            store.close();
        }
    }

    private static Map<String, Object> row(String id) {
        return Map.of("uuid", id, "content", "equal score", "user_id", "owner");
    }

    private static List<String> firstIds(InMemoryGraphStore store) {
        return store.search("query", 2, "episodes", null, null, 0, 0, null, null, null, Map.of())
                .join().get("episodes").stream().map(row -> String.valueOf(row.get("uuid"))).toList();
    }
}
