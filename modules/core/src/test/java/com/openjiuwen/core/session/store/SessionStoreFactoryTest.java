package com.openjiuwen.core.session.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionStoreFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void testCreate_fileType() {
        Path storeFile = tempDir.resolve("session_store.json");
        Map<String, Object> conf = Map.of("storePath", storeFile.toString());
        Store store = SessionStoreFactory.create("file", conf);
        assertThat(store).isInstanceOf(FileStore.class);
    }

    @Test
    void testCreate_fileType_canWriteAndRead() {
        Path storeFile = tempDir.resolve("factory_test_store.json");
        Map<String, Object> conf = Map.of("storePath", storeFile.toString());
        Store store = SessionStoreFactory.create("file", conf);
        store.write(Map.of("factory_key", "factory_value"));
        assertThat(store.read("factory_key")).isEqualTo("factory_value");
        assertThat(Files.exists(storeFile)).isTrue();
    }

    @Test
    void testCreate_fileType_withNullConf_usesDefault() {
        Store store = SessionStoreFactory.create("file", null);
        assertThat(store).isInstanceOf(FileStore.class);
        assertThat(((FileStore) store).getStorePath().getFileName().toString()).isEqualTo("session_store.json");
    }

    @Test
    void testHasProvider_file() {
        assertThat(SessionStoreFactory.hasProvider("file")).isTrue();
    }

    @Test
    void testHasProvider_unknown() {
        assertThat(SessionStoreFactory.hasProvider("unknown")).isFalse();
    }

    @Test
    void testHasProvider_null() {
        assertThat(SessionStoreFactory.hasProvider(null)).isFalse();
    }

    @Test
    void testCreate_unknownThrows() {
        assertThatThrownBy(() -> SessionStoreFactory.create("unknown", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void testRegister_customProvider() {
        SessionStoreProvider customProvider = new SessionStoreProvider() {
            @Override
            public String typeName() {
                return "custom_ss_test";
            }

            @Override
            public Store createStore(Map<String, Object> conf) {
                return new MemoryStore();
            }
        };
        SessionStoreFactory.register("custom_ss_test", customProvider);
        assertThat(SessionStoreFactory.hasProvider("custom_ss_test")).isTrue();
        Store store = SessionStoreFactory.create("custom_ss_test", Map.of());
        assertThat(store).isInstanceOf(MemoryStore.class);
    }

    @Test
    void testRegister_overwriteExistingProvider() {
        String customType = "overwrite_ss_test";
        SessionStoreProvider firstProvider = new SessionStoreProvider() {
            @Override
            public String typeName() { return customType; }
            @Override
            public Store createStore(Map<String, Object> conf) { return new MemoryStore(); }
        };
        SessionStoreProvider secondProvider = new SessionStoreProvider() {
            @Override
            public String typeName() { return customType; }
            @Override
            public Store createStore(Map<String, Object> conf) {
                Path storeFile = tempDir.resolve("overwritten.json");
                return new FileStore(storeFile);
            }
        };
        SessionStoreFactory.register(customType, firstProvider);
        assertThat(SessionStoreFactory.create(customType, Map.of())).isInstanceOf(MemoryStore.class);
        SessionStoreFactory.register(customType, secondProvider);
        assertThat(SessionStoreFactory.create(customType, Map.of())).isInstanceOf(FileStore.class);
    }

    @Test
    void testServiceLoaderDiscoverProviders_fileProviderFound() {
        assertThat(SessionStoreFactory.hasProvider("file")).isTrue();
        Store store = SessionStoreFactory.create("file", Map.of("storePath", tempDir.resolve("sl_test.json").toString()));
        assertThat(store).isInstanceOf(FileStore.class);
    }
}
