/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Edit-safety rail — atomic change tracking + ruff check.
 *
 * <p>Mirrors Python's {@code EditSafetyRail} in {@code openjiuwen.auto_harness.rails.edit_safety_rail}.</p>
 */
public class EditSafetyRail extends DeepAgentRail {

    private static final Logger logger = Logger.getLogger(EditSafetyRail.class.getName());
    private static final Set<String> WRITE_TOOLS = Set.of("write_file", "edit_file");

    private final int maxFiles;
    private final RuffRunner ruffRunner;
    private final Set<String> editedFiles = new HashSet<>();

    public EditSafetyRail() {
        this(3);
    }

    public EditSafetyRail(int maxFiles) {
        this(maxFiles, EditSafetyRail::runRuffProcess);
    }

    public EditSafetyRail(int maxFiles, RuffRunner ruffRunner) {
        this.maxFiles = maxFiles;
        this.ruffRunner = ruffRunner;
    }

    /**
     * Hard-block writes outside the allowed repo scope.
     *
     * @param ctx the agent callback context
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        beforeToolCall((Object) ctx);
    }

    public void beforeToolCall(Object ctx) {
        ToolCallInputs inputs = toolInputs(ctx);
        if (inputs == null || !WRITE_TOOLS.contains(inputs.getToolName())) {
            return;
        }
        String filePath = filePath(inputs);
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        String normalized = normalizeRepoPath(filePath);
        if (isAllowedRepoEditPath(normalized)) {
            return;
        }

        logger.warning("Blocked out-of-scope write: " + (normalized.isBlank() ? filePath : normalized));
        rejectTool(ctx, inputs,
                "Out-of-scope edit blocked. Only `openjiuwen/harness/**`, `openjiuwen/core/**`, "
                        + "`tests/**`, `examples/**`, `docs/en/**`, and `docs/zh/**` may be modified. "
                        + "Rejected path: '" + (normalized.isBlank() ? filePath : normalized) + "'.");
    }

    /**
     * Record edit, check file count, run ruff.
     *
     * @param ctx the agent callback context
     */
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        afterToolCall((Object) ctx);
    }

    public void afterToolCall(Object ctx) {
        ToolCallInputs inputs = toolInputs(ctx);
        if (inputs == null || !WRITE_TOOLS.contains(inputs.getToolName())) {
            return;
        }
        String filePath = filePath(inputs);
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        String normalized = normalizeRepoPath(filePath);
        editedFiles.add(normalized.isBlank() ? filePath : normalized);
        if (editedFiles.size() > maxFiles) {
            logger.warning(String.format("Atomic change limit exceeded: %d files (max %d)",
                    editedFiles.size(), maxFiles));
            pushSteering(ctx, "You have modified " + editedFiles.size() + " files (limit is "
                    + maxFiles + "). Keep changes minimal and focused.");
        }

        if (filePath.endsWith(".py")) {
            runRuff(ctx, filePath);
        }
    }

    /**
     * Reset the edited files tracking.
     */
    public void reset() {
        editedFiles.clear();
    }

    /**
     * Get the edited files.
     *
     * @return the set of edited file paths
     */
    public Set<String> getEditedFiles() {
        return new HashSet<>(editedFiles);
    }

    /**
     * Return tracked edited files in stable order.
     *
     * @return sorted edited file paths
     */
    public List<String> editedFiles() {
        return editedFiles.stream().sorted().toList();
    }

    /**
     * Get the maximum files limit.
     *
     * @return the max files
     */
    public int getMaxFiles() {
        return maxFiles;
    }

    private static ToolCallInputs toolInputs(Object ctx) {
        if (ctx instanceof AgentCallbackContext callbackContext
                && callbackContext.getInputs() instanceof ToolCallInputs inputs) {
            return inputs;
        }
        if (ctx instanceof ToolCallInputs inputs) {
            return inputs;
        }
        return null;
    }

    private static String filePath(ToolCallInputs inputs) {
        Object args = inputs.getToolArgs();
        if (args instanceof Map<?, ?> map) {
            Object value = map.get("file_path");
            if (value == null) {
                value = map.get("path");
            }
            return value == null ? "" : String.valueOf(value);
        }
        return "";
    }

    private static String normalizeRepoPath(String filePath) {
        if (filePath == null) {
            return "";
        }
        String normalized = filePath.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        int openjiuwen = normalized.indexOf("openjiuwen/");
        if (openjiuwen > 0) {
            normalized = normalized.substring(openjiuwen);
        }
        int tests = normalized.indexOf("tests/");
        if (tests > 0) {
            normalized = normalized.substring(tests);
        }
        return normalized;
    }

    private static boolean isAllowedRepoEditPath(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return false;
        }
        return normalizedPath.startsWith("openjiuwen/harness/")
                || normalizedPath.startsWith("openjiuwen/core/")
                || normalizedPath.startsWith("tests/")
                || normalizedPath.startsWith("examples/")
                || normalizedPath.startsWith("docs/en/")
                || normalizedPath.startsWith("docs/zh/");
    }

    private static void rejectTool(Object ctx, ToolCallInputs inputs, String message) {
        if (ctx instanceof AgentCallbackContext callbackContext) {
            callbackContext.getExtra().put("_skip_tool", true);
        }
        inputs.setToolResult(Map.of("error", message));
        String toolCallId = inputs.getToolCall() != null ? inputs.getToolCall().getId() : "";
        inputs.setToolMsg(new ToolMessage(message, toolCallId));
    }

    @SuppressWarnings("unchecked")
    private static void pushSteering(Object ctx, String message) {
        if (!(ctx instanceof AgentCallbackContext callbackContext)) {
            return;
        }
        Object steering = callbackContext.getExtra().get("steering");
        List<String> messages;
        if (steering instanceof List<?> existing) {
            messages = (List<String>) existing;
        } else {
            messages = new ArrayList<>();
            callbackContext.getExtra().put("steering", messages);
        }
        messages.add(message);
    }

    private void runRuff(Object ctx, String filePath) {
        try {
            RuffResult result = ruffRunner.run(filePath);
            if (result.returnCode() != 0 && !result.output().isBlank()) {
                pushSteering(ctx, "ruff check found issues in '" + filePath + "':\n"
                        + result.output() + "\nPlease fix these issues.");
            }
        } catch (IOException e) {
            logger.fine("ruff not found, skipping check");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static RuffResult runRuffProcess(String filePath) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("ruff", "check", filePath)
                .redirectErrorStream(true)
                .start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = String.join(System.lineSeparator(), reader.lines().toList());
        }
        return new RuffResult(process.waitFor(), output);
    }

    @FunctionalInterface
    public interface RuffRunner {
        RuffResult run(String filePath) throws IOException, InterruptedException;
    }

    public record RuffResult(int returnCode, String output) {
    }
}
