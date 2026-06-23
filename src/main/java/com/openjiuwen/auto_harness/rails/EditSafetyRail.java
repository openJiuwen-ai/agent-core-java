/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.infra.EditScope;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * Tracks edited files and blocks writes outside the allowed repository scope.
 *
 * <p>Mirrors Python's {@code EditSafetyRail} in
 * {@code openjiuwen/auto_harness/rails/edit_safety_rail.py}.</p>
 */
public class EditSafetyRail extends AgentRail {
    private static final Logger LOGGER = Logger.getLogger(EditSafetyRail.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> WRITE_TOOLS = Set.of("write_file", "edit_file");

    private final int maxFiles;
    private final RuffChecker ruffChecker;
    private final Set<String> editedFiles = new LinkedHashSet<>();

    public EditSafetyRail() {
        this(3);
    }

    public EditSafetyRail(int maxFiles) {
        this(maxFiles, defaultRuffChecker());
    }

    public EditSafetyRail(int maxFiles, RuffChecker ruffChecker) {
        this.maxFiles = maxFiles;
        this.ruffChecker = ruffChecker == null ? defaultRuffChecker() : ruffChecker;
    }

    @Override
    public CompletionStage<Void> beforeToolCall(AgentCallbackContext context) {
        ToolCallInputs inputs = toolInputs(context);
        if (inputs == null || !WRITE_TOOLS.contains(inputs.getToolName())) {
            return completed();
        }
        String filePath = stringValue(normalizeToolArgs(inputs.getToolArgs()).get("file_path"));
        if (filePath.isBlank() || EditScope.isAllowedRepoEditPath(filePath)) {
            return completed();
        }
        String normalized = EditScope.normalizeRepoPath(filePath);
        LOGGER.warning("Blocked out-of-scope write: " + (normalized.isBlank() ? filePath : normalized));
        rejectTool(context, inputs, "Out-of-scope edit blocked. Only `openjiuwen/harness/**`, "
                + "`openjiuwen/core/**`, `tests/**`, `examples/**`, `docs/en/**`, and `docs/zh/**` "
                + "may be modified. Rejected path: '" + (normalized.isBlank() ? filePath : normalized) + "'.");
        return completed();
    }

    @Override
    public CompletionStage<Void> afterToolCall(AgentCallbackContext context) {
        ToolCallInputs inputs = toolInputs(context);
        if (inputs == null || !WRITE_TOOLS.contains(inputs.getToolName())) {
            return completed();
        }
        String filePath = stringValue(normalizeToolArgs(inputs.getToolArgs()).get("file_path"));
        if (filePath.isBlank()) {
            return completed();
        }
        String normalized = EditScope.normalizeRepoPath(filePath);
        editedFiles.add(normalized.isBlank() ? filePath : normalized);
        if (editedFiles.size() > maxFiles) {
            LOGGER.warning("Atomic change limit exceeded: " + editedFiles.size() + " files");
            context.pushSteering("You have modified " + editedFiles.size()
                    + " files (limit is " + maxFiles + "). Keep changes minimal and focused.");
        }
        if (!filePath.endsWith(".py")) {
            return completed();
        }
        context.getExtra().put("ruff_checked_path", filePath);
        return ruffChecker.check(filePath).thenAccept(result -> {
            if (!result.available() || result.returnCode() == 0 || result.output().isBlank()) {
                return;
            }
            LOGGER.info("ruff check failed for " + filePath);
            context.pushSteering("ruff check found issues in '" + filePath + "':\n"
                    + result.output() + "\nPlease fix these issues.");
        });
    }

    public void reset() {
        editedFiles.clear();
    }

    public Set<String> editedFiles() {
        return new LinkedHashSet<>(editedFiles);
    }

    private static ToolCallInputs toolInputs(AgentCallbackContext context) {
        return context.getInputs() instanceof ToolCallInputs inputs ? inputs : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeToolArgs(Object rawArgs) {
        if (rawArgs instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (rawArgs instanceof String rawString && !rawString.isBlank()) {
            try {
                return OBJECT_MAPPER.readValue(rawString, Map.class);
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static void rejectTool(AgentCallbackContext context, ToolCallInputs inputs, String message) {
        context.getExtra().put("_skip_tool", true);
        inputs.setToolResult(Map.of("error", message));
        inputs.setToolMsg(new ToolMessage(message, toolCallId(inputs), inputs.getToolName()));
    }

    private static String toolCallId(ToolCallInputs inputs) {
        Object toolCall = inputs.getToolCall();
        if (toolCall instanceof Map<?, ?> map && map.get("id") != null) {
            return String.valueOf(map.get("id"));
        }
        return "";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static RuffChecker defaultRuffChecker() {
        return filePath -> CompletableFuture.supplyAsync(() -> {
            ProcessBuilder builder = new ProcessBuilder("python", "-m", "ruff", "check", filePath);
            builder.redirectErrorStream(true);
            try {
                Process process = builder.start();
                byte[] output = process.getInputStream().readAllBytes();
                int returnCode = process.waitFor();
                return new RuffResult(returnCode, new String(output, StandardCharsets.UTF_8), true);
            } catch (IOException exception) {
                LOGGER.fine("ruff not found or unavailable, skipping check: " + exception.getMessage());
                return RuffResult.unavailable();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return RuffResult.unavailable();
            }
        });
    }

    /**
     * Result of running the Python ruff check.
     *
     * <p>Mirrors Python's subprocess return code and stdout handling in
     * {@code openjiuwen/auto_harness/rails/edit_safety_rail.py}.</p>
     */
    public record RuffResult(int returnCode, String output, boolean available) {
        public static RuffResult unavailable() {
            return new RuffResult(0, "", false);
        }
    }

    /**
     * Injectable ruff runner used by tests to mirror Python's patched subprocess.
     */
    @FunctionalInterface
    public interface RuffChecker {
        CompletionStage<RuffResult> check(String filePath);
    }
}
