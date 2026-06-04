/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.sys_operation.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/sys_operation/sandbox/test_jiuwenbox_code_operation.py}.
 */
class TestJiuwenboxCodeOperation extends AbstractSandboxCodeOperationTest {

    @TempDir
    Path tempDir;

    @Override
    protected SysOperation createSysOp() {
        return newJiuwenboxSysOp();
    }

    @Test
    void testShellExecuteCmdSuccess() {
        var result = createSysOp().shell().executeCmd(
                "printf 'out'; printf 'err' >&2",
                "/tmp",
                300,
                java.util.Map.of("JIUWENBOX_ADAPTER_TEST", "ok"),
                null
        );

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals(0, result.getData().getExitCode());
        assertEquals("out", result.getData().getStdout());
        assertEquals("err", result.getData().getStderr());
    }

    @Test
    void testCodeStreamPythonSuccess() {
        List<ExecuteCodeStreamResult> results = collect(
                createSysOp().code().executeCodeStream("print('stream-ok')", "python", 300, null, null)
        );

        assertFalse(results.isEmpty());
        assertEquals(StatusCode.SUCCESS.getCode(), results.getLast().getCode());
        assertEquals(0, results.getLast().getData().getExitCode());
        String stdout = results.stream()
                .map(item -> item.getData().getText())
                .filter(text -> text != null)
                .reduce("", String::concat);
        assertTrue(stdout.contains("stream-ok"));
    }

    @Test
    void testFsWriteReadListSearchAndTransfer() throws Exception {
        SysOperation sysOp = createSysOp();

        var write = sysOp.fs().writeFile("/tmp/adapter/hello.txt", "hello-adapter", "text", false, false, true, "644", "utf-8", null);
        assertEquals(StatusCode.SUCCESS.getCode(), write.getCode());

        var read = sysOp.fs().readFile("/tmp/adapter/hello.txt", "text", null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), read.getCode());
        assertEquals("hello-adapter", read.getData().getContentAsString());

        sysOp.fs().writeFile("/tmp/adapter/sub/keep.py", "print(1)", "text", false, false, true, "644", "utf-8", null);
        sysOp.fs().writeFile("/tmp/adapter/sub/drop.log", "drop", "text", false, false, true, "644", "utf-8", null);

        var files = sysOp.fs().listFiles("/tmp/adapter", true, null, "name", false, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), files.getCode());
        assertTrue(files.getData().getListItems().stream().map(item -> item.getName()).toList()
                .containsAll(List.of("hello.txt", "keep.py", "drop.log")));

        var dirs = sysOp.fs().listDirectories("/tmp/adapter", false, null, "name", false, null);
        assertEquals(StatusCode.SUCCESS.getCode(), dirs.getCode());
        assertTrue(dirs.getData().getListItems().stream().map(item -> item.getName()).toList().contains("sub"));

        var search = sysOp.fs().searchFiles("/tmp/adapter", "*.py", null);
        assertEquals(StatusCode.SUCCESS.getCode(), search.getCode());
        assertEquals(List.of("keep.py"), search.getData().getMatchingFiles().stream().map(item -> item.getName()).toList());

        Path localUpload = tempDir.resolve("upload.txt");
        Files.writeString(localUpload, "uploaded");
        var upload = sysOp.fs().uploadFile(localUpload.toString(), "/tmp/adapter/upload.txt", true, true, true, 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), upload.getCode());

        Path localDownload = tempDir.resolve("download.txt");
        var download = sysOp.fs().downloadFile("/tmp/adapter/upload.txt", localDownload.toString(), true, true, true, 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), download.getCode());
        assertEquals("uploaded", Files.readString(localDownload));
    }
}
