/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.artifacts;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactStoreTest {

    @Test
    void taskLookupOverridesSessionAndFallsBackWhenAbsent() {
        ArtifactStore store = new ArtifactStore();
        store.put("shared", "session-value");
        store.put("shared", "task-value", "task-1");

        assertThat(store.get("shared", "task-1")).isEqualTo("task-value");
        assertThat(store.get("shared", "task-2")).isEqualTo("session-value");
        assertThat(store.get("missing", "task-1", "fallback")).isEqualTo("fallback");
    }

    @Test
    void requireUsesScopedMissingArtifactMessage() {
        ArtifactStore store = new ArtifactStore();

        assertThatThrownBy(() -> store.require("missing"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Missing artifact 'missing' in session");
        assertThatThrownBy(() -> store.require("missing", "task-7"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Missing artifact 'missing' in task=task-7");
    }

    @Test
    void putManyHasAndResetTaskMatchPythonBehavior() {
        ArtifactStore store = new ArtifactStore();
        store.putMany(Map.of("a", 1, "b", 2), "task-1");
        store.put("session-only", 3);

        assertThat(store.has("a", "task-1")).isTrue();
        assertThat(store.has("b", "task-1")).isTrue();
        assertThat(store.has("session-only", "task-1")).isTrue();

        store.resetTask("task-1");

        assertThat(store.has("a", "task-1")).isFalse();
        assertThat(store.has("session-only", "task-1")).isTrue();
        assertThat(store.get("session-only")).isEqualTo(3);
    }
}
