/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's shell-operation result surface in
 * {@code openjiuwen/core/sys_operation/result/shell_operation_result.py}.
 */
class ShellOperationResultTest {

    @Test
    void executeCmdDataKeepsPythonDefaults() {
        ExecuteCmdData data = ExecuteCmdData.builder()
                .command("dir")
                .build();

        assertEquals("dir", data.getCommand());
        assertEquals(".", data.getCwd());
        assertEquals("", data.getStdout());
        assertEquals("", data.getStderr());
        assertNull(data.getExitCode());
    }

    @Test
    void executeCmdChunkDataPreservesStreamingFields() {
        ExecuteCmdChunkData data = ExecuteCmdChunkData.builder()
                .type("stderr")
                .chunkIndex(3)
                .exitCode(7)
                .metadata(Map.of("stream", "stderr"))
                .build();

        assertEquals("", data.getText());
        assertEquals("stderr", data.getType());
        assertEquals(3, data.getChunkIndex());
        assertEquals(7, data.getExitCode());
        assertEquals(Map.of("stream", "stderr"), data.getMetadata());
    }

    @Test
    void executeCmdBackgroundDataPreservesDefaultCwd() {
        ExecuteCmdBackgroundData data = ExecuteCmdBackgroundData.builder()
                .command("ping localhost")
                .pid(42)
                .build();

        assertEquals("ping localhost", data.getCommand());
        assertEquals(".", data.getCwd());
        assertEquals(42, data.getPid());
    }

    @Test
    void resultEnvelopesRemainTyped() {
        ExecuteCmdBackgroundData backgroundData = ExecuteCmdBackgroundData.builder()
                .command("start job")
                .pid(99)
                .build();
        ExecuteCmdBackgroundResult backgroundResult = new ExecuteCmdBackgroundResult();
        backgroundResult.setData(backgroundData);

        assertSame(backgroundData, backgroundResult.getData());
    }
}
