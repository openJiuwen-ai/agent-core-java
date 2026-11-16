/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.local.LocalFsOperation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

class BashOutputTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearCwd() {
        Cwd.clear();
    }

    @Test
    void truncateOutputTreatsZeroAsUnlimited() {
        assertEquals("abcdef", BashOutput.truncateOutput("abcdef", 0));
    }

    @Test
    void persistLargeOutputReturnsStablePathAndByteCount() {
        BashOutput.PersistResult result = BashOutput.persistLargeOutput("hello", "stderr");

        assertTrue(result.path().contains("openjiuwen_bash_outputs"));
        assertEquals("hello\n--- stderr ---\nstderr".getBytes().length, result.byteCount());
    }

    @Test
    void renderToolContentForErrorsPutsWarningAndExitCodeFirst() {
        BashOutput.CommandOutput output = new BashOutput.CommandOutput(
                "stdout line",
                "stderr line",
                7,
                "warning text",
                100
        );

        BashOutput.RenderedContent rendered = BashOutput.renderToolContent(output, true);

        assertTrue(rendered.isError());
        assertEquals("warning text\nExit code 7\nstderr line\nstdout line", rendered.content());
    }

    @Test
    void renderToolContentPersistsOversizedSuccessfulOutput() {
        String stdout = "\n\n" + "a".repeat(2500);
        BashOutput.CommandOutput output = new BashOutput.CommandOutput(stdout, "", 0, null, 100);

        BashOutput.RenderedContent rendered = BashOutput.renderToolContent(output, false);

        assertFalse(rendered.isError());
        assertTrue(rendered.content().startsWith("<persisted-output>\nOutput too large"));
        assertTrue(rendered.content().contains("Preview (first 2KB):"));
        assertTrue(rendered.content().contains("</persisted-output>"));
    }

    @Test
    void restrictedOperationPersistsOutputInsideSandbox() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalWorkConfig config = LocalWorkConfig.builder()
                .restrictToSandbox(true)
                .sandboxRoot(List.of(tempDir.toString()))
                .workDir(tempDir.toString())
                .build();
        SysOperation operation = new SysOperation(SysOperationCard.builder()
                .id("bash-output-test")
                .name("bash-output-test")
                .mode(OperationMode.LOCAL)
                .workConfig(config)
                .build());
        String expected = "x".repeat(5000);

        BashOutput.RenderedContent rendered = BashOutput.renderToolContent(
                new BashOutput.CommandOutput(expected, "", 0, null, 1000), false, operation);
        String path = persistedPath(rendered.content());
        LocalFsOperation fs = new LocalFsOperation("fs", OperationMode.LOCAL, "local fs", config);

        var result = fs.readFile(path, BaseFsOperation.FileMode.TEXT, null, null, null, "utf-8",
                BaseFsOperation.DEFAULT_READ_CHUNK_SIZE, null).get(5, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getContent()).isEqualTo(expected);
        assertThat(Path.of(path)).startsWith(tempDir);
    }

    @Test
    void blankSandboxRootUsesDefaultWorkspace() {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalWorkConfig config = LocalWorkConfig.builder()
                .restrictToSandbox(true)
                .sandboxRoot(List.of("  "))
                .workDir(tempDir.toString())
                .build();
        SysOperation operation = new SysOperation(SysOperationCard.builder()
                .id("bash-output-blank-root-test")
                .name("bash-output-blank-root-test")
                .mode(OperationMode.LOCAL)
                .workConfig(config)
                .build());

        BashOutput.RenderedContent rendered = BashOutput.renderToolContent(
                new BashOutput.CommandOutput("x".repeat(5000), "", 0, null, 1000), false, operation);

        assertThat(rendered.content()).startsWith("<persisted-output>");
        assertThat(Path.of(persistedPath(rendered.content()))).startsWith(tempDir);
    }

    @Test
    void restrictedOperationFallsBackToTruncatedOutputWhenArtifactRootIsUnavailable() {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        Cwd.setTenantRoot(tempDir.getParent().resolve("another-tenant").toString());
        LocalWorkConfig config = LocalWorkConfig.builder()
                .restrictToSandbox(true)
                .sandboxRoot(List.of(tempDir.toString()))
                .workDir(tempDir.toString())
                .build();
        SysOperation operation = new SysOperation(SysOperationCard.builder()
                .id("bash-output-fallback-test")
                .name("bash-output-fallback-test")
                .mode(OperationMode.LOCAL)
                .workConfig(config)
                .build());

        BashOutput.RenderedContent rendered = BashOutput.renderToolContent(
                new BashOutput.CommandOutput("x".repeat(5000), "", 0, null, 1000), false, operation);

        assertThat(rendered.isError()).isFalse();
        assertThat(rendered.content()).doesNotContain("<persisted-output>");
        assertThat(rendered.content()).contains("lines omitted");
    }

    private static String persistedPath(String content) {
        String marker = "Full output saved to: ";
        int start = content.indexOf(marker) + marker.length();
        return content.substring(start, content.indexOf('\n', start)).trim();
    }

    @Test
    void renderPartialOnFailureReturnsFailureHeaderWhenOutputExists() {
        BashOutput.CommandOutput output = new BashOutput.CommandOutput("stdout", "", 124, null, 100);

        String rendered = BashOutput.renderPartialOnFailure(output, "timeout");

        assertNotNull(rendered);
        assertEquals("timeout\nExit code 124\nstdout", rendered);
    }
}
