package com.openjiuwen.core.session.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStoreProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void testFileProvider_typeName() {
        FileSessionStoreProvider provider = new FileSessionStoreProvider();
        assertThat(provider.typeName()).isEqualTo("file");
    }

    @Test
    void testFileProvider_createWithStorePath() {
        FileSessionStoreProvider provider = new FileSessionStoreProvider();
        Path storeFile = tempDir.resolve("custom_store.json");
        Map<String, Object> conf = Map.of("storePath", storeFile.toString());
        Store store = provider.createStore(conf);
        assertThat(store).isInstanceOf(FileStore.class);
        assertThat(((FileStore) store).getStorePath()).isEqualTo(storeFile.toAbsolutePath().normalize());
    }

    @Test
    void testFileProvider_createWithDefaultPath() {
        FileSessionStoreProvider provider = new FileSessionStoreProvider();
        Store store = provider.createStore(null);
        assertThat(store).isInstanceOf(FileStore.class);
        assertThat(((FileStore) store).getStorePath().getFileName().toString()).isEqualTo("session_store.json");
    }

    @Test
    void testFileProvider_createWithEmptyConf_defaultPath() {
        FileSessionStoreProvider provider = new FileSessionStoreProvider();
        Store store = provider.createStore(Map.of());
        assertThat(store).isInstanceOf(FileStore.class);
        assertThat(((FileStore) store).getStorePath().getFileName().toString()).isEqualTo("session_store.json");
    }

    @Test
    void testFileProvider_createStore_canReadAndWrite() {
        FileSessionStoreProvider provider = new FileSessionStoreProvider();
        Path storeFile = tempDir.resolve("test_store.json");
        Map<String, Object> conf = Map.of("storePath", storeFile.toString());
        Store store = provider.createStore(conf);
        store.write(Map.of("test_key", "test_value"));
        assertThat(store.read("test_key")).isEqualTo("test_value");
        assertThat(Files.exists(storeFile)).isTrue();
    }

    @Test
    void testFileProvider_createStore_withHashMapConf() {
        FileSessionStoreProvider provider = new FileSessionStoreProvider();
        Path storeFile = tempDir.resolve("hashmap_store.json");
        Map<String, Object> conf = new HashMap<>();
        conf.put("storePath", storeFile.toString());
        Store store = provider.createStore(conf);
        assertThat(store).isInstanceOf(FileStore.class);
    }
}
