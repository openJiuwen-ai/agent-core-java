/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.memory.TeamMemoryExtractor.AgentHandle;
import com.openjiuwen.agent_teams.memory.TeamMemoryExtractor.ExtractionRequest;
import com.openjiuwen.agent_teams.memory.TeamMemoryExtractor.ExtractionTool;
import com.openjiuwen.agent_teams.memory.TeamMemoryExtractor.FileEntry;
import com.openjiuwen.agent_teams.memory.TeamMemoryExtractor.FileSystemView;
import com.openjiuwen.agent_teams.memory.TeamMemoryExtractor.ModelView;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.agent_teams.tools.TeamTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link TeamMemoryExtractor}.
 *
 * <p>Mirrors Python's {@code test_extractor.py} for
 * {@code openjiuwen/agent_teams/memory/extractor.py}.</p>
 *
 * <p>Mirrors Python's supplemental coverage in
 * {@code tests/unit_tests/core/memory/team/test_extractor.py}.</p>
 */
class TeamMemoryExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void buildExtractionContextIncludesTasksAndSortedMessages() {
        TeamTask task = new TeamTask("t1", "team", "Fix bug", "body", "done", "", 1L);
        TeamMessage later = new TeamMessage("m2", "team", "bob", null, "broadcast", 3_600_000L, true, false);
        TeamMessage earlier = new TeamMessage("m1", "team", "alice", "bob", "please review", 60_000L, false, false);

        String context = TeamMemoryExtractor.buildExtractionContext(
                List.of(task),
                List.of(later, earlier),
                8.0d
        );

        assertThat(context).contains("# 本轮团队协作记录");
        assertThat(context).contains("### Fix bug [done] -> 未分配");
        assertThat(context).contains("body");
        assertThat(context).contains("[01-01 08:01] alice -> bob: please review");
        assertThat(context).contains("[01-01 09:00] bob -> 全体: broadcast");
        assertThat(context.indexOf("please review")).isLessThan(context.indexOf("broadcast"));
    }

    @Test
    void createExtractionToolsUsesTeamScopedIds() {
        List<ExtractionTool> tools = TeamMemoryExtractor.createExtractionTools(
                tempDir.toString(),
                new FakeFileSystem(),
                "team1"
        );

        assertThat(tools)
                .extracting(ExtractionTool::id)
                .containsExactly("extract.team1.read", "extract.team1.write", "extract.team1.list");
        assertThat(tools)
                .extracting(ExtractionTool::name)
                .containsExactly("read_memory_file", "write_memory_file", "list_memory_files");
    }

    @Test
    void memoryFileToolsRestrictPathsAndUseBasename() {
        FakeFileSystem fileSystem = new FakeFileSystem();

        assertThat(TeamMemoryExtractor.readMemoryFile(tempDir.toString(), fileSystem, "../secret")
                .toCompletableFuture().join().error()).isEqualTo("Invalid path");

        TeamMemoryExtractor.writeMemoryFile(tempDir.toString(), fileSystem, "nested/TEAM_MEMORY.md", "content")
                .toCompletableFuture().join();
        assertThat(fileSystem.writes)
                .containsExactly(new FakeWrite(tempDir.resolve("TEAM_MEMORY.md").toString(), "content", true));

        fileSystem.files = List.of(new FileEntry("TEAM_MEMORY.md", false), new FileEntry("subdir", true));
        assertThat(TeamMemoryExtractor.listMemoryFiles(tempDir.toString(), fileSystem)
                .toCompletableFuture().join().files()).containsExactly("TEAM_MEMORY.md");
    }

    @Test
    void extractTeamMemoriesSkipsWhenModelNone() {
        FakeAgentFactory factory = new FakeAgentFactory();
        ExtractionRequest request = baseRequest(null, factory, new FakeRunner());

        TeamMemoryExtractor.extractTeamMemories(request).toCompletableFuture().join();

        assertThat(factory.calls).isZero();
    }

    @Test
    void extractTeamMemoriesDoesNotPropagateTaskManagerErrors() {
        FakeAgentFactory factory = new FakeAgentFactory();
        FakeTaskManager taskManager = new FakeTaskManager();
        taskManager.failure = new RuntimeException("boom");
        ExtractionRequest request = new ExtractionRequest(
                "team1",
                new FakeDatabase(List.of()),
                taskManager,
                tempDir.toString(),
                new FakeFileSystem(),
                new FakeModel(),
                8.0d,
                factory,
                new FakeRunner()
        );

        TeamMemoryExtractor.extractTeamMemories(request).toCompletableFuture().join();

        assertThat(factory.calls).isZero();
    }

    @Test
    void extractTeamMemoriesSuccessPathCallsAgentAndRunner() {
        FakeAgentFactory factory = new FakeAgentFactory();
        FakeRunner runner = new FakeRunner();
        TeamTask task = new TeamTask("t1", "team1", "task", "body", "done", "u", 1L);
        TeamMessage message = new TeamMessage("m1", "team1", "alice", "bob", "please review", 100L, false, false);
        ExtractionRequest request = new ExtractionRequest(
                "team1",
                new FakeDatabase(List.of(message)),
                new FakeTaskManager(List.of(task)),
                tempDir.toString(),
                new FakeFileSystem(),
                new FakeModel(),
                8.0d,
                factory,
                runner
        );

        TeamMemoryExtractor.extractTeamMemories(request).toCompletableFuture().join();

        assertThat(factory.calls).isEqualTo(1);
        assertThat(factory.systemPrompt).contains("团队记忆提取 agent");
        assertThat(factory.tools).hasSize(3);
        assertThat(factory.maxIterations).isEqualTo(TeamMemoryExtractor.EXTRACTION_AGENT_MAX_ITERATIONS);
        assertThat(factory.enableTaskLoop).isFalse();
        assertThat(runner.calls).isEqualTo(1);
        assertThat(runner.query).contains("请分析以下团队 team1 的协作记录并提取记忆");
        assertThat(runner.query).contains("please review");
        assertThat(Files.exists(tempDir)).isTrue();
    }

    private ExtractionRequest baseRequest(ModelView model, FakeAgentFactory factory, FakeRunner runner) {
        return new ExtractionRequest(
                "team1",
                new FakeDatabase(List.of()),
                new FakeTaskManager(List.of(new TeamTask("t1", "team1", "t", "", "open", "", 1L))),
                tempDir.toString(),
                new FakeFileSystem(),
                model,
                8.0d,
                factory,
                runner
        );
    }

    private record FakeWrite(String path, String content, boolean createIfNotExist) {
    }

    private static final class FakeFileSystem implements FileSystemView {
        private final List<FakeWrite> writes = new ArrayList<>();
        private List<FileEntry> files = List.of();

        @Override
        public CompletionStage<Optional<String>> readFile(String path) {
            return CompletableFuture.completedFuture(Optional.of("existing"));
        }

        @Override
        public CompletionStage<Boolean> writeFile(String path, String content, boolean createIfNotExist) {
            writes.add(new FakeWrite(path, content, createIfNotExist));
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<List<FileEntry>> listFiles(String path, boolean recursive) {
            return CompletableFuture.completedFuture(files);
        }
    }

    private static final class FakeDatabase implements TeamMemoryExtractor.TeamDatabaseView {
        private final List<TeamMessage> messages;

        private FakeDatabase(List<TeamMessage> messages) {
            this.messages = messages;
        }

        @Override
        public TeamMemoryExtractor.TeamMessageStoreView message() {
            return teamName -> CompletableFuture.completedFuture(messages);
        }
    }

    private static final class FakeTaskManager implements TeamMemoryExtractor.TeamTaskManagerView {
        private final List<TeamTask> tasks;
        private RuntimeException failure;

        private FakeTaskManager() {
            this(List.of());
        }

        private FakeTaskManager(List<TeamTask> tasks) {
            this.tasks = tasks;
        }

        @Override
        public CompletionStage<List<TeamTask>> listTasks() {
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            return CompletableFuture.completedFuture(tasks);
        }
    }

    private static final class FakeModel implements ModelView {
    }

    private static final class FakeAgent implements AgentHandle {
    }

    private static final class FakeAgentFactory implements TeamMemoryExtractor.AgentFactory {
        private int calls;
        private String systemPrompt;
        private List<ExtractionTool> tools;
        private int maxIterations;
        private boolean enableTaskLoop;

        @Override
        public AgentHandle createAgent(
                ModelView model,
                String systemPrompt,
                List<ExtractionTool> tools,
                int maxIterations,
                boolean enableTaskLoop
        ) {
            calls++;
            this.systemPrompt = systemPrompt;
            this.tools = tools;
            this.maxIterations = maxIterations;
            this.enableTaskLoop = enableTaskLoop;
            return new FakeAgent();
        }
    }

    private static final class FakeRunner implements TeamMemoryExtractor.RunnerView {
        private int calls;
        private String query;

        @Override
        public CompletionStage<Void> runAgent(AgentHandle agent, String query) {
            calls++;
            this.query = query;
            return CompletableFuture.completedFuture(null);
        }
    }
}
