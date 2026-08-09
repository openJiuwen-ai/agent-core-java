/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.sysop.protocal.BaseFsProtocal;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Focused candidate validation for {@link MemoryToolOps}.
 *
 * <p>Mirrors Python's {@code memory_tool_ops} module in
 * {@code openjiuwen/core/memory/lite/memory_tool_ops.py}.</p>
 */
public final class MemoryToolOpsTest {

    private MemoryToolOpsTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("memory-tool-ops-candidate");
        Workspace workspace = new Workspace(root.toString(), "cn");

        assertEquals(root.resolve("USER.md").normalize().toString(),
                MemoryToolOps.validateMemoryPath("USER.md", workspace).value());
        assertEquals(root.resolve("memory").resolve("MEMORY.md").normalize().toString(),
                MemoryToolOps.validateMemoryPath("MEMORY.md", workspace).value());
        assertEquals(root.resolve("memory").resolve("daily_memory").resolve("2026-06-15.md").normalize().toString(),
                MemoryToolOps.validateMemoryPath("2026-06-15.md", workspace).value());
        assertFalse(MemoryToolOps.validateMemoryPath("../secret.md", workspace).valid());

        FakeSysOperation sysOperation = new FakeSysOperation();
        MemoryToolContext context = new MemoryToolContext();
        context.setWorkspace(workspace);
        context.setSysOperation(sysOperation);

        Map<String, Object> read = MemoryToolOps.readMemoryWithContext(context, "MEMORY.md", 2, 1)
                .toCompletableFuture()
                .join();
        assertEquals(true, read.get("success"));
        assertEquals("line2", read.get("content"));
        assertEquals(3, read.get("totalLines"));
        assertEquals(2, read.get("start_line"));
        assertEquals(2, read.get("end_line"));
        assertEquals(true, read.get("truncated"));

        Map<String, Object> write = MemoryToolOps.writeMemoryWithContext(context, "note.md", "hello", false)
                .toCompletableFuture()
                .join();
        assertEquals(true, write.get("success"));
        assertEquals(false, write.get("appended"));
        assertEquals(true, sysOperation.fs().appendUsed);

        Map<String, Object> edit = MemoryToolOps.editMemoryWithContext(context, "note.md", "line2", "changed")
                .toCompletableFuture()
                .join();
        assertEquals(true, edit.get("success"));
        assertEquals("line1\nchanged\nline3hello", sysOperation.fs().content);

        System.out.println("PASS MemoryToolOpsTest");
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("expected false");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static final class FakeSysOperation {
        private final FakeFs fs = new FakeFs();

        public FakeFs fs() {
            return fs;
        }
    }

    public static final class FakeFs {
        private String content = "line1\nline2\nline3";
        private boolean appendUsed;

        public CompletableFuture<FakeReadResult> readFile(
                String path,
                String mode,
                Integer head,
                Integer tail,
                BaseFsProtocal.LineRange lineRange,
                String encoding,
                int chunkSize,
                Map<String, Object> options
        ) {
            return CompletableFuture.completedFuture(new FakeReadResult(new FakeReadData(content)));
        }

        public CompletableFuture<FakeReadResult> readFile(String path) {
            return CompletableFuture.completedFuture(new FakeReadResult(new FakeReadData(content)));
        }

        public CompletableFuture<FakeWriteResult> writeFile(
                String path,
                String newContent,
                String mode,
                boolean prependNewline,
                boolean appendNewline,
                boolean append,
                boolean createIfNotExist,
                String permissions,
                String encoding,
                Map<String, Object> options
        ) {
            appendUsed = append;
            content = append ? content + (prependNewline ? "\n" : "") + newContent : newContent;
            return CompletableFuture.completedFuture(new FakeWriteResult(new FakeWriteData(1)));
        }
    }

    public record FakeReadResult(FakeReadData data) {
    }

    public record FakeReadData(String content) {
    }

    public record FakeWriteResult(FakeWriteData data) {
    }

    public record FakeWriteData(int size) {
    }
}
