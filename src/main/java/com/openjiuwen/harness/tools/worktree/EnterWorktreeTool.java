/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Creates or enters an isolated git worktree session.
 *
 * <p>Mirrors Python's {@code EnterWorktreeTool} in
 * {@code openjiuwen/harness/tools/worktree/tools.py}.</p>
 */
public class EnterWorktreeTool extends AbstractHarnessTool {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> ADJECTIVES = List.of("swift", "bright", "calm", "keen", "bold");
    private static final List<String> NOUNS = List.of("fox", "owl", "elm", "oak", "ray");

    private final WorktreeManager manager;

    public EnterWorktreeTool(WorktreeManager manager) {
        super(toolCard("enter_worktree", "worktree.enter", "Create or enter an isolated git worktree."));
        this.manager = manager;
    }

    public EnterWorktreeTool(WorktreeManager manager, String language, String agentId) {
        super(toolCard(
                scopedToolId("enter_worktree", agentId),
                "enter_worktree",
                "Create or enter an isolated git worktree."));
        this.manager = manager;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        WorktreeSession existing = WorktreeSessionContext.getCurrentSession();
        if (existing != null) {
            return ToolOutput.failure("Already in worktree '" + existing.getWorktreeName()
                    + "'. Exit first with exit_worktree.");
        }

        String slug;
        boolean existed;
        try {
            slug = resolveSlug(inputs);
            SlugUtils.validateSlug(slug);
            existed = slugExists(slug);
        } catch (RuntimeException exception) {
            return ToolOutput.failure(exception.getMessage());
        }

        String ownerId = resolveOwner(kwargs, "owner_id", "member_name");
        String tag = resolveOwner(kwargs, "tag", "team_name");
        try {
            WorktreeSession session = manager.enter(slug, ownerId, tag).join();
            Cwd.setCwd(session.getWorktreePath());
            Cwd.setOriginalCwd(session.getWorktreePath());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("worktree_path", session.getWorktreePath());
            data.put("worktree_branch", session.getWorktreeBranch());
            if (existed || session.isExisted()) {
                data.put("message", "Entered existing worktree at " + session.getWorktreePath()
                        + " on branch " + session.getWorktreeBranch() + ". CWD switched to worktree.");
                data.put("existed", true);
            } else {
                data.put("message", "Created worktree at " + session.getWorktreePath()
                        + " on branch " + session.getWorktreeBranch() + ". CWD switched to worktree.");
            }
            return ToolOutput.success(data);
        } catch (RuntimeException exception) {
            return ToolOutput.failure("Failed to create worktree: " + rootMessage(exception));
        }
    }

    private static String resolveSlug(Map<String, Object> inputs) {
        Object requested = inputs == null ? null : inputs.get("name");
        if (requested != null) {
            String selected = String.valueOf(requested).trim();
            if (selected.isEmpty()) {
                throw new IllegalArgumentException("'name' must not be empty");
            }
            return selected;
        }
        String existing = WorktreeSessionContext.getDefaultWorktreeName();
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String generated = generateRandomSlug();
        WorktreeSessionContext.setDefaultWorktreeName(generated);
        return generated;
    }

    static String generateRandomSlug() {
        return ADJECTIVES.get(RANDOM.nextInt(ADJECTIVES.size())) + "-"
                + NOUNS.get(RANDOM.nextInt(NOUNS.size())) + "-"
                + String.format(Locale.ROOT, "%04x", RANDOM.nextInt(0x10000));
    }

    private static String resolveOwner(Map<String, Object> kwargs, String primary, String legacy) {
        if (kwargs == null) {
            return null;
        }
        Object value = kwargs.get(primary);
        if (value == null || String.valueOf(value).isEmpty()) {
            value = kwargs.get(legacy);
        }
        return value == null ? null : String.valueOf(value);
    }

    private boolean slugExists(String slug) {
        try {
            String targetPath = WorktreeManager.resolveTargetPath(slug);
            return Boolean.TRUE.equals(manager.getBackend().exists(targetPath).join());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String scopedToolId(String baseId, String agentId) {
        return agentId == null || agentId.isBlank() ? baseId : baseId + "-" + agentId;
    }
}
