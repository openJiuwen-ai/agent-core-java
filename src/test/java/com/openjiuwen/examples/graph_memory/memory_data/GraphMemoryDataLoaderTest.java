/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.graph_memory.memory_data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphMemoryDataLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void chunkConvSlicesMessagesWithPythonOverlapSemantics() {
        List<Integer> messages = List.of(1, 2, 3, 4, 5, 6, 7);

        assertThat(GraphMemoryDataLoader.chunkConv(messages, 3, 1))
                .containsExactly(List.of(1, 2, 3), List.of(3, 4, 5), List.of(6, 7));
    }

    @Test
    void chunkConvWithoutOverlapUsesConsecutiveChunkStride() {
        List<String> messages = List.of("a", "b", "c", "d", "e");

        assertThat(GraphMemoryDataLoader.chunkConv(messages, 2))
                .containsExactly(List.of("a", "b"), List.of("c", "d"), List.of("e"));
    }

    @Test
    void convertTestDataNormalizesUserMessages() {
        Map<String, Object> raw = Map.of(
                "role", "user",
                "name", "张三",
                "content", "你好",
                "iso_time", "2025-07-19T19:12"
        );

        assertThat(GraphMemoryDataLoader.convertTestData(raw)).containsExactly(
                Map.entry("role", "张三（用户）"),
                Map.entry("content", "你好"),
                Map.entry("iso_time", "2025-07-19T19:12")
        );
    }

    @Test
    void convertTestDataPrefersMappedAgentIdAndRemovesFullNamePrefix() {
        Map<String, Object> raw = Map.of(
                "role", "assistant",
                "agent_id", "小智",
                "agent_name", "小优",
                "content", "技术向导型AI助手   请打开理财页面。",
                "iso_time", "2025-07-19T19:13"
        );

        assertThat(GraphMemoryDataLoader.convertTestData(raw)).containsExactly(
                Map.entry("role", "小智（技术向导型AI助手）"),
                Map.entry("content", "请打开理财页面。"),
                Map.entry("iso_time", "2025-07-19T19:13")
        );
    }

    @Test
    void convertTestDataFallsBackToMappedAgentNameAndRejectsUnknownAgents() {
        Map<String, Object> byName = Map.of(
                "role", "assistant",
                "agent_id", "D",
                "agent_name", "小鑫",
                "content", "规则严谨型AI客服请先核实身份。",
                "iso_time", "2025-07-19T19:14"
        );
        Map<String, Object> unknown = Map.of(
                "role", "assistant",
                "agent_id", "Z",
                "agent_name", "未知",
                "content", "hello",
                "iso_time", "2025-07-19T19:15"
        );

        assertThat(GraphMemoryDataLoader.convertTestData(byName)).containsEntry("role", "小鑫（规则严谨型AI客服）");
        assertThat(GraphMemoryDataLoader.convertTestData(byName)).containsEntry("content", "请先核实身份。");
        assertThatThrownBy(() -> GraphMemoryDataLoader.convertTestData(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知");
    }

    @Test
    void loadTestDataReadsConversationJsonAndConvertsEveryMessage() throws IOException {
        Path file = tempDir.resolve("conversation_0001.json");
        Files.writeString(file, """
                {
                  "conversation": [
                    {
                      "role": "user",
                      "name": "李四",
                      "content": "帮我看看额度",
                      "iso_time": "2025-07-19T19:16",
                      "agent_id": null,
                      "agent_name": null
                    },
                    {
                      "role": "assistant",
                      "agent_id": "A",
                      "agent_name": "小艺",
                      "content": "手机内置个人生活助手   已为您打开额度页。",
                      "iso_time": "2025-07-19T19:17"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        assertThat(GraphMemoryDataLoader.loadTestData(file)).containsExactly(
                Map.of("role", "李四（用户）", "content", "帮我看看额度", "iso_time", "2025-07-19T19:16"),
                Map.of("role", "小艺（手机内置个人生活助手）", "content", "已为您打开额度页。", "iso_time", "2025-07-19T19:17")
        );
    }

    @Test
    void listDataFilesReturnsSortedAbsoluteConversationJsonFiles() throws IOException {
        Path mockData = tempDir.resolve("mock_data");
        Files.createDirectories(mockData);
        Path second = Files.createFile(mockData.resolve("conversation_0002.json"));
        Path first = Files.createFile(mockData.resolve("conversation_0001.json"));
        Files.createFile(mockData.resolve("notes.json"));

        assertThat(GraphMemoryDataLoader.listDataFiles(mockData)).containsExactly(
                first.toAbsolutePath().normalize().toString(),
                second.toAbsolutePath().normalize().toString()
        );
    }
}
