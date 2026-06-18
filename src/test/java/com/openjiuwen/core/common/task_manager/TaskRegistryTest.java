/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for {@link TaskRegistry}.
 *
 * <p>Mirrors Python's {@code TaskRegistry} in
 * {@code openjiuwen/core/common/task_manager/registry.py}.</p>
 */
class TaskRegistryTest {

    @Test
    void addGetContainsAndGroupLookupMirrorPythonRegistry() {
        TaskRegistry registry = new TaskRegistry();
        Task task = new Task("task-a", "alpha", "workers", null, Map.of());

        registry.add(task);

        assertThat(registry.contains("task-a")).isTrue();
        assertThat(registry.get("task-a")).isSameAs(task);
        assertThat(registry.getByGroup("workers")).containsExactly(task);
        assertThat(registry.getGroupTaskIds("workers")).containsExactly("task-a");
    }

    @Test
    void parentAndStatusLookupsReturnLiveTasksOnly() {
        TaskRegistry registry = new TaskRegistry();
        Task parent = new Task("parent");
        Task child = new Task("child");
        child.setParentTaskId(parent.getTaskId());
        parent.complete("done");
        child.start();

        registry.add(parent);
        registry.add(child);

        assertThat(registry.getByParent("parent")).containsExactly(child);
        assertThat(registry.getByStatus(TaskStatus.COMPLETED)).containsExactly(parent);
        assertThat(registry.getRunning()).containsExactly(child);
        assertThat(registry.getAll()).containsExactlyInAnyOrder(parent, child);
    }

    @Test
    void keysValuesItemsAndPopMirrorMappingSurface() {
        TaskRegistry registry = new TaskRegistry();
        Task task = new Task("task-a", null, "group-a", null, Map.of());
        Task fallback = new Task("fallback");

        registry.add(task);

        assertThat(registry.keys()).containsExactly("task-a");
        assertThat(registry.values()).containsExactly(task);
        assertThat(registry.items()).singleElement().satisfies(entry -> {
            assertThat(entry.getKey()).isEqualTo("task-a");
            assertThat(entry.getValue()).isSameAs(task);
        });
        assertThat(registry.pop("missing", fallback)).isSameAs(fallback);
        assertThat(registry.pop("task-a")).isSameAs(task);
        assertThat(registry.contains("task-a")).isFalse();
    }

    @Test
    void removeUnsafeUpdatesGroupIndex() {
        TaskRegistry registry = new TaskRegistry();
        Task first = new Task("task-a", null, "workers", null, Map.of());
        Task second = new Task("task-b", null, "workers", null, Map.of());
        registry.add(first);
        registry.add(second);

        assertThat(registry.removeUnsafe("task-a")).isSameAs(first);

        assertThat(registry.getByGroup("workers")).containsExactly(second);
        assertThat(registry.getGroupTaskIds("workers")).containsExactly("task-b");
        assertThat(registry.removeUnsafe("task-b")).isSameAs(second);
        assertThat(registry.getByGroup("workers")).isEmpty();
        assertThat(registry.getGroupTaskIds("workers")).isEmpty();
    }

    @Test
    void missingLookupsReturnEmptyCollectionsOrNull() {
        TaskRegistry registry = new TaskRegistry();

        assertThat(registry.get("missing")).isNull();
        assertThat(registry.contains("missing")).isFalse();
        assertThat(registry.getByGroup("missing")).isEmpty();
        assertThat(registry.getByParent("missing")).isEmpty();
        assertThat(registry.getByStatus(TaskStatus.RUNNING)).isEmpty();
        assertThat(registry.getRunning()).isEmpty();
        assertThat(registry.getAll()).isEmpty();
        assertThat(registry.keys()).isEmpty();
        assertThat(registry.items()).isEmpty();
        assertThat(registry.values()).isEmpty();
    }

    @Test
    void weakValueCleanupRemovesStaleGroupReferencesOnAccess() throws Exception {
        TaskRegistry registry = new TaskRegistry();
        addShortLivedTask(registry);

        for (int index = 0; index < 10 && registry.contains("ephemeral"); index++) {
            System.gc();
            Thread.sleep(20L);
        }

        if (!registry.contains("ephemeral")) {
            assertThat(registry.getByGroup("temporary")).isEmpty();
            assertThat(registry.getGroupTaskIds("temporary")).isEmpty();
        }
    }

    private static void addShortLivedTask(TaskRegistry registry) {
        Task task = new Task("ephemeral", null, "temporary", null, Map.of());
        registry.add(task);
    }
}
