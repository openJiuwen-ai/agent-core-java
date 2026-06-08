/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BashPermissionTest {

    @Test
    void bypassModeAllowsEverything() {
        PermissionConfig config = cfg(PermissionMode.BYPASS, null, null);
        assertThat(BashPermission.checkPermission("rm -rf /", config).isAllowed()).isTrue();
    }

    @Test
    void bypassModeIgnoresDenyPatterns() {
        PermissionConfig config = cfg(PermissionMode.BYPASS, List.of("rm"), null);
        assertThat(BashPermission.checkPermission("rm foo", config).isAllowed()).isTrue();
    }

    @Test
    void denyPatternsBlockMatchingCommands() {
        PermissionConfig config = cfg(PermissionMode.AUTO, List.of("\\bsudo\\b"), null);
        PermissionResult result = BashPermission.checkPermission("sudo apt install foo", config);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).contains("denied");
    }

    @Test
    void denyPatternsCheckEachPipelineSegment() {
        PermissionConfig config = cfg(PermissionMode.AUTO, List.of("\\bsudo\\b"), null);
        assertThat(BashPermission.checkPermission("echo hi | sudo tee file", config).isAllowed()).isFalse();
    }

    @Test
    void denyPatternsAllowCommandsWithoutMatches() {
        PermissionConfig config = cfg(PermissionMode.AUTO, List.of("\\bsudo\\b"), null);
        assertThat(BashPermission.checkPermission("echo hello", config).isAllowed()).isTrue();
    }

    @Test
    void allowPatternsPassMatchingCommands() {
        PermissionConfig config = cfg(PermissionMode.AUTO, null, List.of("^git\\s"));
        assertThat(BashPermission.checkPermission("git status", config).isAllowed()).isTrue();
    }

    @Test
    void denyPatternsTakePrecedenceOverAllowPatterns() {
        PermissionConfig config = cfg(PermissionMode.AUTO, List.of("--force"), List.of("^git\\s"));
        assertThat(BashPermission.checkPermission("git push --force", config).isAllowed()).isFalse();
    }

    @Test
    void readOnlyModeAllowsReadCommands() {
        PermissionConfig config = cfg(PermissionMode.READ_ONLY, null, null);
        assertThat(BashPermission.checkPermission("cat foo.txt | grep bar", config).isAllowed()).isTrue();
    }

    @Test
    void readOnlyModeRejectsWriteCommands() {
        PermissionConfig config = cfg(PermissionMode.READ_ONLY, null, null);

        assertThat(BashPermission.checkPermission("cat file.txt", config).isAllowed()).isTrue();
        PermissionResult result = BashPermission.checkPermission("rm file.txt", config);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).contains("Read-only");
    }

    @Test
    void readOnlyModeAllowsLs() {
        PermissionConfig config = cfg(PermissionMode.READ_ONLY, null, null);
        assertThat(BashPermission.checkPermission("ls -la", config).isAllowed()).isTrue();
    }

    @Test
    void readOnlyModeDeniesGitPush() {
        PermissionConfig config = cfg(PermissionMode.READ_ONLY, null, null);
        assertThat(BashPermission.checkPermission("git push origin main", config).isAllowed()).isFalse();
    }

    @Test
    void readOnlyModeAllowsNeutralPipelineIntoReadCommand() {
        PermissionConfig config = cfg(PermissionMode.READ_ONLY, null, null);
        assertThat(BashPermission.checkPermission("echo hello | grep h", config).isAllowed()).isTrue();
    }

    @Test
    void acceptEditsModeAllowsFileOperations() {
        PermissionConfig config = cfg(PermissionMode.ACCEPT_EDITS, null, null);
        assertThat(BashPermission.checkPermission("mkdir -p /tmp/foo", config).isAllowed()).isTrue();
        assertThat(BashPermission.checkPermission("cp a.txt b.txt", config).isAllowed()).isTrue();
        assertThat(BashPermission.checkPermission("sed -i 's/old/new/' file", config).isAllowed()).isTrue();
    }

    @Test
    void acceptEditsModeAllowsKnownDevTools() {
        PermissionConfig config = cfg(PermissionMode.ACCEPT_EDITS, null, null);
        assertThat(BashPermission.checkPermission("git commit -m test", config).isAllowed()).isTrue();
        assertThat(BashPermission.checkPermission("python3 -m pytest", config).isAllowed()).isTrue();
        assertThat(BashPermission.checkPermission("make test", config).isAllowed()).isTrue();
    }

    @Test
    void acceptEditsModeDeniesUnknownCommands() {
        PermissionConfig config = cfg(PermissionMode.ACCEPT_EDITS, null, null);
        PermissionResult result = BashPermission.checkPermission("my_custom_script --dangerous", config);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).containsIgnoringCase("unknown command");
    }

    @Test
    void acceptEditsModeDeniesPipelinesWithUnknownCommands() {
        PermissionConfig config = cfg(PermissionMode.ACCEPT_EDITS, null, null);
        assertThat(BashPermission.checkPermission("cat file | evil_binary", config).isAllowed()).isFalse();
    }

    @Test
    void autoModeAllowsAnyCommand() {
        PermissionConfig config = cfg(PermissionMode.AUTO, null, null);
        assertThat(BashPermission.checkPermission("anything_at_all --foo", config).isAllowed()).isTrue();
    }

    @Test
    void autoModeStillHonorsDenyPatterns() {
        PermissionConfig config = cfg(PermissionMode.AUTO, List.of("\\bsudo\\b"), null);
        assertThat(BashPermission.checkPermission("sudo rm -rf /", config).isAllowed()).isFalse();
    }

    @Test
    void autoModeAllowsEmptyCommands() {
        PermissionConfig config = cfg(PermissionMode.AUTO, null, null);
        assertThat(BashPermission.checkPermission("", config).isAllowed()).isTrue();
    }

    @Test
    void compilePatternsReturnsEmptyForNull() {
        assertThat(PermissionConfig.compilePatterns(null)).isEmpty();
    }

    @Test
    void compilePatternsReturnsEmptyForEmptyLists() {
        assertThat(PermissionConfig.compilePatterns(List.of())).isEmpty();
    }

    @Test
    void compilePatternsCompilesCaseInsensitiveRegexes() {
        List<Pattern> patterns = PermissionConfig.compilePatterns(List.of("\\bfoo\\b", "bar"));
        assertThat(patterns).hasSize(2);
        assertThat(patterns.get(0).matcher("FOO").find()).isTrue();
        assertThat(patterns.get(1).matcher("bar").find()).isTrue();
    }

    private static PermissionConfig cfg(PermissionMode mode, List<String> deny, List<String> allow) {
        PermissionConfig config = new PermissionConfig();
        config.setMode(mode);
        config.setDenyPatterns(PermissionConfig.compilePatterns(deny));
        config.setAllowPatterns(PermissionConfig.compilePatterns(allow));
        return config;
    }
}
