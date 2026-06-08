/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's git-ignore filtering behavior in
 * {@code openjiuwen/harness/lsp/core/utils/git_ignore.py}.
 */
class GitIgnoreFilterTest {

    @Test
    void uriToFilePathDelegatesToFileUriUtils() {
        assertEquals(
                FileUriUtils.fileUriToPath("file:///workspace/a.py"),
                GitIgnoreFilter.uriToFilePath("file:///workspace/a.py")
        );
    }

    @Test
    void filterReturnsOriginalListWhenThereAreNoResolvableUris() {
        List<Map<String, Object>> locations = List.of(Map.of("name", "symbol"));

        List<Map<String, Object>> filtered = GitIgnoreFilter.filterGitIgnoredLocations(
                locations,
                "/repo",
                (batch, cwd) -> Set.of()
        );

        assertSame(locations, filtered);
    }

    @Test
    void filterRemovesIgnoredUriAndTargetUriEntries() {
        Map<String, Object> uriLocation = new LinkedHashMap<>();
        uriLocation.put("uri", "file:///repo/ignored.py");
        Map<String, Object> targetUriLocation = new LinkedHashMap<>();
        targetUriLocation.put("targetUri", "file:///repo/kept.py");

        List<Map<String, Object>> filtered = GitIgnoreFilter.filterGitIgnoredLocations(
                List.of(uriLocation, targetUriLocation),
                "/repo",
                (batch, cwd) -> Set.of(
                        GitIgnoreFilter.uriToFilePath("file:///repo/ignored.py")
                )
        );

        assertEquals(1, filtered.size());
        assertSame(targetUriLocation, filtered.get(0));
    }

    @Test
    void nestedLocationUriIsCollectedButNotFilteredWithoutTopLevelUri() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("location", Map.of("uri", "file:///repo/ignored.py"));

        List<Map<String, Object>> filtered = GitIgnoreFilter.filterGitIgnoredLocations(
                List.of(nested),
                "/repo",
                (batch, cwd) -> Set.of(GitIgnoreFilter.uriToFilePath("file:///repo/ignored.py"))
        );

        assertEquals(1, filtered.size());
        assertSame(nested, filtered.get(0));
    }

    @Test
    void filterBatchesUniquePathsAndSwallowsRunnerFailure() {
        List<Map<String, Object>> locations = new ArrayList<>();
        for (int index = 0; index < 55; index++) {
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("uri", "file:///repo/file-" + index + ".py");
            locations.add(location);
        }

        AtomicInteger callCount = new AtomicInteger();
        AtomicInteger maxBatchSize = new AtomicInteger();

        List<Map<String, Object>> filtered = GitIgnoreFilter.filterGitIgnoredLocations(
                locations,
                "/repo",
                (batch, cwd) -> {
                    callCount.incrementAndGet();
                    maxBatchSize.updateAndGet(current -> Math.max(current, batch.size()));
                    if (callCount.get() == 1) {
                        throw new IOException("simulated failure");
                    }
                    return Set.of(GitIgnoreFilter.uriToFilePath("file:///repo/file-54.py"));
                }
        );

        assertEquals(2, callCount.get());
        assertTrue(maxBatchSize.get() <= 50);
        assertEquals(54, filtered.size());
    }
}
