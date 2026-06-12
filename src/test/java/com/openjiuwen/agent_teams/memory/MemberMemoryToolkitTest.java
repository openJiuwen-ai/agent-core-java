/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.memory.MemberMemoryToolkit.ClosableMemoryIndexManagerView;
import com.openjiuwen.agent_teams.memory.MemberMemoryToolkit.MemoryIndexManagerProvider;
import com.openjiuwen.agent_teams.memory.MemberMemoryToolkit.MemoryLocalFunction;
import com.openjiuwen.agent_teams.memory.MemberMemoryToolkit.MemoryManagerParams;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.MemorySearchResult;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.SearchOptions;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.ToolCard;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.ToolView;
import com.openjiuwen.harness.workspace.Workspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link MemberMemoryToolkit}.
 *
 * <p>Mirrors Python's {@code test_member_memory_toolkit.py} for
 * {@code openjiuwen/agent_teams/memory/member_memory_toolkit.py}.</p>
 */
class MemberMemoryToolkitTest {

    @TempDir
    Path tempDir;

    @Test
    void initializationGeneralScenarioCreatesGeneralContextAndTools() {
        FakeProvider provider = new FakeProvider();
        MemberMemoryToolkit toolkit = toolkit("alice", "team1", "general", false, provider);

        assertThat(await(toolkit.initialize())).isTrue();

        assertThat(toolkit.getScenario()).isEqualTo("general");
        assertThat(toolkit.getContext()).isInstanceOf(MemberMemoryToolkit.MemoryToolContext.class);
        assertThat(toolNames(toolkit.getTools()))
                .containsExactly("memory_search", "memory_get", "read_memory", "write_memory", "edit_memory");
        assertThat(provider.requests.getFirst().agentId()).isEqualTo("team1.alice");
        assertThat(provider.requests.getFirst().nodeName()).isEqualTo("memory");
    }

    @Test
    void initializationCodingScenarioCreatesCodingContextAndTools() {
        FakeProvider provider = new FakeProvider();
        MemberMemoryToolkit toolkit = toolkit("bob", "team1", "coding", false, provider);

        assertThat(await(toolkit.initialize())).isTrue();

        assertThat(toolkit.getScenario()).isEqualTo("coding");
        assertThat(toolkit.getContext()).isInstanceOf(MemberMemoryToolkit.CodingMemoryToolContext.class);
        assertThat(toolNames(toolkit.getTools()))
                .containsExactly("coding_memory_read", "coding_memory_write", "coding_memory_edit");
        assertThat(provider.requests.getFirst().nodeName()).isEqualTo("coding_memory");
    }

    @Test
    void scenarioNormalizationMatchesPython() {
        MemberMemoryToolkit toolkit = toolkit("charlie", "team1", "  CODING  ", false, new FakeProvider());

        assertThat(toolkit.getScenario()).isEqualTo("coding");
    }

    @Test
    void readOnlyFlagIsPreserved() {
        MemberMemoryToolkit toolkit = toolkit("dave", "team1", "general", true, new FakeProvider());

        assertThat(toolkit.isReadOnly()).isTrue();
    }

    @Test
    void getToolsAndToolCardsReturnLists() {
        MemberMemoryToolkit toolkit = toolkit("eve", "team1", "general", false, new FakeProvider());

        assertThat(toolkit.getTools()).isEmpty();
        assertThat(toolkit.getToolCards()).isEmpty();

        await(toolkit.initialize());

        assertThat(toolkit.getTools()).hasSize(5);
        assertThat(toolkit.getToolCards()).extracting(ToolCard::name).contains("memory_search");
    }

    @Test
    void closeCleansUpResources() {
        FakeProvider provider = new FakeProvider();
        MemberMemoryToolkit toolkit = toolkit("grace", "team1", "general", false, provider);
        await(toolkit.initialize());
        FakeManager manager = provider.managers.getFirst();

        await(toolkit.close());

        assertThat(manager.closed).isTrue();
        assertThat(toolkit.getManager()).isNull();
        assertThat(toolkit.getContext()).isNull();
        assertThat(toolkit.getTools()).isEmpty();
        assertThat(toolkit.isInitialized()).isFalse();
    }

    @Test
    void managerPropertyReturnsResolvedManager() {
        FakeProvider provider = new FakeProvider();
        MemberMemoryToolkit toolkit = toolkit("henry", "team1", "general", false, provider);

        assertThat(toolkit.getManager()).isNull();

        await(toolkit.initialize());

        assertThat(toolkit.getManager()).isSameAs(provider.managers.getFirst());
        assertThat(toolkit.manager()).isSameAs(provider.managers.getFirst());
    }

    @Test
    void createGeneralToolsReturnsFewerToolsInReadOnlyMode() {
        MemberMemoryToolkit readWrite = toolkit("jack", "team1", "general", false, new FakeProvider());
        MemberMemoryToolkit readOnly = toolkit("jack_ro", "team1", "general", true, new FakeProvider());
        await(readWrite.initialize());
        await(readOnly.initialize());

        List<MemoryLocalFunction> toolsReadWrite = MemberMemoryToolkit.createGeneralTools(readWrite, false);
        List<MemoryLocalFunction> toolsReadOnly = MemberMemoryToolkit.createGeneralTools(readOnly, true);

        assertThat(toolsReadWrite).hasSize(5);
        assertThat(toolsReadOnly).hasSize(3);
        assertThat(toolsReadOnly).extracting(tool -> tool.card().name())
                .doesNotContain("write_memory", "edit_memory");
    }

    @Test
    void createCodingToolsReturnsFewerToolsInReadOnlyMode() {
        MemberMemoryToolkit readWrite = toolkit("leo", "team1", "coding", false, new FakeProvider());
        MemberMemoryToolkit readOnly = toolkit("leo_ro", "team1", "coding", true, new FakeProvider());
        await(readWrite.initialize());
        await(readOnly.initialize());

        List<MemoryLocalFunction> toolsReadWrite = MemberMemoryToolkit.createCodingTools(readWrite, false);
        List<MemoryLocalFunction> toolsReadOnly = MemberMemoryToolkit.createCodingTools(readOnly, true);

        assertThat(toolsReadWrite).hasSize(3);
        assertThat(toolsReadOnly).hasSize(1);
        assertThat(toolsReadOnly).extracting(tool -> tool.card().name())
                .doesNotContain("coding_memory_write", "coding_memory_edit");
    }

    @Test
    void differentMembersHaveDifferentToolIds() {
        MemberMemoryToolkit toolkit1 = toolkit("alice", "team1", "general", false, new FakeProvider());
        MemberMemoryToolkit toolkit2 = toolkit("bob", "team1", "general", false, new FakeProvider());
        await(toolkit1.initialize());
        await(toolkit2.initialize());

        Set<String> ids1 = toolIds(toolkit1.getTools());
        Set<String> ids2 = toolIds(toolkit2.getTools());

        assertThat(ids1).doesNotContainAnyElementsOf(ids2);
    }

    @Test
    void twoToolkitsSameTeamUseDifferentManagersWhenInitialized() {
        FakeProvider provider = new FakeProvider();
        MemberMemoryToolkit toolkit1 = toolkit("m1", "team_iso", "general", false, provider);
        MemberMemoryToolkit toolkit2 = toolkit("m2", "team_iso", "general", false, provider);

        assertThat(await(toolkit1.initialize())).isTrue();
        assertThat(await(toolkit2.initialize())).isTrue();

        assertThat(toolkit1.getManager()).isNotSameAs(toolkit2.getManager());
    }

    @Test
    void initializeCloseDoesNotTouchExternalResourceRegistry() {
        List<String> resourceRegistry = new ArrayList<>();
        MemberMemoryToolkit toolkit = toolkit("solo", "team_x", "general", false, new FakeProvider());

        int before = resourceRegistry.size();
        assertThat(await(toolkit.initialize())).isTrue();
        await(toolkit.close());

        assertThat(resourceRegistry).hasSize(before);
    }

    @Test
    void generalToolNamesIncludeSearchCodingIncludesRead() {
        MemberMemoryToolkit general = toolkit("g", "t", "general", false, new FakeProvider());
        MemberMemoryToolkit coding = toolkit("c", "t", "coding", false, new FakeProvider());
        await(general.initialize());
        await(coding.initialize());

        assertThat(toolNames(general.getTools())).contains("memory_search");
        assertThat(toolNames(coding.getTools())).contains("coding_memory_read");
    }

    @Test
    void readOnlyInitializedGeneralToolsAreReadOnlyOnly() {
        MemberMemoryToolkit toolkit = toolkit("ro", "t", "general", true, new FakeProvider());

        assertThat(await(toolkit.initialize())).isTrue();

        Set<String> names = toolNames(toolkit.getTools());
        assertThat(names).contains("memory_search");
        assertThat(names).doesNotContain("write_memory", "edit_memory");
    }

    @Test
    void disabledMemoryDoesNotInitialize() {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit(
                "disabled",
                "team1",
                new Workspace(tempDir.toString(), "en"),
                "general",
                null,
                null,
                false,
                new FakeProvider(),
                () -> false
        );

        assertThat(await(toolkit.initialize())).isFalse();
        assertThat(toolkit.getTools()).isEmpty();
        assertThat(toolkit.getManager()).isNull();
    }

    private MemberMemoryToolkit toolkit(
            String memberName,
            String teamName,
            String scenario,
            boolean readOnly,
            MemoryIndexManagerProvider provider
    ) {
        return new MemberMemoryToolkit(
                memberName,
                teamName,
                new Workspace(tempDir.toString(), "en"),
                scenario,
                null,
                null,
                readOnly,
                provider,
                () -> true
        );
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static Set<String> toolNames(List<ToolView> tools) {
        Set<String> names = new LinkedHashSet<>();
        for (ToolView tool : tools) {
            names.add(tool.card().name());
        }
        return names;
    }

    private static Set<String> toolIds(List<ToolView> tools) {
        Set<String> ids = new LinkedHashSet<>();
        for (ToolView tool : tools) {
            ids.add(tool.card().id());
        }
        return ids;
    }

    private static final class FakeProvider implements MemoryIndexManagerProvider {
        private final List<MemoryManagerParams> requests = new ArrayList<>();
        private final List<FakeManager> managers = new ArrayList<>();

        @Override
        public CompletionStage<TeamMemoryManager.MemoryIndexManagerView> get(MemoryManagerParams params) {
            requests.add(params);
            FakeManager manager = new FakeManager();
            managers.add(manager);
            return CompletableFuture.completedFuture(manager);
        }
    }

    private static final class FakeManager implements ClosableMemoryIndexManagerView {
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
