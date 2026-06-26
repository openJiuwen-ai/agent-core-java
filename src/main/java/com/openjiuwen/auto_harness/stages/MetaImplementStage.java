/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CodeChangeArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.harness.DeepAgent;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Execute code changes for the current task.
 *
 * <p>Mirrors Python's {@code MetaImplementStage} in
 * {@code openjiuwen/auto_harness/stages/implement.py}.</p>
 */
public class MetaImplementStage extends ImplementStage {

    private static final Logger LOGGER = Logger.getLogger(MetaImplementStage.class.getName());

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            throw new IllegalArgumentException("MetaImplementStage requires TaskContext");
        }
        List<Object> events = new ArrayList<>();
        String prompt = buildImplementPrompt(taskContext.getTask(), taskContext.getRuntime().getRelated());
        Map<String, Integer> promptStats = buildPromptDebugStats(prompt);
        String startedAt = Instant.now().toString();
        long startNanos = System.nanoTime();
        LOGGER.info(() -> "Implement LLM call starting: task=" + taskContext.getTask().getTopic()
                + ", started_at=" + startedAt
                + ", prompt_chars=" + promptStats.get("chars")
                + ", prompt_lines=" + promptStats.get("lines")
                + ", prompt_bytes=" + promptStats.get("bytes")
                + ", model_timeout_secs=" + taskContext.getOrchestrator().getConfig().getModelTimeoutSecs());
        events.add(BaseExecutionContext.message("任务准备就绪: " + taskContext.getTask().getTopic()));

        DeepAgent agent = taskContext.getRuntime().getTaskAgent() instanceof DeepAgent deepAgent ? deepAgent : null;
        String implementError = "";
        for (Object chunk : runImplementStream(
                agent,
                taskContext.getTask(),
                taskContext.getRuntime().getRelated(),
                taskContext.getRuntime().getTaskSession(),
                prompt
        )) {
            events.add(chunk);
            implementError = extractControllerTaskFailedError(chunk);
            if (!implementError.isEmpty()) {
                break;
            }
        }
        if (!implementError.isEmpty()) {
            double elapsedSecs = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            String error = "Implement model call failed after %.1fs (started_at=%s, prompt_chars=%d, "
                    .formatted(elapsedSecs, startedAt, promptStats.get("chars"))
                    + "prompt_lines=" + promptStats.get("lines")
                    + ", prompt_bytes=" + promptStats.get("bytes")
                    + ", model_timeout_secs=" + taskContext.getOrchestrator().getConfig().getModelTimeoutSecs()
                    + ").\n" + implementError;
            events.add(failedResult(taskContext, error));
            return events.iterator();
        }

        LOGGER.info(() -> "Implement LLM call finished: task=" + taskContext.getTask().getTopic()
                + ", elapsed_secs=" + ((System.nanoTime() - startNanos) / 1_000_000_000.0)
                + ", prompt_chars=" + promptStats.get("chars")
                + ", prompt_lines=" + promptStats.get("lines")
                + ", prompt_bytes=" + promptStats.get("bytes"));
        List<String> editedFiles = detectEditedFiles(taskContext);
        if (editedFiles.isEmpty()) {
            String error = "Implement phase finished without any code edits. "
                    + "No allowed repo file was changed according to git status/diff.";
            events.add(failedResult(taskContext, error));
            return events.iterator();
        }
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("code_change", CodeChangeArtifact.builder()
                .related(new ArrayList<>(taskContext.getRuntime().getRelated()))
                .editedFiles(editedFiles)
                .build());
        events.add(StageResult.builder().artifacts(artifacts).build());
        return events.iterator();
    }

    private static List<String> detectEditedFiles(TaskContext taskContext) {
        GitOperations git = taskContext.getOrchestrator().getGit();
        try {
            return extractRepoEditCandidates(
                    git.statusPorcelain(),
                    git.diffNameOnly("HEAD"),
                    taskContext.getRuntime().getPreexistingDirtyFiles()
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect implement edits", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while inspecting implement edits", e);
        }
    }

    private static StageResult failedResult(TaskContext taskContext, String error) {
        taskContext.getTask().setStatus(TaskStatus.FAILED);
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("code_change", CodeChangeArtifact.builder()
                .related(new ArrayList<>(taskContext.getRuntime().getRelated()))
                .editedFiles(List.of())
                .build());
        artifacts.put("task_result", CycleResult.builder()
                .success(false)
                .error(error)
                .build());
        return StageResult.builder()
                .status("failed")
                .artifacts(artifacts)
                .messages(List.of(error))
                .error(error)
                .build();
    }
}
