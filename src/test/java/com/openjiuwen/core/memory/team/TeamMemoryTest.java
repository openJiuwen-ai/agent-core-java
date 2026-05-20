package com.openjiuwen.core.memory.team;

import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.tools.TeamTaskManager;
import com.openjiuwen.agentteams.tools.database.DatabaseConfig;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class TeamMemoryTest {

    @TempDir
    Path tempDir;

    @Test
    void teamMemoryConfigShouldResolveEmbeddingConfig() {
        TeamMemoryConfig config = TeamMemoryConfig.builder().enabled(true).scenario("coding").build();
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getScenario()).isEqualTo("coding");
        assertThat(TeamMemoryConfig.resolveEmbeddingConfig(config)).isNull();
    }

    @Test
    void sharedMemoryManagerShouldWriteReadAndAppend() throws Exception {
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.resolve("team-memory").toString(), null);
        manager.ensureDir();
        manager.writeTeamSummary("line1");
        manager.appendEntry("line2");

        String content = manager.readTeamSummary();
        assertThat(content).contains("line1").contains("line2");
    }

    @Test
    void memberMemoryToolkitShouldCreateToolsAndOperateOnFiles() throws Exception {
        Workspace workspace = Workspace.builder().rootPath(tempDir.toString()).build();
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("alice", "teamA", workspace, "general", null, null, false);
        assertThat(toolkit.initialize()).isTrue();
        assertThat(toolkit.getTools()).isNotEmpty();

        var writeTool = toolkit.getTools().stream().filter(tool -> "write_memory".equals(tool.getCard().getName())).findFirst().orElseThrow();
        writeTool.invoke(Map.of("path", "MEMORY.md", "content", "prefers tests", "append", false), Map.of());
        var searchTool = toolkit.getTools().stream().filter(tool -> "memory_search".equals(tool.getCard().getName())).findFirst().orElseThrow();
        Object result = searchTool.invoke(Map.of("query", "tests"), Map.of());

        assertThat(String.valueOf(result)).contains("prefers tests");
    }

    @Test
    void memberMemoryToolkitShouldUsePythonCodingToolNames() throws Exception {
        Workspace workspace = Workspace.builder().rootPath(tempDir.toString()).build();
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("alice", "teamA", workspace, "coding", null, null, false);
        assertThat(toolkit.initialize()).isTrue();

        assertThat(toolkit.getToolCards())
                .extracting(card -> card.getName())
                .containsExactlyInAnyOrder("coding_memory_read", "coding_memory_write", "coding_memory_edit")
                .doesNotContain("read_memory", "write_memory", "edit_memory");
        assertThat(toolkit.getToolCards())
                .extracting(card -> card.getId())
                .allMatch(id -> String.valueOf(id).startsWith("coding_memory.teamA.alice."));
    }

    @Test
    void temporaryReadOnlyToolkitShouldExposeOnlyReadTools() throws Exception {
        Workspace source = Workspace.builder().rootPath(tempDir.resolve("source").toString()).build();
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("alice", "teamA", source, "general", null, null, true);

        assertThat(toolkit.initialize()).isTrue();

        assertThat(toolkit.getToolCards())
                .extracting(card -> card.getName())
                .contains("memory_search")
                .doesNotContain("write_memory", "edit_memory");
        toolkit.close();
    }

    @Test
    void teamMemoryManagerReadOnlyWorkspaceRootMatchesSource() throws Exception {
        Path source = tempDir.resolve("source-workspace");
        Files.createDirectories(source);
        TeamMemoryManager manager = new TeamMemoryManager(TeamMemoryManagerParams.builder()
                .memberName("alice")
                .teamName("teamA")
                .role(TeamRole.TEAMMATE)
                .lifecycle(TeamLifecycle.TEMPORARY)
                .scenario(TeamScenario.GENERAL)
                .workspace(null)
                .readOnlySourceWorkspace(source.toString())
                .build());

        assertThat(manager.initToolkit()).isTrue();
        assertThat(manager.getToolkit().isReadOnly()).isTrue();
        assertThat(manager.getToolkit().getMemoryDir()).startsWith(source.toAbsolutePath().normalize());
        manager.close();
    }

    @Test
    void teamMemoryManagerPromptMarksReadOnlySourceMemory() throws Exception {
        Path source = tempDir.resolve("source-workspace");
        Files.createDirectories(source.resolve("memory"));
        Files.writeString(source.resolve("memory").resolve("MEMORY.md"), "source memory");
        TeamMemoryManager manager = new TeamMemoryManager(TeamMemoryManagerParams.builder()
                .memberName("alice")
                .teamName("teamA")
                .role(TeamRole.TEAMMATE)
                .lifecycle(TeamLifecycle.TEMPORARY)
                .scenario(TeamScenario.GENERAL)
                .readOnlySourceWorkspace(source.toString())
                .language(TeamLanguage.EN)
                .promptMode(PromptMode.PROACTIVE)
                .build());

        assertThat(manager.initToolkit()).isTrue();
        String prompt = manager.loadAndInject("");

        assertThat(prompt)
                .contains("# Persistent Storage System (Read-Only Mode)")
                .contains("Writing or modifying memory files is not allowed")
                .contains("source memory");
        manager.close();
    }

    @Test
    void teamMemoryManagerShouldUsePythonMemorySectionPriorityAndCacheBaseSection() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace-cache");
        Files.createDirectories(workspaceRoot.resolve("memory"));
        TeamMemoryManager manager = new TeamMemoryManager(TeamMemoryManagerParams.builder()
                .memberName("alice")
                .teamName("teamA")
                .role(TeamRole.TEAMMATE)
                .lifecycle(TeamLifecycle.TEMPORARY)
                .scenario(TeamScenario.GENERAL)
                .workspace(Workspace.builder().rootPath(workspaceRoot.toString()).build())
                .language(TeamLanguage.EN)
                .promptMode(PromptMode.PASSIVE)
                .build());

        assertThat(manager.initToolkit()).isTrue();
        String first = manager.loadAndInject("first");
        String second = manager.loadAndInject("second");

        assertThat(first).isEqualTo(second);
        assertThat(first)
                .contains("## Persistent Storage System (Passive Mode)")
                .contains("Record only when the user explicitly asks");
        assertThat(manager.getCachedPromptBlock()).isEqualTo(second);
        manager.close();
    }

    @Test
    void codingTeamMemoryManagerShouldUsePythonCodingMemorySection() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace-coding");
        Files.createDirectories(workspaceRoot.resolve("coding_memory"));
        Files.writeString(workspaceRoot.resolve("coding_memory").resolve("MEMORY.md"), "project uses release branch");
        TeamMemoryManager manager = new TeamMemoryManager(TeamMemoryManagerParams.builder()
                .memberName("alice")
                .teamName("teamA")
                .role(TeamRole.TEAMMATE)
                .lifecycle(TeamLifecycle.TEMPORARY)
                .scenario(TeamScenario.CODING)
                .workspace(Workspace.builder().rootPath(workspaceRoot.toString()).build())
                .language(TeamLanguage.EN)
                .build());

        assertThat(manager.initToolkit()).isTrue();
        String prompt = manager.loadAndInject("");

        assertThat(prompt)
                .contains("# coding memory")
                .contains("coding_memory_write")
                .contains("project uses release branch");
        manager.close();
    }

    @Test
    void twoMembersUseDistinctManagersAndDiskPaths() throws Exception {
        Workspace workspaceA = Workspace.builder().rootPath(tempDir.resolve("ws-a").toString()).build();
        Workspace workspaceB = Workspace.builder().rootPath(tempDir.resolve("ws-b").toString()).build();
        MemberMemoryToolkit toolkitA = new MemberMemoryToolkit("alice", "teamA", workspaceA, "general", null, null, false);
        MemberMemoryToolkit toolkitB = new MemberMemoryToolkit("bob", "teamA", workspaceB, "general", null, null, false);

        assertThat(toolkitA.initialize()).isTrue();
        assertThat(toolkitB.initialize()).isTrue();

        Files.createDirectories(toolkitA.getMemoryDir());
        Files.writeString(toolkitA.getMemoryDir().resolve("alice_only.txt"), "only alice");
        assertThat(toolkitB.getMemoryDir().resolve("alice_only.txt")).doesNotExist();
        assertThat(toolkitA.getManager()).isNotSameAs(toolkitB.getManager());
        toolkitA.close();
        toolkitB.close();
    }

    @Test
    void teamMemoryManagerShouldBuildPromptFromPersonalAndSharedMemory() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Files.createDirectories(workspaceRoot.resolve("memory"));
        Files.writeString(workspaceRoot.resolve("memory").resolve("MEMORY.md"), "member knows release flow");

        String sharedDir = tempDir.resolve("team-memory").toString();
        SharedMemoryManager shared = new SharedMemoryManager(sharedDir, null);
        shared.writeTeamSummary("team decided to use branch A");

        TeamMemoryManager manager = new TeamMemoryManager(TeamMemoryManagerParams.builder()
                .memberName("alice")
                .teamName("teamA")
                .role(TeamRole.LEADER)
                .lifecycle(TeamLifecycle.PERSISTENT)
                .scenario(TeamScenario.GENERAL)
                .workspace(Workspace.builder().rootPath(workspaceRoot.toString()).build())
                .teamMemoryDir(sharedDir)
                .build());

        assertThat(manager.initToolkit()).isTrue();
        String prompt = manager.loadAndInject("release");

        assertThat(prompt).contains("member knows release flow").contains("team decided to use branch A");
    }

    @Test
    void extractorShouldBuildContextFromDatabaseRecords() {
        String context = TeamMemoryExtractor.buildExtractionContext(
                List.of(Map.of("title", "Task1", "status", "done", "assignee", "alice", "content", "Finished release work")),
                List.of(Map.of("timestamp", 1000L, "from_member_name", "alice", "to_member_name", "bob", "content", "Use release pipeline", "broadcast", false)),
                8.0
        );

        assertThat(context).contains("Task1").contains("Use release pipeline");
    }

    @Test
    void extractorShouldFormatMessageTimestampUsingSeconds() {
        String context = TeamMemoryExtractor.buildExtractionContext(
                List.of(),
                List.of(Map.of("timestamp", 100.0, "from_member_name", "alice", "to_member_name", "bob", "content", "Use release pipeline", "broadcast", false)),
                8.0
        );

        assertThat(context).contains("[01-01 08:01] alice -> bob: Use release pipeline");
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractorToolsShouldRestrictPathsToMemoryDirBasename() throws Exception {
        SysOperation sysOperation = new SysOperation(SysOperationCard.builder()
                .id("sys-extract")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempDir.toString()).build())
                .build());
        List<Object> tools = TeamMemoryExtractor.createExtractionTools(tempDir.toString(), sysOperation, "teamA");
        Tool read = (Tool) tools.stream().filter(tool -> ((Tool) tool).getCard().getName().equals("read_memory_file")).findFirst().orElseThrow();
        Tool write = (Tool) tools.stream().filter(tool -> ((Tool) tool).getCard().getName().equals("write_memory_file")).findFirst().orElseThrow();

        Object invalidRead = read.invoke(Map.of("path", "../secret.txt"), Map.of());
        Object invalidWrite = write.invoke(Map.of("path", "/tmp/secret.txt", "content", "x"), Map.of());
        Object writeResult = write.invoke(Map.of("path", "nested/TEAM_MEMORY.md", "content", "remember release pipeline"), Map.of());
        Object readResult = read.invoke(Map.of("path", "TEAM_MEMORY.md"), Map.of());

        assertThat((Map<String, Object>) invalidRead).containsEntry("error", "Invalid path");
        assertThat((Map<String, Object>) invalidWrite).containsEntry("error", "Invalid path");
        assertThat((Map<String, Object>) writeResult).containsEntry("success", true);
        assertThat(Files.exists(tempDir.resolve("TEAM_MEMORY.md"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("nested").resolve("TEAM_MEMORY.md"))).isFalse();
        assertThat((Map<String, Object>) readResult).containsEntry("content", "remember release pipeline");
    }

    @Test
    void extractorShouldSkipWhenModelIsMissing() throws Exception {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        TeamTaskManager taskManager = new TeamTaskManager("teamA", "alice", db, mock(Messager.class));
        SysOperation sysOperation = mock(SysOperation.class);

        try (MockedStatic<com.openjiuwen.harness.factory.HarnessFactory> harnessFactory = mockStatic(com.openjiuwen.harness.factory.HarnessFactory.class);
             MockedStatic<com.openjiuwen.core.runner.Runner> runner = mockStatic(com.openjiuwen.core.runner.Runner.class)) {
            TeamMemoryExtractor.extractTeamMemories("teamA", db, taskManager, tempDir.resolve("team-extract").toString(), sysOperation, null, 8.0);

            harnessFactory.verifyNoInteractions();
            runner.verifyNoInteractions();
        }
    }

    @Test
    void extractorShouldNotPropagateTaskManagerErrors() throws Exception {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        TeamTaskManager taskManager = mock(TeamTaskManager.class);
        when(taskManager.list()).thenThrow(new RuntimeException("boom"));
        Model model = mock(Model.class);
        SysOperation sysOperation = mock(SysOperation.class);

        try (MockedStatic<com.openjiuwen.harness.factory.HarnessFactory> harnessFactory = mockStatic(com.openjiuwen.harness.factory.HarnessFactory.class);
             MockedStatic<com.openjiuwen.core.runner.Runner> runner = mockStatic(com.openjiuwen.core.runner.Runner.class)) {
            assertThatCode(() -> TeamMemoryExtractor.extractTeamMemories(
                    "teamA",
                    db,
                    taskManager,
                    tempDir.resolve("team-extract").toString(),
                    sysOperation,
                    model,
                    8.0
            )).doesNotThrowAnyException();

            harnessFactory.verifyNoInteractions();
            runner.verifyNoInteractions();
        }
    }

    @Test
    void extractorShouldSkipWhenTasksAndMessagesAreEmpty() throws Exception {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        TeamTaskManager taskManager = new TeamTaskManager("teamA", "alice", db, mock(Messager.class));
        Model model = mock(Model.class);
        SysOperation sysOperation = mock(SysOperation.class);

        try (MockedStatic<com.openjiuwen.harness.factory.HarnessFactory> harnessFactory = mockStatic(com.openjiuwen.harness.factory.HarnessFactory.class);
             MockedStatic<com.openjiuwen.core.runner.Runner> runner = mockStatic(com.openjiuwen.core.runner.Runner.class)) {
            TeamMemoryExtractor.extractTeamMemories("teamA", db, taskManager, tempDir.resolve("team-extract").toString(), sysOperation, model, 8.0);

            harnessFactory.verifyNoInteractions();
            runner.verifyNoInteractions();
        }
    }

    @Test
    void extractorShouldCreateAgentAndRunWhenDataExists() throws Exception {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        db.task.createTask("task-1", "teamA", "Task1", "Finished release work", "done");
        db.message.createMessage("msg-1", "teamA", "alice", "Use release pipeline", "bob", false, false, 1000L);
        TeamTaskManager taskManager = new TeamTaskManager("teamA", "alice", db, mock(Messager.class));
        Model model = mock(Model.class);
        SysOperation sysOperation = mock(SysOperation.class);

        try (MockedStatic<com.openjiuwen.harness.factory.HarnessFactory> harnessFactory = mockStatic(com.openjiuwen.harness.factory.HarnessFactory.class);
             MockedStatic<com.openjiuwen.core.runner.Runner> runner = mockStatic(com.openjiuwen.core.runner.Runner.class)) {
            com.openjiuwen.harness.deep_agent.DeepAgent agent = mock(com.openjiuwen.harness.deep_agent.DeepAgent.class);
            ArgumentCaptor<com.openjiuwen.harness.schema.config.DeepAgentConfig> configCaptor = ArgumentCaptor.forClass(com.openjiuwen.harness.schema.config.DeepAgentConfig.class);
            harnessFactory.when(() -> com.openjiuwen.harness.factory.HarnessFactory.createDeepAgent(any(), any(), any()))
                    .thenReturn(agent);
            runner.when(() -> com.openjiuwen.core.runner.Runner.runAgent(any(), any(), any(), any()))
                    .thenReturn(null);

            TeamMemoryExtractor.extractTeamMemories("teamA", db, taskManager, tempDir.resolve("team-extract").toString(), sysOperation, model, 8.0);

            harnessFactory.verify(() -> com.openjiuwen.harness.factory.HarnessFactory.createDeepAgent(any(), configCaptor.capture(), any()));
            runner.verify(() -> com.openjiuwen.core.runner.Runner.runAgent(any(), any(), any(), any()));
            assertThat(configCaptor.getValue().isEnableTaskLoop()).isFalse();
            assertThat(configCaptor.getValue().getMaxIterations()).isEqualTo(TeamMemoryExtractor.EXTRACTION_AGENT_MAX_ITERATIONS);
            assertThat(configCaptor.getValue().getSystemPrompt()).contains(TeamMemoryExtractor.EXTRACTION_AGENT_PROMPT);
            assertThat(configCaptor.getValue().getTools()).hasSize(3);
        }
    }
}
