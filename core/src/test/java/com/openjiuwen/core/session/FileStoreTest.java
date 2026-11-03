
package com.openjiuwen.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.session.store.FileStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

class FileStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void fileStoreShouldPersistAndReadNestedValues() throws Exception {
        Path storePath = tempDir.resolve("session.json");
        FileStore store = new FileStore(storePath);

        store.write(Map.of("user.name", "gallon", "user.preferences.theme", "dark"));

        assertThat(store.read("user.name")).isEqualTo("gallon");
        assertThat(store.read("user.preferences.theme")).isEqualTo("dark");
        assertThat(Files.readString(storePath)).contains("gallon");
    }

    @Test
    void fileStoreShouldMergeUpdatesAndDeleteNulls() {
        FileStore store = new FileStore(tempDir.resolve("data/store.json"));
        store.write(Map.of("profile.name", "alice", "profile.language", "en"));
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("profile.language", "zh");
        update.put("profile.name", null);
        store.write(update);

        assertThat(store.read("profile.language")).isEqualTo("zh");
        assertThat(store.read("profile.name")).isNull();
        assertThat(store.getDataSnapshot()).containsKey("profile");
    }

    @Test
    void fileStoreShouldHandleMissingOrBlankFile() throws Exception {
        Path storePath = tempDir.resolve("blank.json");
        Files.createDirectories(storePath.getParent());
        Files.writeString(storePath, "");
        FileStore store = new FileStore(storePath);

        assertThat(store.read("missing")).isNull();
        store.write(Map.of("count", 1));
        assertThat(store.read("count")).isEqualTo(1);
    }
}
