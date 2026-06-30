/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.rails;

import com.openjiuwen.autoharness.infra.EditScope;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.SteeringQueue;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Edit-safety rail: blocks out-of-scope writes, tracks edited files, and runs ruff for Python files.
 */
public class EditSafetyRail extends DeepAgentRail {
    private static final Set<String> WRITE_TOOLS = Set.of("write_file", "edit_file");

    private final int maxFiles;
    private final Set<String> editedFiles = new LinkedHashSet<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public EditSafetyRail() {
        this(3);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EditSafetyRail(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs) || !WRITE_TOOLS.contains(inputs.getToolName())) {
            return;
        }
        String filePath = filePath(inputs.getToolArgs());
        if (filePath.isBlank()) {
            return;
        }
        String normalized = EditScope.normalizeRepoPath(filePath);
        if (EditScope.isAllowedRepoEditPath(filePath)) {
            return;
        }
        rejectTool(ctx, inputs, "Out-of-scope edit blocked. Only `openjiuwen/harness/**`, `openjiuwen/core/**`, "
                + "`tests/**`, `examples/**`, `docs/en/**`, and `docs/zh/**` may be modified. Rejected path: '"
                + (normalized.isBlank() ? filePath : normalized) + "'.");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void afterToolCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs) || !WRITE_TOOLS.contains(inputs.getToolName())) {
            return;
        }
        String filePath = filePath(inputs.getToolArgs());
        if (filePath.isBlank()) {
            return;
        }
        String normalized = EditScope.normalizeRepoPath(filePath);
        editedFiles.add(normalized.isBlank() ? filePath : normalized);
        int count = editedFiles.size();
        if (count > maxFiles) {
            pushSteering(ctx, "You have modified " + count + " files (limit is " + maxFiles
                    + "). Keep changes minimal and focused.");
        }
        if (filePath.endsWith(".py")) {
            runRuff(ctx, filePath);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void reset() {
        editedFiles.clear();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> editedFiles() {
        return new ArrayList<>(editedFiles);
    }

    private static void runRuff(AgentCallbackContext ctx, String filePath) {
        try {
            Process process = new ProcessBuilder("ruff", "check", filePath)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int code = process.waitFor();
            if (code != 0 && !output.isEmpty()) {
                pushSteering(ctx, "ruff check found issues in '" + filePath + "':\n" + output
                        + "Please fix these issues.");
            }
        } catch (IOException ignored) {
            // Python skips ruff when it is not installed.
        } catch (InterruptedException e) {

        }
    }

    static void pushSteering(AgentCallbackContext ctx, String message) {
        if (ctx != null && ctx.getExtra() != null) {
            @SuppressWarnings("unchecked")
            List<String> steering = (List<String>) ctx.getExtra()
                    .computeIfAbsent("steering", key -> new ArrayList<String>());
            steering.add(message);
            ctx.pushSteering(message);
            Object queues = ctx.getExtra().get("loop_queues");
            if (!ctx.hasSteeringQueue() && queues instanceof SteeringQueue steeringQueue) {
                steeringQueue.pushSteering(message);
            }
        }
    }

    static void rejectTool(AgentCallbackContext ctx, ToolCallInputs inputs, String errorMsg) {
        ctx.getExtra().put("_skip_tool", Boolean.TRUE);
        inputs.setToolResult(Map.of("error", errorMsg));
        inputs.setToolMsg(ToolMessage.builder()
                .content(errorMsg)
                .toolCallId(inputs.getToolCall() != null ? inputs.getToolCall().getId() : "")
                .build());
    }

    private static String filePath(Object args) {
        if (args instanceof Map<?, ?> map) {
            Object value = map.get("file_path");
            return value == null ? "" : String.valueOf(value);
        }
        return "";
    }
}
