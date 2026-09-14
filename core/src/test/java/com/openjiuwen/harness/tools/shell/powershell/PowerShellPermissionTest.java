/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PowerShellPermissionTest {

    @Test
    void bypassModeAllowsEverything() {
        PermissionConfig config = cfg(PermissionMode.BYPASS, null, null);
        assertThat(PowerShellPermission.checkPermission("Remove-Item -Recurse C:\\temp", config).isAllowed()).isTrue();
    }

    @Test
    void bypassModeIgnoresDenyPatterns() {
        PermissionConfig config = cfg(PermissionMode.BYPASS, List.of("Remove-Item"), null);
        assertThat(PowerShellPermission.checkPermission("Remove-Item foo", config).isAllowed()).isTrue();
    }

    @Test
    void denyPatternsBlockMatchingCommands() {
        PermissionConfig config = cfg(PermissionMode.AUTO, List.of("Remove-Item"), null);
        PermissionResult result = PowerShellPermission.checkPermission("Remove-Item foo", config);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).contains("denied");
    }

    @Test
    void denyPatternsCheckEachPipelineSegment() {
        PermissionConfig config = cfg(PermissionMode.AUTO, List.of("Remove-Item"), null);
        assertThat(PowerShellPermission.checkPermission("Write-Output hi | Remove-Item foo", config).isAllowed()).isFalse();
    }

    @Test
    void denyPatternsAllowCommandsWithoutMatches() {
        PermissionConfig config = cfg(PermissionMode.AUTO, List.of("Remove-Item"), null);
        assertThat(PowerShellPermission.checkPermission("Write-Output hello", config).isAllowed()).isTrue();
    }

    @Test
    void allowPatternsPassMatchingCommands() {
        PermissionConfig config = cfg(PermissionMode.AUTO, null, List.of("^Get-Content"));
        assertThat(PowerShellPermission.checkPermission("Get-Content file.txt", config).isAllowed()).isTrue();
    }

    @Test
    void denyPatternsTakePrecedenceOverAllowPatterns() {
        PermissionConfig config = cfg(PermissionMode.AUTO, List.of("-Force"), List.of("^Get-Content"));
        assertThat(PowerShellPermission.checkPermission("Get-Content file.txt -Force", config).isAllowed()).isFalse();
    }

    @Test
    void readOnlyModeAllowsReadCommands() {
        PermissionConfig config = cfg(PermissionMode.READ_ONLY, null, null);
        assertThat(PowerShellPermission.checkPermission("Get-Content foo.txt | Select-String bar", config).isAllowed()).isTrue();
    }

    @Test
    void readOnlyModeRejectsWriteCommands() {
        PermissionConfig config = cfg(PermissionMode.READ_ONLY, null, null);

        assertThat(PowerShellPermission.checkPermission("Get-Content file.txt", config).isAllowed()).isTrue();
        PermissionResult result = PowerShellPermission.checkPermission("Remove-Item file.txt", config);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).contains("Read-only");
    }

    @Test
    void readOnlyModeAllowsDirectoryListing() {
        PermissionConfig config = cfg(PermissionMode.READ_ONLY, null, null);
        assertThat(PowerShellPermission.checkPermission("Get-ChildItem -Force", config).isAllowed()).isTrue();
    }

    @Test
    void readOnlyModeDeniesGitPush() {
        PermissionConfig config = cfg(PermissionMode.READ_ONLY, null, null);
        assertThat(PowerShellPermission.checkPermission("git push origin main", config).isAllowed()).isFalse();
    }

    @Test
    void readOnlyModeAllowsNeutralPipelineIntoReadCommand() {
        PermissionConfig config = cfg(PermissionMode.READ_ONLY, null, null);
        assertThat(PowerShellPermission.checkPermission("Write-Output hello | Select-String h", config).isAllowed()).isTrue();
    }

    @Test
    void acceptEditsModeAllowsFileOperations() {
        PermissionConfig config = cfg(PermissionMode.ACCEPT_EDITS, null, null);
        assertThat(PowerShellPermission.checkPermission("New-Item -ItemType Directory temp", config).isAllowed()).isTrue();
        assertThat(PowerShellPermission.checkPermission("Copy-Item a.txt b.txt", config).isAllowed()).isTrue();
        assertThat(PowerShellPermission.checkPermission("Set-Content file.txt value", config).isAllowed()).isTrue();
    }

    @Test
    void acceptEditsModeAllowsKnownDevTools() {
        PermissionConfig config = cfg(PermissionMode.ACCEPT_EDITS, null, null);
        assertThat(PowerShellPermission.checkPermission("git commit -m test", config).isAllowed()).isTrue();
        assertThat(PowerShellPermission.checkPermission("python3 -m pytest", config).isAllowed()).isTrue();
        assertThat(PowerShellPermission.checkPermission("mvn test", config).isAllowed()).isTrue();
    }

    @Test
    void acceptEditsModeDeniesUnknownCommands() {
        PermissionConfig config = cfg(PermissionMode.ACCEPT_EDITS, null, null);
        PermissionResult result = PowerShellPermission.checkPermission("Invoke-Thing file.txt", config);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).containsIgnoringCase("unknown command");
    }

    @Test
    void acceptEditsModeDeniesPipelinesWithUnknownCommands() {
        PermissionConfig config = cfg(PermissionMode.ACCEPT_EDITS, null, null);
        assertThat(PowerShellPermission.checkPermission("Get-Content file.txt | Invoke-Thing", config).isAllowed()).isFalse();
    }

    @Test
    void autoModeAllowsAnyCommand() {
        PermissionConfig config = cfg(PermissionMode.AUTO, null, null);
        assertThat(PowerShellPermission.checkPermission("Anything-At-All -Foo", config).isAllowed()).isTrue();
    }

    @Test
    void autoModeStillHonorsDenyPatterns() {
        PermissionConfig config = cfg(PermissionMode.AUTO, List.of("Remove-Item"), null);
        assertThat(PowerShellPermission.checkPermission("Remove-Item -Recurse C:\\temp", config).isAllowed()).isFalse();
    }

    @Test
    void autoModeAllowsEmptyCommands() {
        PermissionConfig config = cfg(PermissionMode.AUTO, null, null);
        assertThat(PowerShellPermission.checkPermission("", config).isAllowed()).isTrue();
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
        List<Pattern> patterns = PermissionConfig.compilePatterns(List.of("foo", "bar"));
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
