/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's code-operation result surface in
 * {@code openjiuwen/core/sys_operation/result/code_operation_result.py}.
 */
class CodeOperationResultTest {

    @Test
    void executeCodeDataKeepsDefaults() {
        ExecuteCodeData data = ExecuteCodeData.builder()
                .codeContent("print('hi')")
                .language("python")
                .build();

        assertEquals("", data.getStdout());
        assertEquals("", data.getStderr());
    }

    @Test
    void executeCodeChunkDataKeepsMetadataAndChunkFields() {
        ExecuteCodeChunkData data = ExecuteCodeChunkData.builder()
                .chunkIndex(2)
                .type("stdout")
                .metadata(Map.of("lang", "python"))
                .build();

        assertEquals("", data.getText());
        assertEquals("stdout", data.getType());
        assertEquals(2, data.getChunkIndex());
        assertEquals(Map.of("lang", "python"), data.getMetadata());
    }
}
