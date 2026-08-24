package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.memory.support.TestInMemoryKVStore;
import com.openjiuwen.spi.store.BaseKVStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

class TodoStorageProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void testFileProvider_typeName() {
        FileTodoStorageProvider provider = new FileTodoStorageProvider();
        assertThat(provider.typeName()).isEqualTo("file");
    }

    @Test
    void testFileProvider_createWithStorePath() {
        FileTodoStorageProvider provider = new FileTodoStorageProvider();
        Map<String, Object> conf = new HashMap<>();
        conf.put("basePath", tempDir.toString());
        TodoStorage storage = provider.create(conf);
        assertThat(storage).isInstanceOf(FileTodoStorage.class);
    }

    @Test
    void testKvProvider_typeName() {
        KvTodoStorageProvider provider = new KvTodoStorageProvider();
        assertThat(provider.typeName()).isEqualTo("kv");
    }

    @Test
    void testKvProvider_createWithSharedKvStore() {
        KvTodoStorageProvider provider = new KvTodoStorageProvider();
        BaseKVStore sharedKvStore = new TestInMemoryKVStore();
        Map<String, Object> conf = new HashMap<>();
        conf.put("sharedKvStore", sharedKvStore);
        TodoStorage storage = provider.create(conf);
        assertThat(storage).isInstanceOf(KvTodoStorage.class);
    }

    @Test
    void testKvProvider_createWithConf() {
        KvTodoStorageProvider provider = new KvTodoStorageProvider();
        Map<String, Object> conf = new HashMap<>();
        conf.put("kvStoreType", "in_memory");
        TodoStorage storage = provider.create(conf);
        assertThat(storage).isInstanceOf(KvTodoStorage.class);
    }

    @Test
    void testFileProvider_createWithNullConf_defaultPath() {
        FileTodoStorageProvider provider = new FileTodoStorageProvider();
        TodoStorage storage = provider.create(null);
        assertThat(storage).isInstanceOf(FileTodoStorage.class);
    }

    @Test
    void testFileProvider_createWithEmptyConf_defaultPath() {
        FileTodoStorageProvider provider = new FileTodoStorageProvider();
        TodoStorage storage = provider.create(Map.of());
        assertThat(storage).isInstanceOf(FileTodoStorage.class);
    }

    @Test
    void testKvProvider_createWithNullConf_defaultInMemory() {
        KvTodoStorageProvider provider = new KvTodoStorageProvider();
        TodoStorage storage = provider.create(null);
        assertThat(storage).isInstanceOf(KvTodoStorage.class);
    }

    @Test
    void testKvProvider_createWithKvStoreConf() {
        KvTodoStorageProvider provider = new KvTodoStorageProvider();
        Map<String, Object> kvStoreConf = new HashMap<>();
        Map<String, Object> conf = new HashMap<>();
        conf.put("kvStoreType", "in_memory");
        conf.put("kvStoreConf", kvStoreConf);
        TodoStorage storage = provider.create(conf);
        assertThat(storage).isInstanceOf(KvTodoStorage.class);
    }

    @Test
    void testKvProvider_sharedKvStorePriorityOverConf() {
        KvTodoStorageProvider provider = new KvTodoStorageProvider();
        BaseKVStore sharedKvStore = new TestInMemoryKVStore();
        Map<String, Object> conf = new HashMap<>();
        conf.put("sharedKvStore", sharedKvStore);
        conf.put("kvStoreType", "in_memory");
        TodoStorage storage = provider.create(conf);
        assertThat(storage).isInstanceOf(KvTodoStorage.class);
    }
}
