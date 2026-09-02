package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.memory.support.TestInMemoryKVStore;
import com.openjiuwen.spi.store.BaseKVStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TodoStorageFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void testCreate_fileType() {
        Map<String, Object> conf = new HashMap<>();
        conf.put("basePath", tempDir.toString());
        TodoStorage storage = TodoStorageFactory.create("file", conf);
        assertThat(storage).isInstanceOf(FileTodoStorage.class);
    }

    @Test
    void testCreate_fileType_canSaveAndLoad() throws IOException {
        Map<String, Object> conf = new HashMap<>();
        conf.put("basePath", tempDir.toString());
        TodoStorage storage = TodoStorageFactory.create("file", conf);
        storage.save("factory-session", List.of(TodoItem.create("Factory Task")));
        List<TodoItem> loaded = storage.load("factory-session");
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("Factory Task");
    }

    @Test
    void testCreate_fileType_withNullConf() {
        TodoStorage storage = TodoStorageFactory.create("file", null);
        assertThat(storage).isInstanceOf(FileTodoStorage.class);
    }

    @Test
    void testCreate_kvType() {
        Map<String, Object> conf = new HashMap<>();
        conf.put("kvStoreType", "in_memory");
        TodoStorage storage = TodoStorageFactory.create("kv", conf);
        assertThat(storage).isInstanceOf(KvTodoStorage.class);
    }

    @Test
    void testCreate_kvType_canSaveAndLoad() throws IOException {
        Map<String, Object> conf = new HashMap<>();
        conf.put("kvStoreType", "in_memory");
        TodoStorage storage = TodoStorageFactory.create("kv", conf);
        storage.save("kv-factory-session", List.of(TodoItem.create("KV Factory Task")));
        List<TodoItem> loaded = storage.load("kv-factory-session");
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("KV Factory Task");
    }

    @Test
    void testHasProvider_file() {
        assertThat(TodoStorageFactory.hasProvider("file")).isTrue();
    }

    @Test
    void testHasProvider_kv() {
        assertThat(TodoStorageFactory.hasProvider("kv")).isTrue();
    }

    @Test
    void testHasProvider_unknown() {
        assertThat(TodoStorageFactory.hasProvider("unknown")).isFalse();
    }

    @Test
    void testHasProvider_null() {
        assertThat(TodoStorageFactory.hasProvider(null)).isFalse();
    }

    @Test
    void testCreate_unknownThrows() {
        assertThatThrownBy(() -> TodoStorageFactory.create("unknown", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown");
    }

    @Test
    void testRegister_customProvider() {
        String customType = "custom_todo_test";
        TodoStorageProvider customProvider = new TodoStorageProvider() {
            @Override
            public String typeName() {
                return customType;
            }

            @Override
            public TodoStorage create(Map<String, Object> conf) {
                String basePath = conf != null ? (String) conf.get("basePath") : null;
                return new FileTodoStorage(Path.of(basePath != null ? basePath : "."));
            }
        };
        TodoStorageFactory.register(customType, customProvider);
        assertThat(TodoStorageFactory.hasProvider(customType)).isTrue();
        Map<String, Object> conf = new HashMap<>();
        conf.put("basePath", tempDir.toString());
        TodoStorage storage = TodoStorageFactory.create(customType, conf);
        assertThat(storage).isInstanceOf(FileTodoStorage.class);
    }

    @Test
    void testRegister_overwriteExistingProvider() {
        String customType = "overwrite_todo_test";
        TodoStorageProvider fileProvider = new TodoStorageProvider() {
            @Override
            public String typeName() { return customType; }
            @Override
            public TodoStorage create(Map<String, Object> conf) {
                return new FileTodoStorage(tempDir);
            }
        };
        TodoStorageProvider kvProvider = new TodoStorageProvider() {
            @Override
            public String typeName() { return customType; }
            @Override
            public TodoStorage create(Map<String, Object> conf) {
                return new KvTodoStorage(new TestInMemoryKVStore());
            }
        };
        TodoStorageFactory.register(customType, fileProvider);
        assertThat(TodoStorageFactory.create(customType, Map.of())).isInstanceOf(FileTodoStorage.class);
        TodoStorageFactory.register(customType, kvProvider);
        assertThat(TodoStorageFactory.create(customType, Map.of())).isInstanceOf(KvTodoStorage.class);
    }

    @Test
    void testServiceLoaderDiscoverProviders_fileAndKvFound() {
        assertThat(TodoStorageFactory.hasProvider("file")).isTrue();
        assertThat(TodoStorageFactory.hasProvider("kv")).isTrue();
    }
}
