/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.WriteFileResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

class WriteFileToolSchemaTest {
    @TempDir
    Path tempDir;

    private LocalFunction writeFile;

    @BeforeEach
    void prepareTool() {
        SysOperationCard card = SysOperationCard.builder().id("write-file-schema").mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempDir.toString()).build()).build();
        SysOperation operation = new SysOperation(card);
        writeFile = SysOperationToolAdapter.extractTools(card, operation).stream()
                .filter(entry -> entry.toolId().equals("write-file-schema.fs.writeFile"))
                .findFirst().orElseThrow().localFunction();
    }

    @Test
    void textContentShouldPassSchemaAndBeWrittenThroughTool() throws Exception {
        Object result = writeFile.invoke(Map.of("path", "script.py", "content", "print('附件分析')",
                "isCreateIfMissing", true), Map.of());

        assertWritten(result, "script.py", "print('附件分析')");
    }

    @Test
    void streamingTextWriteShouldUseTheSameSchema() throws Exception {
        Iterator<Object> result = writeFile.stream(Map.of("path", "stream.txt", "content", "hello",
                "isCreateIfMissing", true), Map.of());

        assertWritten(result.next(), "stream.txt", "hello");
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void legacyStructuredContentShouldRemainSupported() throws Exception {
        Object result = writeFile.invoke(Map.of("path", "legacy.txt", "content", Map.of("text", "legacy"),
                "isCreateIfMissing", true), Map.of());

        assertWritten(result, "legacy.txt", "legacy");
    }

    @Test
    void binaryContentShouldRemainSupportedThroughTool() throws Exception {
        byte[] content = "binary".getBytes(StandardCharsets.UTF_8);
        Object result = writeFile.invoke(Map.of("path", "data.bin", "content", content, "mode", "bytes",
                "isCreateIfMissing", true), Map.of());

        assertThat(result).isInstanceOfSatisfying(WriteFileResult.class,
                output -> assertThat(output.getCode()).isEqualTo(StatusCode.SUCCESS.getCode()));
        assertThat(Files.readAllBytes(tempDir.resolve("data.bin"))).containsExactly(content);
    }

    @Test
    void mapParametersShouldStillRejectStrings() {
        assertThatThrownBy(() -> writeFile.invoke(Map.of("path", "invalid.txt", "content", "hello",
                "options", "invalid", "isCreateIfMissing", true), Map.of()))
                .hasMessageContaining("valid object");
        assertThat(Files.exists(tempDir.resolve("invalid.txt"))).isFalse();
    }

    private void assertWritten(Object result, String fileName, String content) throws IOException {
        assertThat(result).isInstanceOfSatisfying(WriteFileResult.class,
                output -> assertThat(output.getCode()).isEqualTo(StatusCode.SUCCESS.getCode()));
        assertThat(Files.readString(tempDir.resolve(fileName), StandardCharsets.UTF_8)).isEqualTo(content);
    }
}
