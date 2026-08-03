/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.rails.EditSafetyRail;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CommitArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CommitFacts;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.VerifyReportArtifact;
import com.openjiuwen.auto_harness.stages.CommitStage;
import com.openjiuwen.harness.DeepAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code CommitStage} and module helpers in
 * {@code openjiuwen/auto_harness/stages/commit.py}.</p>
 */
class TestCommitStage {

    @TempDir
    private Path tempDir;

    @Test
    void missingAgentFailsAfterRetryAndUsesDirtyFallback() {
        FakeGitOperations git = new FakeGitOperations();
        git.dirtyFiles = List.of(
                "openjiuwen/core/foo.py",
                "tests/unit_tests/test_foo.py",
                "README.md"
        );
        git.trackedModifiedFiles = List.of("openjiuwen/core/foo.py", "README.md");
        git.untrackedFiles = List.of("tests/unit_tests/test_foo.py");
        git.diffStat = " openjiuwen/core/foo.py | 2 +";
        AutoHarnessOrchestrator orchestrator = orchestrator(git);
        OptimizationTask task = OptimizationTask.builder()
                .topic("lint-fix")
                .description("Fix lint")
                .files(List.of("openjiuwen/core/foo.py"))
                .status(TaskStatus.RUNNING)
                .build();
        TaskRuntime runtime = new TaskRuntime();
        runtime.setPreexistingDirtyFiles(List.of("README.md"));
        TaskContext ctx = new TaskContext(orchestrator, task, runtime);
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder()
                .ciResult(Map.of("errors", "tests/unit_tests/test_foo.py failed"))
                .build());

        List<Object> events = toList(new CommitStage().stream(ctx));

        assertThat(events).hasSize(1);
        StageResult result = (StageResult) events.get(0);
        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getError()).contains("No agent available for commit phase.");
        assertThat(result.getMessages()).anySatisfy(message ->
                assertThat(message).contains("首次提交未成功"));
        CommitArtifact artifact = (CommitArtifact) result.getArtifacts().get("commit_result");
        assertThat(artifact.isCommitted()).isFalse();
        assertThat(artifact.getFacts().getEditedFiles())
                .containsExactly("openjiuwen/core/foo.py", "tests/unit_tests/test_foo.py");
        assertThat(artifact.getFacts().getAllowedFiles())
                .containsExactly("openjiuwen/core/foo.py", "tests/unit_tests/test_foo.py");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        List<Experience> experiences = orchestrator.getExperienceStore().listRecent().join();
        assertThat(experiences).hasSize(1);
        assertThat(experiences.get(0).getSummary()).isEqualTo("commit failed");
    }

    @Test
    void agentCommitSuccessYieldsAgentChunksAndCommitArtifact() {
        FakeGitOperations git = new FakeGitOperations();
        git.dirtyFiles = List.of("openjiuwen/core/foo.py");
        git.trackedModifiedFiles = List.of("openjiuwen/core/foo.py");
        git.diffStat = " openjiuwen/core/foo.py | 4 ++--";
        git.heads = new ArrayList<>(List.of("before", "after"));
        git.statusText = "";
        git.lastCommitStat = "commit abc123\n openjiuwen/core/foo.py | 4 ++--";
        AutoHarnessOrchestrator orchestrator = orchestrator(git);
        OptimizationTask task = OptimizationTask.builder()
                .topic("lint-fix")
                .description("Fix lint")
                .files(List.of("openjiuwen/core/foo.py"))
                .status(TaskStatus.RUNNING)
                .build();
        ScriptedAgent agent = new ScriptedAgent();
        TaskRuntime runtime = new TaskRuntime();
        runtime.setEditSafetyRail(new StaticEditSafetyRail(Set.of("openjiuwen/core/foo.py")));
        runtime.setCommitAgent(agent);
        TaskContext ctx = new TaskContext(orchestrator, task, runtime);
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder().build());

        List<Object> events = toList(new CommitStage().stream(ctx));

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isEqualTo(Map.of("type", "commit_chunk"));
        StageResult result = (StageResult) events.get(1);
        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getMessages()).containsExactly("检查提交范围", "提交变更");
        CommitArtifact artifact = (CommitArtifact) result.getArtifacts().get("commit_result");
        assertThat(artifact.isCommitted()).isTrue();
        assertThat(artifact.getStatusText()).isEmpty();
        assertThat(artifact.getLastCommitStat()).contains("commit abc123");
        assertThat(agent.lastQuery).contains("任务: lint-fix");
        assertThat(agent.lastQuery).contains("允许提交文件: openjiuwen/core/foo.py");
        assertThat(agent.lastQuery).doesNotContain("[4/5]");
    }

    @Test
    void derivesAllowedFilesForDeclaredDerivedLegacyAndNoDeclaredModes() {
        CommitFacts facts = CommitFacts.builder()
                .taskDeclaredFiles(List.of("openjiuwen/core/foo.py", "docs/zh/guide.md", "docs/readme.md"))
                .editedFiles(List.of(
                        "openjiuwen/core/foo.py",
                        "tests/unit_tests/test_foo.py",
                        "tests/unit_tests/auto_harness/test_schema.py",
                        "docs/zh/guide.md",
                        "docs/readme.md",
                        "README.md"
                ))
                .derivedTestFiles(List.of("tests/unit_tests/test_foo.py"))
                .legacyRelatedTestFiles(List.of("tests/unit_tests/auto_harness/test_schema.py"))
                .build();

        assertThat(CommitStage.deriveAllowedFiles(facts)).containsExactly(
                "docs/zh/guide.md",
                "openjiuwen/core/foo.py",
                "tests/unit_tests/auto_harness/test_schema.py",
                "tests/unit_tests/test_foo.py"
        );

        facts.setTaskDeclaredFiles(List.of());
        assertThat(CommitStage.deriveAllowedFiles(facts)).containsExactly(
                "docs/zh/guide.md",
                "openjiuwen/core/foo.py",
                "tests/unit_tests/auto_harness/test_schema.py",
                "tests/unit_tests/test_foo.py"
        );
    }

    private AutoHarnessOrchestrator orchestrator(FakeGitOperations git) {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.toString())
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        orchestrator.setGit(git);
        orchestrator.setExperienceStore(new ExperienceStore(tempDir.resolve("experience")));
        return orchestrator;
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static final class FakeGitOperations extends GitOperations {
        private List<String> dirtyFiles = List.of();
        private List<String> trackedModifiedFiles = List.of();
        private List<String> untrackedFiles = List.of();
        private String diffStat = "";
        private List<String> heads = new ArrayList<>(List.of("same", "same"));
        private String statusText = "";
        private String lastCommitStat = "";

        private FakeGitOperations() {
            super("");
        }

        @Override
        public Map<String, List<String>> collectStatus() {
            Map<String, List<String>> status = new LinkedHashMap<>();
            status.put("dirty_files", new ArrayList<>(dirtyFiles));
            status.put("tracked_modified_files", new ArrayList<>(trackedModifiedFiles));
            status.put("untracked_files", new ArrayList<>(untrackedFiles));
            status.put("renamed_files", new ArrayList<>());
            return status;
        }

        @Override
        public String currentBranch() {
            return "feature";
        }

        @Override
        public String currentHead() {
            if (heads.isEmpty()) {
                return "same";
            }
            return heads.remove(0);
        }

        @Override
        public String diffStat(List<String> paths) {
            return diffStat;
        }

        @Override
        public String statusPorcelain() {
            return statusText;
        }

        @Override
        public String showLastCommitStat() {
            return lastCommitStat;
        }
    }

    private static final class StaticEditSafetyRail extends EditSafetyRail {
        private final Set<String> editedFiles;

        private StaticEditSafetyRail(Set<String> editedFiles) {
            this.editedFiles = new LinkedHashSet<>(editedFiles);
        }

        @Override
        public Set<String> editedFiles() {
            return new LinkedHashSet<>(editedFiles);
        }
    }

    private static final class ScriptedAgent extends DeepAgent {
        private String lastQuery = "";

        @Override
        public Iterator<Map<String, Object>> stream(Map<String, Object> inputs) {
            lastQuery = String.valueOf(inputs.get("query"));
            return List.of(Map.of("type", (Object) "commit_chunk")).iterator();
        }
    }
}
