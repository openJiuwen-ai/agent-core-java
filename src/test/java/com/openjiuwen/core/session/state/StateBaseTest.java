/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's state-base behaviors in
 * {@code openjiuwen/core/session/state/base.py}.
 */
class StateBaseTest {

    @Test
    void inMemoryStateLikeMergesNestedUpdatesAndReturnsDeepCopies() {
        InMemoryStateLike state = new InMemoryStateLike(new LinkedHashMap<>(Map.of(
                "data", new LinkedHashMap<>(Map.of("left", 1))
        )));

        state.update(Map.of("data", Map.of("right", 2)));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) state.get("data");
        assertEquals(1, data.get("left"));
        assertEquals(2, data.get("right"));

        data.put("extra", 3);
        assertNull(((Map<?, ?>) state.get("data")).get("extra"));
    }

    @Test
    void inMemoryStateLikeSetStateIgnoresEmptyMap() {
        InMemoryStateLike state = new InMemoryStateLike(new LinkedHashMap<>(Map.of("key", "value")));

        state.setState(new LinkedHashMap<>());

        assertEquals("value", state.get("key"));
    }

    @Test
    void inMemoryCommitStateCommitAndRollbackMatchPythonSemantics() {
        InMemoryCommitState commitState = new InMemoryCommitState();
        commitState.updateById("node-a", Map.of("alpha", 1));
        commitState.updateById("node-b", Map.of("beta", 2));
        commitState.rollback("node-b");

        commitState.commit();

        assertEquals(1, commitState.get("alpha"));
        assertNull(commitState.get("beta"));
        assertTrue(commitState.getUpdates().isEmpty());
    }

    @Test
    void inMemoryCommitStateTransformerReceivesStateLikeInstance() {
        InMemoryCommitState commitState = new InMemoryCommitState();

        Object transformed = commitState.getByTransformer(candidate -> candidate instanceof StateLike);

        assertEquals(Boolean.TRUE, transformed);
    }

    @Test
    void inMemoryCommitStateSetUpdatesIgnoresEmptyMap() {
        InMemoryCommitState commitState = new InMemoryCommitState();
        commitState.updateById("node-a", Map.of("alpha", 1));
        Map<String, Object> before = commitState.getUpdates();

        commitState.setUpdates(new LinkedHashMap<>());

        assertSame(before, commitState.getUpdates());
        assertFalse(commitState.getUpdates().isEmpty());
    }

    @Test
    void inMemoryCommitStateUpdateWithoutNodeIdRaisesFrameworkError() {
        InMemoryCommitState commitState = new InMemoryCommitState();

        assertThrows(RuntimeException.class, () -> commitState.update(Map.of("key", "value")));
        assertThrows(RuntimeException.class, () -> commitState.updateById(null, Map.of("key", "value")));
    }
}
