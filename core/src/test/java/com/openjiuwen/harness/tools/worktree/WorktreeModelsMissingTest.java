/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for worktree model values and serialization.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/harness/tools/worktree/test_models.py}.</p>
 */
class WorktreeModelsMissingTest {

    private static final String SOURCE = "tests/unit_tests/harness/tools/worktree/test_models.py";
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @TestFactory
    Collection<DynamicTest> pythonWorktreeModelCases() {
        return List.of(
                caseOf("TestWorktreeLifecyclePolicy::test_enum_values",
                        WorktreeModelsMissingTest::lifecyclePolicyEnumValues),
                caseOf("TestWorktreeLifecyclePolicy::test_all_members",
                        WorktreeModelsMissingTest::lifecyclePolicyAllMembers),
                caseOf("TestWorktreeConfig::test_defaults",
                        WorktreeModelsMissingTest::worktreeConfigDefaults),
                caseOf("TestWorktreeConfig::test_enabled",
                        WorktreeModelsMissingTest::worktreeConfigEnabled),
                caseOf("TestWorktreeConfig::test_with_lifecycle_policy",
                        WorktreeModelsMissingTest::worktreeConfigWithLifecyclePolicy),
                caseOf("TestWorktreeConfig::test_with_sparse_paths",
                        WorktreeModelsMissingTest::worktreeConfigWithSparsePaths),
                caseOf("TestWorktreeConfig::test_with_all_fields",
                        WorktreeModelsMissingTest::worktreeConfigWithAllFields),
                caseOf("TestWorktreeSession::test_minimal",
                        WorktreeModelsMissingTest::worktreeSessionMinimal),
                caseOf("TestWorktreeSession::test_full",
                        WorktreeModelsMissingTest::worktreeSessionFull),
                caseOf("TestWorktreeSession::test_serialization_roundtrip",
                        WorktreeModelsMissingTest::worktreeSessionSerializationRoundtrip),
                caseOf("TestWorktreeSession::test_json_roundtrip",
                        WorktreeModelsMissingTest::worktreeSessionJsonRoundtrip),
                caseOf("TestWorktreeCreateResult::test_defaults",
                        WorktreeModelsMissingTest::worktreeCreateResultDefaults),
                caseOf("TestWorktreeCreateResult::test_full",
                        WorktreeModelsMissingTest::worktreeCreateResultFull),
                caseOf("TestWorktreeChangeSummary::test_defaults",
                        WorktreeModelsMissingTest::worktreeChangeSummaryDefaults),
                caseOf("TestWorktreeChangeSummary::test_with_values",
                        WorktreeModelsMissingTest::worktreeChangeSummaryWithValues)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void lifecyclePolicyEnumValues() {
        assertThat(WorktreeLifecyclePolicy.AUTO.getValue()).isEqualTo("auto");
        assertThat(WorktreeLifecyclePolicy.EPHEMERAL.getValue()).isEqualTo("ephemeral");
        assertThat(WorktreeLifecyclePolicy.DURABLE.getValue()).isEqualTo("durable");
        assertThat(WorktreeLifecyclePolicy.AUTO.toString()).isEqualTo("auto");
    }

    private static void lifecyclePolicyAllMembers() {
        assertThat(Set.of(WorktreeLifecyclePolicy.values())).hasSize(3);
    }

    private static void worktreeConfigDefaults() {
        WorktreeConfig config = new WorktreeConfig();

        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getBaseDir()).isNull();
        assertThat(config.getSparsePaths()).isNull();
        assertThat(config.getSymlinkDirectories()).isNull();
        assertThat(config.getIncludePatterns()).isNull();
        assertThat(config.getCleanupAfterDays()).isEqualTo(30);
        assertThat(config.isAutoCleanupOnShutdown()).isTrue();
        assertThat(config.getLifecyclePolicy()).isEqualTo(WorktreeLifecyclePolicy.AUTO);
    }

    private static void worktreeConfigEnabled() {
        WorktreeConfig config = new WorktreeConfig(
                true,
                null,
                null,
                null,
                null,
                30,
                true,
                WorktreeLifecyclePolicy.AUTO
        );

        assertThat(config.isEnabled()).isTrue();
    }

    private static void worktreeConfigWithLifecyclePolicy() {
        WorktreeConfig config = new WorktreeConfig(
                true,
                null,
                null,
                null,
                null,
                30,
                true,
                WorktreeLifecyclePolicy.DURABLE
        );

        assertThat(config.getLifecyclePolicy()).isEqualTo(WorktreeLifecyclePolicy.DURABLE);
    }

    private static void worktreeConfigWithSparsePaths() {
        WorktreeConfig config = new WorktreeConfig(
                true,
                null,
                List.of("src/", "tests/"),
                null,
                null,
                30,
                true,
                WorktreeLifecyclePolicy.AUTO
        );

        assertThat(config.getSparsePaths()).containsExactly("src/", "tests/");
    }

    private static void worktreeConfigWithAllFields() {
        WorktreeConfig config = new WorktreeConfig(
                true,
                "/tmp/wt",
                List.of("src/"),
                List.of(".venv"),
                List.of(".env.local"),
                7,
                false,
                WorktreeLifecyclePolicy.EPHEMERAL
        );

        assertThat(config.getBaseDir()).isEqualTo("/tmp/wt");
        assertThat(config.isAutoCleanupOnShutdown()).isFalse();
        assertThat(config.getCleanupAfterDays()).isEqualTo(7);
        assertThat(config.getLifecyclePolicy()).isEqualTo(WorktreeLifecyclePolicy.EPHEMERAL);
    }

    private static void worktreeSessionMinimal() {
        WorktreeSession session = new WorktreeSession(
                "/home/user/repo",
                "/home/user/workspace/.worktrees/test",
                "test"
        );

        assertThat(session.getOriginalCwd()).isEqualTo("/home/user/repo");
        assertThat(session.getWorktreeBranch()).isNull();
        assertThat(session.getMemberName()).isNull();
        assertThat(session.isHookBased()).isFalse();
        assertThat(session.getLifecyclePolicy()).isEqualTo(WorktreeLifecyclePolicy.AUTO);
        assertThat(session.getCreationDurationMs()).isNull();
        assertThat(session.isUsedSparsePaths()).isFalse();
    }

    private static void worktreeSessionFull() {
        WorktreeSession session = new WorktreeSession(
                "/repo",
                "/workspace/.worktrees/feat",
                "feat",
                "worktree-feat",
                "main",
                "abc123",
                "m1",
                "t1",
                true,
                WorktreeLifecyclePolicy.DURABLE,
                "persistent",
                42.5,
                true
        );

        assertThat(session.getWorktreeBranch()).isEqualTo("worktree-feat");
        assertThat(session.getOriginalHeadCommit()).isEqualTo("abc123");
        assertThat(session.isHookBased()).isTrue();
        assertThat(session.getCreationDurationMs()).isEqualTo(42.5);
    }

    private static void worktreeSessionSerializationRoundtrip() throws Exception {
        WorktreeSession session = new WorktreeSession(
                "/repo",
                "/workspace/.worktrees/test",
                "test",
                "worktree-test",
                "main",
                null,
                "m1",
                null,
                false,
                WorktreeLifecyclePolicy.AUTO,
                null,
                null,
                false
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> data = MAPPER.readValue(MAPPER.writeValueAsBytes(session), Map.class);
        WorktreeSession restored = MAPPER.convertValue(data, WorktreeSession.class);

        assertThat(restored).isEqualTo(session);
    }

    private static void worktreeSessionJsonRoundtrip() throws Exception {
        WorktreeSession session = new WorktreeSession(
                "/repo",
                "/workspace/.worktrees/x",
                "x"
        );

        String json = MAPPER.writeValueAsString(session);
        WorktreeSession restored = MAPPER.readValue(json, WorktreeSession.class);

        assertThat(restored).isEqualTo(session);
    }

    private static void worktreeCreateResultDefaults() {
        WorktreeCreateResult result = new WorktreeCreateResult("/wt/test");

        assertThat(result.getWorktreePath()).isEqualTo("/wt/test");
        assertThat(result.getWorktreeBranch()).isNull();
        assertThat(result.getHeadCommit()).isNull();
        assertThat(result.getBaseBranch()).isNull();
        assertThat(result.isExisted()).isFalse();
        assertThat(result.isHookBased()).isFalse();
    }

    private static void worktreeCreateResultFull() {
        WorktreeCreateResult result = new WorktreeCreateResult(
                "/wt/test",
                "worktree-test",
                "deadbeef",
                "main",
                true,
                true
        );

        assertThat(result.isExisted()).isTrue();
        assertThat(result.getHeadCommit()).isEqualTo("deadbeef");
    }

    private static void worktreeChangeSummaryDefaults() {
        WorktreeChangeSummary summary = new WorktreeChangeSummary();

        assertThat(summary.getChangedFiles()).isEqualTo(0);
        assertThat(summary.getCommits()).isEqualTo(0);
    }

    private static void worktreeChangeSummaryWithValues() {
        WorktreeChangeSummary summary = new WorktreeChangeSummary(3, 2);

        assertThat(summary.getChangedFiles()).isEqualTo(3);
        assertThat(summary.getCommits()).isEqualTo(2);
    }
}
