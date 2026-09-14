/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorktreeModelsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void lifecyclePolicyRoundTripsAsStringEnum() throws Exception {
        assertEquals("auto", WorktreeLifecyclePolicy.AUTO.getValue());
        assertEquals(Set.of(
                WorktreeLifecyclePolicy.AUTO,
                WorktreeLifecyclePolicy.EPHEMERAL,
                WorktreeLifecyclePolicy.DURABLE
        ), Set.of(WorktreeLifecyclePolicy.values()));
        assertEquals("\"durable\"", MAPPER.writeValueAsString(WorktreeLifecyclePolicy.DURABLE));
        assertEquals(
                WorktreeLifecyclePolicy.EPHEMERAL,
                MAPPER.readValue("\"ephemeral\"", WorktreeLifecyclePolicy.class)
        );
    }

    @Test
    void worktreeConfigKeepsDefaultsAndExplicitValues() throws Exception {
        WorktreeConfig defaults = new WorktreeConfig();
        assertFalse(defaults.isEnabled());
        assertNull(defaults.getBaseDir());
        assertNull(defaults.getSparsePaths());
        assertNull(defaults.getSymlinkDirectories());
        assertNull(defaults.getIncludePatterns());
        assertEquals(30, defaults.getCleanupAfterDays());
        assertTrue(defaults.isAutoCleanupOnShutdown());
        assertEquals(WorktreeLifecyclePolicy.AUTO, defaults.getLifecyclePolicy());

        WorktreeConfig config = MAPPER.readValue(
                """
                {
                  "enabled": true,
                  "base_dir": "/tmp/wt",
                  "sparse_paths": ["src/"],
                  "symlink_directories": [".venv"],
                  "include_patterns": [".env.local"],
                  "cleanup_after_days": 7,
                  "auto_cleanup_on_shutdown": false,
                  "lifecycle_policy": "ephemeral"
                }
                """,
                WorktreeConfig.class
        );
        assertTrue(config.isEnabled());
        assertEquals("/tmp/wt", config.getBaseDir());
        assertEquals(List.of("src/"), config.getSparsePaths());
        assertEquals(List.of(".venv"), config.getSymlinkDirectories());
        assertEquals(List.of(".env.local"), config.getIncludePatterns());
        assertEquals(7, config.getCleanupAfterDays());
        assertFalse(config.isAutoCleanupOnShutdown());
        assertEquals(WorktreeLifecyclePolicy.EPHEMERAL, config.getLifecyclePolicy());
    }

    @Test
    void worktreeSessionRoundTripsWithSnakeCasePayload() throws Exception {
        WorktreeSession session = new WorktreeSession(
                "/repo",
                "/workspace/.worktrees/feat",
                "feat",
                "worktree-feat",
                "main",
                "abc123",
                "member-1",
                "team-1",
                true,
                WorktreeLifecyclePolicy.DURABLE,
                "persistent",
                42.5,
                true
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = MAPPER.readValue(MAPPER.writeValueAsBytes(session), Map.class);
        assertEquals("/repo", payload.get("original_cwd"));
        assertEquals("/workspace/.worktrees/feat", payload.get("worktree_path"));
        assertEquals("worktree-feat", payload.get("worktree_branch"));
        assertEquals("member-1", payload.get("member_name"));
        assertEquals("durable", payload.get("lifecycle_policy"));
        assertEquals(Boolean.TRUE, payload.get("used_sparse_paths"));

        WorktreeSession restored = MAPPER.readValue(MAPPER.writeValueAsBytes(session), WorktreeSession.class);
        assertEquals(session, restored);
    }

    @Test
    void worktreeCreateResultDefaultsAndFieldsMirrorPythonModel() {
        WorktreeCreateResult defaults = new WorktreeCreateResult("/wt/test");
        assertEquals("/wt/test", defaults.getWorktreePath());
        assertNull(defaults.getWorktreeBranch());
        assertNull(defaults.getHeadCommit());
        assertNull(defaults.getBaseBranch());
        assertFalse(defaults.isExisted());
        assertFalse(defaults.isHookBased());

        WorktreeCreateResult result = new WorktreeCreateResult(
                "/wt/test",
                "worktree-test",
                "deadbeef",
                "main",
                true,
                true
        );
        assertTrue(result.isExisted());
        assertTrue(result.isHookBased());
        assertEquals("deadbeef", result.getHeadCommit());
    }

    @Test
    void worktreeChangeSummaryDefaultsAndValuesMirrorPythonModel() {
        WorktreeChangeSummary defaults = new WorktreeChangeSummary();
        assertEquals(0, defaults.getChangedFiles());
        assertEquals(0, defaults.getCommits());

        WorktreeChangeSummary summary = new WorktreeChangeSummary(3, 2);
        assertEquals(3, summary.getChangedFiles());
        assertEquals(2, summary.getCommits());
    }
}
