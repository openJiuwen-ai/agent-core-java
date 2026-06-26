/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.memory.TeamMemoryManager.AbilityManagerView;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.AddResult;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.DeepAgentView;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.ExtractionInvocation;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.ExtractionInvoker;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.MemberMemoryToolkitView;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.MemoryIndexManagerView;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.MemorySearchResult;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.Parameters;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.PromptBuilderView;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.ResourceManagerView;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.SearchOptions;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.SharedMemoryManagerFactory;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.SharedMemoryManagerView;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.ToolCard;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.ToolView;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.ToolkitFactory;
import com.openjiuwen.agent_teams.memory.TeamMemoryManager.ToolkitRequest;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.workspace.Workspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link TeamMemoryManager}.
 *
 * <p>Mirrors Python's {@code test_team_memory_manager.py} and
 * {@code test_team_memory_integration.py} for
 * {@code openjiuwen/agent_teams/memory/manager.py}.</p>
 *
 * <p>Mirrors Python's integration lifecycle tests in
 * {@code tests/unit_tests/core/memory/team/test_team_memory_integration.py}.</p>
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.memory.team.test_team_memory_manager} in
 * {@code tests/unit_tests/core/memory/team/test_team_memory_manager.py}.</p>
 *
 * <p>Mirrors Python's temporary read-only coverage in
 * {@code tests/unit_tests/core/memory/team/test_temporary_readonly.py}.</p>
 */
class TeamMemoryManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void initToolkitIsIdempotent() {
        TestParams params = createParams();
        TeamMemoryManager manager = new TeamMemoryManager(params);

        assertThat(await(manager.initToolkit())).isTrue();
        assertThat(await(manager.initToolkit())).isTrue();

        assertThat(params.toolkitFactory.toolkit.initializeCalls).isEqualTo(1);
        assertThat(manager.getToolkit()).isNotNull();
    }

    @Test
    void initToolkitReturnsFalseWithoutWorkspace() {
        TestParams params = createParams();
        params.workspace = null;
        TeamMemoryManager manager = new TeamMemoryManager(params);

        assertThat(await(manager.initToolkit())).isFalse();
        assertThat(manager.getToolkit()).isNull();
    }

    @Test
    void registerToolsIsIdempotent() {
        TestParams params = createParams();
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());
        FakeDeepAgent deepAgent = new FakeDeepAgent();

        manager.registerTools(deepAgent);
        List<String> namesAfterFirst = new ArrayList<>(manager.getOwnedToolNames());
        manager.registerTools(deepAgent);

        assertThat(manager.getOwnedToolNames()).containsExactlyElementsOf(namesAfterFirst);
        assertThat(deepAgent.abilityManager.addCalls).isEqualTo(1);
    }

    @Test
    void registerToolsStripsMemoryRails() {
        TestParams params = createParams();
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());
        FakeDeepAgent deepAgent = new FakeDeepAgent();
        deepAgent.pendingRails = 2;
        deepAgent.registeredRails = 2;

        manager.registerTools(deepAgent);

        assertThat(deepAgent.pendingRails).isZero();
        assertThat(deepAgent.staleRails).isEqualTo(2);
    }

    @Test
    void registerToolsHandlesMissingAbilityManager() {
        TestParams params = createParams();
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());
        FakeDeepAgent deepAgent = new FakeDeepAgent();
        deepAgent.abilityManager = null;

        manager.registerTools(deepAgent);

        assertThat(manager.getDeepAgentForCleanup()).isNull();
        assertThat(manager.getOwnedToolNames()).isEmpty();
    }

    @Test
    void loadAndInjectWithoutBuilderDoesNothing() {
        TestParams params = createParams();
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());
        FakeDeepAgent deepAgent = new FakeDeepAgent();
        deepAgent.promptBuilder = null;

        await(manager.loadAndInject(deepAgent, "test"));

        assertThat(manager.getCachedBaseSection()).isNull();
    }

    @Test
    void loadAndInjectReusesCachedBaseSection() {
        TestParams params = createParams();
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());
        FakeDeepAgent deepAgent = new FakeDeepAgent();

        await(manager.loadAndInject(deepAgent, ""));
        PromptSection firstSection = manager.getCachedBaseSection();
        await(manager.loadAndInject(deepAgent, ""));

        assertThat(manager.getCachedBaseSection()).isSameAs(firstSection);
    }

    @Test
    void loadAndInjectAddsAndReplacesSection() {
        TestParams params = createParams();
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());
        FakeDeepAgent deepAgent = new FakeDeepAgent();

        await(manager.loadAndInject(deepAgent));
        PromptSection firstSection = deepAgent.promptBuilder.getSection(TeamMemoryManager.SECTION_NAME);
        await(manager.loadAndInject(deepAgent));
        PromptSection secondSection = deepAgent.promptBuilder.getSection(TeamMemoryManager.SECTION_NAME);

        assertThat(firstSection).isNotNull();
        assertThat(secondSection).isNotNull();
        assertThat(secondSection.getName()).isEqualTo(firstSection.getName());
        assertThat(deepAgent.promptBuilder.sections).hasSize(1);
    }

    @Test
    void loadAndInjectAppendsPersonalAndSharedMemory() {
        TestParams params = createParams();
        params.teamMemoryDir = tempDir.resolve("team-memory").toString();
        FakeIndexManager indexManager = new FakeIndexManager(List.of(new MemorySearchResult("note.md")));
        params.toolkitFactory.toolkit.indexManager = indexManager;
        params.sharedFactory.summary = "shared note";
        Path personalMemory = params.workspace.getNodePath("memory").resolve("note.md").normalize();
        params.fileSystem.files.put(personalMemory.toString(), "remember this");
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());
        FakeDeepAgent deepAgent = new FakeDeepAgent();

        await(manager.loadAndInject(deepAgent, "query"));

        String rendered = deepAgent.promptBuilder.getSection(TeamMemoryManager.SECTION_NAME).render("en");
        assertThat(indexManager.options.maxResults()).isEqualTo(5);
        assertThat(rendered).contains("Your relevant memories");
        assertThat(rendered).contains("remember this");
        assertThat(rendered).contains("Team shared memory");
        assertThat(rendered).contains("shared note");
    }

    @Test
    void extractAfterRoundSkipsNonPersistentLifecycle() {
        TestParams params = createParams();
        params.lifecycle = "temporary";
        params.role = "leader";
        params.enableAutoExtract = true;
        params.teamMemoryDir = tempDir.resolve("team-memory").toString();
        params.database = new FakeDatabase();
        TeamMemoryManager manager = new TeamMemoryManager(params);

        await(manager.extractAfterRound());

        assertThat(params.extractionInvoker.calls).isZero();
    }

    @Test
    void extractAfterRoundSkipsTeammateRole() {
        TestParams params = createParams();
        params.lifecycle = "persistent";
        params.role = "teammate";
        params.enableAutoExtract = true;
        params.teamMemoryDir = tempDir.resolve("team-memory").toString();
        params.database = new FakeDatabase();
        TeamMemoryManager manager = new TeamMemoryManager(params);

        await(manager.extractAfterRound());

        assertThat(params.extractionInvoker.calls).isZero();
    }

    @Test
    void extractAfterRoundSkipsWhenAutoExtractDisabled() {
        TestParams params = createParams();
        params.lifecycle = "persistent";
        params.role = "leader";
        params.enableAutoExtract = false;
        params.teamMemoryDir = tempDir.resolve("team-memory").toString();
        params.database = new FakeDatabase();
        TeamMemoryManager manager = new TeamMemoryManager(params);

        await(manager.extractAfterRound());

        assertThat(params.extractionInvoker.calls).isZero();
    }

    @Test
    void extractAfterRoundLeaderPersistentCallsExtractor() {
        TestParams params = createParams();
        params.lifecycle = "persistent";
        params.role = "leader";
        params.enableAutoExtract = true;
        params.teamMemoryDir = tempDir.resolve("team-memory").toString();
        params.database = new FakeDatabase();
        params.taskManager = new FakeTaskManager();
        params.extractionModel = new FakeModel();
        params.timezoneOffsetHours = 9.0d;
        TeamMemoryManager manager = new TeamMemoryManager(params);

        await(manager.extractAfterRound());

        assertThat(params.extractionInvoker.calls).isEqualTo(1);
        ExtractionInvocation invocation = params.extractionInvoker.invocations.getFirst();
        assertThat(invocation.teamName()).isEqualTo("test_team");
        assertThat(invocation.database()).isSameAs(params.database);
        assertThat(invocation.taskManager()).isSameAs(params.taskManager);
        assertThat(invocation.teamMemoryDir()).isEqualTo(params.teamMemoryDir);
        assertThat(invocation.model()).isSameAs(params.extractionModel);
        assertThat(invocation.timezoneOffsetHours()).isEqualTo(9.0d);
    }

    @Test
    void extractAfterRoundLeaderPersistentRunsEveryRound() {
        TestParams params = createParams();
        params.lifecycle = "persistent";
        params.role = "leader";
        params.enableAutoExtract = true;
        params.teamMemoryDir = tempDir.resolve("team-memory").toString();
        params.database = new FakeDatabase();
        params.taskManager = new FakeTaskManager();
        params.extractionModel = new FakeModel();
        TeamMemoryManager manager = new TeamMemoryManager(params);

        await(manager.extractAfterRound());
        await(manager.extractAfterRound());

        assertThat(params.extractionInvoker.calls).isEqualTo(2);
    }

    @Test
    void closeCleansUpResources() {
        TestParams params = createParams();
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());
        FakeDeepAgent deepAgent = new FakeDeepAgent();
        manager.registerTools(deepAgent);
        await(manager.loadAndInject(deepAgent));

        await(manager.close());

        assertThat(manager.getOwnedToolNames()).isEmpty();
        assertThat(manager.getOwnedToolIds()).isEmpty();
        assertThat(manager.getCachedBaseSection()).isNull();
        assertThat(manager.getToolkit()).isNull();
        assertThat(params.toolkitFactory.toolkit.closed).isTrue();
        assertThat(deepAgent.abilityManager.abilities).isEmpty();
        assertThat(params.resourceManager.tools).isEmpty();
    }

    @Test
    void closeWithoutRegisterToolsClosesToolkit() {
        TestParams params = createParams();
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());

        await(manager.close());

        assertThat(manager.getToolkit()).isNull();
        assertThat(params.toolkitFactory.toolkit.closed).isTrue();
    }

    @Test
    void closeIsIdempotent() {
        TestParams params = createParams();
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());

        await(manager.close());
        await(manager.close());
        await(manager.close());

        assertThat(manager.getToolkit()).isNull();
    }

    @Test
    void closeAfterMultipleOperationsIsIdempotent() {
        TestParams params = createParams();
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());
        FakeDeepAgent deepAgent = new FakeDeepAgent();
        manager.registerTools(deepAgent);

        await(manager.loadAndInject(deepAgent, "test1"));
        await(manager.loadAndInject(deepAgent, "test2"));
        await(manager.close());
        await(manager.close());

        assertThat(manager.getToolkit()).isNull();
        assertThat(manager.getOwnedToolNames()).isEmpty();
        assertThat(deepAgent.abilityManager.abilities).isEmpty();
    }

    @Test
    void readOnlySourceWorkspaceCreatesWorkspace() {
        TestParams params = createParams();
        params.readOnlySourceWorkspace = tempDir.resolve("source").toString();
        TeamMemoryManager manager = new TeamMemoryManager(params);

        assertThat(await(manager.initToolkit())).isTrue();
        assertThat(manager.getWorkspace()).isNotNull();
        assertThat(manager.getWorkspace().getRootPath()).isEqualTo(params.readOnlySourceWorkspace);
        assertThat(params.toolkitFactory.requests.getFirst().readOnly()).isTrue();
    }

    @Test
    void initToolkitReadOnlyToolsExposeReadOnlyOnly() {
        TestParams params = createParams();
        params.toolkitFactory = null;
        params.readOnlySourceWorkspace = tempDir.resolve("source").toString();
        TeamMemoryManager manager = new TeamMemoryManager(params);

        assertThat(await(manager.initToolkit())).isTrue();

        assertThat(manager.getToolkit()).isInstanceOf(MemberMemoryToolkit.class);
        MemberMemoryToolkit toolkit = (MemberMemoryToolkit) manager.getToolkit();
        assertThat(toolkit.isReadOnly()).isTrue();
        assertThat(toolNames(toolkit.getTools()))
                .contains("memory_search")
                .doesNotContain("write_memory", "edit_memory");

        await(manager.close());
    }

    @Test
    void loadAndInjectReadOnlyGeneralBuildsReadOnlyMemorySection() {
        TestParams params = createParams();
        params.readOnlySourceWorkspace = tempDir.resolve("source").toString();
        params.promptMode = "proactive";
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());
        FakeDeepAgent deepAgent = new FakeDeepAgent();

        await(manager.loadAndInject(deepAgent, ""));

        String rendered = deepAgent.promptBuilder.getSection(TeamMemoryManager.SECTION_NAME).render("en");
        assertThat(rendered).contains("Read-Only Mode");
        assertThat(rendered).contains("Writing or modifying memory files is not allowed.");
    }

    @Test
    void loadAndInjectReadOnlyCodingBuildsReadOnlyCodingMemorySection() {
        TestParams params = createParams();
        params.readOnlySourceWorkspace = tempDir.resolve("source").toString();
        params.scenario = "coding";
        TeamMemoryManager manager = new TeamMemoryManager(params);
        await(manager.initToolkit());
        FakeDeepAgent deepAgent = new FakeDeepAgent();

        await(manager.loadAndInject(deepAgent, ""));

        String rendered = deepAgent.promptBuilder.getSection(TeamMemoryManager.SECTION_NAME).render("en");
        assertThat(rendered).contains("# coding memory (read-only)");
        assertThat(rendered).contains("No writing allowed.");
        assertThat(rendered).contains("coding_memory");
    }

    @Test
    void codingScenarioChineseLanguageAndProactiveModeInjectSections() {
        TestParams coding = createParams();
        coding.scenario = "coding";
        TeamMemoryManager codingManager = new TeamMemoryManager(coding);
        await(codingManager.initToolkit());
        FakeDeepAgent codingAgent = new FakeDeepAgent();
        await(codingManager.loadAndInject(codingAgent));
        assertThat(codingAgent.promptBuilder.getSection(TeamMemoryManager.SECTION_NAME)).isNotNull();

        TestParams chinese = createParams();
        chinese.language = "cn";
        TeamMemoryManager chineseManager = new TeamMemoryManager(chinese);
        await(chineseManager.initToolkit());
        FakeDeepAgent chineseAgent = new FakeDeepAgent();
        await(chineseManager.loadAndInject(chineseAgent));
        assertThat(chineseAgent.promptBuilder.getSection(TeamMemoryManager.SECTION_NAME)).isNotNull();

        TestParams proactive = createParams();
        proactive.promptMode = "proactive";
        TeamMemoryManager proactiveManager = new TeamMemoryManager(proactive);
        await(proactiveManager.initToolkit());
        FakeDeepAgent proactiveAgent = new FakeDeepAgent();
        await(proactiveManager.loadAndInject(proactiveAgent));
        assertThat(proactiveAgent.promptBuilder.getSection(TeamMemoryManager.SECTION_NAME)).isNotNull();
    }

    private TestParams createParams() {
        TestParams params = new TestParams();
        params.workspace = new Workspace(tempDir.toString(), "en");
        params.fileSystem = new FakeFileSystem();
        params.toolkitFactory = new FakeToolkitFactory();
        params.sharedFactory = new FakeSharedMemoryManagerFactory();
        params.resourceManager = new FakeResourceManager();
        params.extractionInvoker = new RecordingExtractionInvoker();
        return params;
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static List<String> toolNames(List<ToolView> tools) {
        List<String> names = new ArrayList<>();
        for (ToolView tool : tools) {
            names.add(tool.card().name());
        }
        return names;
    }

    private static final class TestParams implements Parameters {
        private String memberName = "test_member";
        private String teamName = "test_team";
        private String role = "teammate";
        private String lifecycle = "temporary";
        private String scenario = "general";
        private Workspace workspace;
        private FakeFileSystem fileSystem;
        private String teamMemoryDir;
        private String language = "en";
        private String promptMode = "passive";
        private boolean enableAutoExtract;
        private String readOnlySourceWorkspace;
        private TeamMemoryExtractor.TeamDatabaseView database;
        private TeamMemoryExtractor.TeamTaskManagerView taskManager;
        private TeamMemoryExtractor.ModelView extractionModel;
        private double timezoneOffsetHours = 8.0d;
        private FakeToolkitFactory toolkitFactory;
        private FakeSharedMemoryManagerFactory sharedFactory;
        private FakeResourceManager resourceManager;
        private RecordingExtractionInvoker extractionInvoker;

        @Override
        public String memberName() {
            return memberName;
        }

        @Override
        public String teamName() {
            return teamName;
        }

        @Override
        public String role() {
            return role;
        }

        @Override
        public String lifecycle() {
            return lifecycle;
        }

        @Override
        public String scenario() {
            return scenario;
        }

        @Override
        public Workspace workspace() {
            return workspace;
        }

        @Override
        public TeamMemoryExtractor.FileSystemView sysOperation() {
            return fileSystem;
        }

        @Override
        public String teamMemoryDir() {
            return teamMemoryDir;
        }

        @Override
        public String language() {
            return language;
        }

        @Override
        public String promptMode() {
            return promptMode;
        }

        @Override
        public boolean enableAutoExtract() {
            return enableAutoExtract;
        }

        @Override
        public String readOnlySourceWorkspace() {
            return readOnlySourceWorkspace;
        }

        @Override
        public TeamMemoryExtractor.TeamDatabaseView database() {
            return database;
        }

        @Override
        public TeamMemoryExtractor.TeamTaskManagerView taskManager() {
            return taskManager;
        }

        @Override
        public TeamMemoryExtractor.ModelView extractionModel() {
            return extractionModel;
        }

        @Override
        public double timezoneOffsetHours() {
            return timezoneOffsetHours;
        }

        @Override
        public ToolkitFactory toolkitFactory() {
            return toolkitFactory;
        }

        @Override
        public SharedMemoryManagerFactory sharedMemoryManagerFactory() {
            return sharedFactory;
        }

        @Override
        public ResourceManagerView resourceManager() {
            return resourceManager;
        }

        @Override
        public ExtractionInvoker extractionInvoker() {
            return extractionInvoker;
        }
    }

    private static final class FakeToolkitFactory implements ToolkitFactory {
        private final FakeToolkit toolkit = new FakeToolkit();
        private final List<ToolkitRequest> requests = new ArrayList<>();

        @Override
        public MemberMemoryToolkitView create(ToolkitRequest request) {
            requests.add(request);
            return toolkit;
        }
    }

    private static final class FakeToolkit implements MemberMemoryToolkitView {
        private int initializeCalls;
        private boolean closed;
        private MemoryIndexManagerView indexManager;

        @Override
        public CompletionStage<Boolean> initialize() {
            initializeCalls++;
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public List<ToolView> getTools() {
            return List.of(new FakeTool(new ToolCard("tool-1", "memory_search")));
        }

        @Override
        public MemoryIndexManagerView manager() {
            return indexManager;
        }

        @Override
        public CompletionStage<Void> close() {
            closed = true;
            return CompletableFuture.completedFuture(null);
        }
    }

    private record FakeTool(ToolCard card) implements ToolView {
    }

    private static final class FakeDeepAgent implements DeepAgentView {
        private FakePromptBuilder promptBuilder = new FakePromptBuilder();
        private FakeAbilityManager abilityManager = new FakeAbilityManager();
        private int pendingRails;
        private int registeredRails;
        private int staleRails;

        @Override
        public PromptBuilderView getSystemPromptBuilder() {
            return promptBuilder;
        }

        @Override
        public AbilityManagerView getAbilityManager() {
            return abilityManager;
        }

        @Override
        public int stripMemoryRailsByType() {
            int removed = pendingRails + registeredRails;
            staleRails += registeredRails;
            pendingRails = 0;
            return removed;
        }
    }

    private static final class FakePromptBuilder implements PromptBuilderView {
        private final Map<String, PromptSection> sections = new LinkedHashMap<>();

        @Override
        public void removeSection(String name) {
            sections.remove(name);
        }

        @Override
        public void addSection(PromptSection section) {
            sections.put(section.getName(), section);
        }

        private PromptSection getSection(String name) {
            return sections.get(name);
        }
    }

    private static final class FakeAbilityManager implements AbilityManagerView {
        private final Map<String, ToolCard> abilities = new LinkedHashMap<>();
        private int addCalls;

        @Override
        public AddResult add(ToolCard toolCard) {
            addCalls++;
            boolean added = !abilities.containsKey(toolCard.name());
            abilities.put(toolCard.name(), toolCard);
            return new AddResult(added);
        }

        @Override
        public void remove(List<String> names) {
            names.forEach(abilities::remove);
        }
    }

    private static final class FakeResourceManager implements ResourceManagerView {
        private final Map<String, ToolView> tools = new LinkedHashMap<>();

        @Override
        public Optional<ToolView> getTool(String id) {
            return Optional.ofNullable(tools.get(id));
        }

        @Override
        public void addTool(ToolView tool) {
            tools.put(tool.card().id(), tool);
        }

        @Override
        public void removeTool(String id) {
            tools.remove(id);
        }
    }

    private static final class FakeFileSystem implements TeamMemoryExtractor.FileSystemView {
        private final Map<String, String> files = new LinkedHashMap<>();

        @Override
        public CompletionStage<Optional<String>> readFile(String path) {
            return CompletableFuture.completedFuture(Optional.ofNullable(files.get(path)));
        }

        @Override
        public CompletionStage<Boolean> writeFile(String path, String content, boolean createIfNotExist) {
            files.put(path, content);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<List<TeamMemoryExtractor.FileEntry>> listFiles(String path, boolean recursive) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private static final class FakeIndexManager implements MemoryIndexManagerView {
        private final List<MemorySearchResult> results;
        private SearchOptions options;

        private FakeIndexManager(List<MemorySearchResult> results) {
            this.results = results;
        }

        @Override
        public CompletionStage<List<MemorySearchResult>> search(String query, SearchOptions options) {
            this.options = options;
            return CompletableFuture.completedFuture(results);
        }
    }

    private static final class FakeSharedMemoryManagerFactory implements SharedMemoryManagerFactory {
        private String summary;

        @Override
        public SharedMemoryManagerView create(String teamMemoryDir, TeamMemoryExtractor.FileSystemView sysOperation) {
            return new FakeSharedMemoryManager(summary);
        }
    }

    private record FakeSharedMemoryManager(String summary) implements SharedMemoryManagerView {
        @Override
        public CompletionStage<Void> ensureDir() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Optional<String>> readTeamSummary() {
            return CompletableFuture.completedFuture(Optional.ofNullable(summary));
        }
    }

    private static final class RecordingExtractionInvoker implements ExtractionInvoker {
        private int calls;
        private final List<ExtractionInvocation> invocations = new ArrayList<>();

        @Override
        public CompletionStage<Void> extract(ExtractionInvocation invocation) {
            calls++;
            invocations.add(invocation);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeDatabase implements TeamMemoryExtractor.TeamDatabaseView {
        @Override
        public TeamMemoryExtractor.TeamMessageStoreView message() {
            return teamName -> CompletableFuture.completedFuture(List.of());
        }
    }

    private static final class FakeTaskManager implements TeamMemoryExtractor.TeamTaskManagerView {
        @Override
        public CompletionStage<List<com.openjiuwen.agent_teams.tools.TeamTask>> listTasks() {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private static final class FakeModel implements TeamMemoryExtractor.ModelView {
    }
}
