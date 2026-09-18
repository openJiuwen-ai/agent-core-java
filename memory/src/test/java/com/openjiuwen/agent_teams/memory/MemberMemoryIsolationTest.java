/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.memory.MemberMemoryToolkit.ClosableMemoryIndexManagerView;
import com.openjiuwen.agent_teams.memory.MemberMemoryToolkit.MemoryIndexManagerProvider;
import com.openjiuwen.agent_teams.memory.MemberMemoryToolkit.MemoryManagerParams;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.MemorySearchResult;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.SearchOptions;
import com.openjiuwen.harness.workspace.Workspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <p>Mirrors Python's {@code test_two_members_distinct_managers_and_disk_paths} in
 * {@code tests/unit_tests/core/memory/team/test_memory_isolation.py}.</p>
 */
class MemberMemoryIsolationTest {

    @TempDir
    Path tempDir;

    @Test
    void twoMembersUseDistinctManagersAndDiskPaths() throws Exception {
        Path rootA = Files.createDirectories(tempDir.resolve("ws_a"));
        Path rootB = Files.createDirectories(tempDir.resolve("ws_b"));
        RecordingProvider provider = new RecordingProvider();

        MemberMemoryToolkit toolkitA = toolkit("m1", "same_team", rootA, provider);
        MemberMemoryToolkit toolkitB = toolkit("m2", "same_team", rootB, provider);

        assertThat(await(toolkitA.initialize())).isTrue();
        assertThat(await(toolkitB.initialize())).isTrue();

        assertThat(toolkitA.getManager()).isNotNull();
        assertThat(toolkitB.getManager()).isNotNull();
        assertThat(toolkitA.getManager()).isNotSameAs(toolkitB.getManager());
        assertThat(provider.requests).extracting(MemoryManagerParams::nodeName)
                .containsExactly("memory", "memory");
        assertThat(provider.requests).extracting(request -> request.workspace().root())
                .containsExactly(rootA.normalize(), rootB.normalize());

        Path marker = rootA.resolve("memory").resolve("m1_exclusive.txt");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "only-a");

        Path otherPath = rootB.resolve("memory").resolve("m1_exclusive.txt");
        assertThat(Files.isRegularFile(otherPath)).isFalse();

        await(toolkitA.close());
        await(toolkitB.close());
        assertThat(provider.managers).extracting(RecordingManager::isClosed)
                .containsExactly(Boolean.TRUE, Boolean.TRUE);
    }

    private static MemberMemoryToolkit toolkit(
            String memberName,
            String teamName,
            Path root,
            MemoryIndexManagerProvider provider
    ) {
        return new MemberMemoryToolkit(
                memberName,
                teamName,
                new Workspace(root.toString(), "en"),
                "general",
                null,
                null,
                false,
                provider,
                () -> true
        );
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static final class RecordingProvider implements MemoryIndexManagerProvider {
        private final List<MemoryManagerParams> requests = new ArrayList<>();
        private final List<RecordingManager> managers = new ArrayList<>();

        @Override
        public CompletionStage<TeamMemoryManager.MemoryIndexManagerView> get(MemoryManagerParams params) {
            requests.add(params);
            RecordingManager manager = new RecordingManager();
            managers.add(manager);
            return CompletableFuture.completedFuture(manager);
        }
    }

    private static final class RecordingManager implements ClosableMemoryIndexManagerView {
        private boolean closed;

        @Override
        public CompletionStage<List<MemorySearchResult>> search(String query, SearchOptions options) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public CompletionStage<Void> close() {
            closed = true;
            return CompletableFuture.completedFuture(null);
        }
    }
}
