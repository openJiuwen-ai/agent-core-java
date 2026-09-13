/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.spi.store.BaseKVStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TodoToolFromConfigTest {

    @TempDir
    Path tempDir;

    private TodoStorage extractStorage(TodoTool tool) {
        try {
            Field field = TodoTool.class.getDeclaredField("storage");
            field.setAccessible(true);
            return (TodoStorage) field.get(tool);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testFromConfig_fileType() {
        Map<String, Object> conf = new HashMap<>();
        conf.put("basePath", tempDir.toString());
        TodoTool tool = TodoTool.fromConfig("file", conf);
        assertThat(extractStorage(tool)).isInstanceOf(FileTodoStorage.class);
    }

    @Test
    void testFromConfig_kvType() {
        Map<String, Object> conf = new HashMap<>();
        conf.put("sharedKvStore", new InMemoryKVStore());
        TodoTool tool = TodoTool.fromConfig("kv", conf);
        assertThat(extractStorage(tool)).isInstanceOf(KvTodoStorage.class);
    }

    @Test
    void testFromConfig_unknownType_fallsBackToFile() {
        Map<String, Object> conf = new HashMap<>();
        conf.put("basePath", tempDir.toString());
        TodoTool tool = TodoTool.fromConfig("unknown", conf);
        assertThat(extractStorage(tool)).isInstanceOf(FileTodoStorage.class);
    }

    @Test
    void testLegacyConstructor_fileStorage() {
        TodoTool tool = new TodoTool(tempDir.toString());
        assertThat(extractStorage(tool)).isInstanceOf(FileTodoStorage.class);
    }

    @Test
    void testStorageConstructor_customStorage() {
        TodoStorage customStorage = new KvTodoStorage(new InMemoryKVStore());
        TodoTool tool = new TodoTool(customStorage);
        assertThat(extractStorage(tool)).isSameAs(customStorage);
    }

    @Test
    void testTodoCRUD_viaFileConfig() throws Exception {
        Map<String, Object> conf = new HashMap<>();
        conf.put("basePath", tempDir.toString());
        TodoTool tool = TodoTool.fromConfig("file", conf);
        String sessionId = "crud-file-session";

        ToolOutput createResult = tool.create(sessionId, List.of(
                Map.of("content", "Task A", "activeForm", "Doing A", "description", "Desc A"),
                Map.of("content", "Task B", "activeForm", "Doing B", "description", "Desc B")
        ));
        assertThat(createResult.isSuccess()).isTrue();

        ToolOutput listResult = tool.list(sessionId);
        assertThat(listResult.isSuccess()).isTrue();
        List<?> active = (List<?>) listResult.getData();
        assertThat(active).hasSize(2);
    }

    @Test
    void testTodoCRUD_viaKvConfig() throws Exception {
        BaseKVStore kvStore = new InMemoryKVStore();
        Map<String, Object> conf = new HashMap<>();
        conf.put("sharedKvStore", kvStore);
        TodoTool tool = TodoTool.fromConfig("kv", conf);
        String sessionId = "crud-kv-session";

        ToolOutput createResult = tool.create(sessionId, List.of(
                Map.of("content", "KV Task A", "activeForm", "Doing KV A", "description", "KV Desc A"),
                Map.of("content", "KV Task B", "activeForm", "Doing KV B", "description", "KV Desc B")
        ));
        assertThat(createResult.isSuccess()).isTrue();

        ToolOutput listResult = tool.list(sessionId);
        assertThat(listResult.isSuccess()).isTrue();
        List<?> active = (List<?>) listResult.getData();
        assertThat(active).hasSize(2);
    }
}
