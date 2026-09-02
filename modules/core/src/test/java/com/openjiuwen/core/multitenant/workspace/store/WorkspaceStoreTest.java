package com.openjiuwen.core.multitenant.workspace.store;

import com.openjiuwen.core.multitenant.workspace.WorkspaceStore;
import com.openjiuwen.core.multitenant.workspace.WorkspaceStoreFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceStoreTest {

    @TempDir
    Path baseDir;

    LocalWorkspaceStore localStore;
    ObjectStorageWorkspaceStore obsStore;

    @BeforeEach
    void setup() {
        localStore = new LocalWorkspaceStore(baseDir.toString());
        obsStore = new ObjectStorageWorkspaceStore("obs", "deepagent-ws", "https://obs.example.com");
    }

    @Test
    void testLocalStore_tierName() {
        assertThat(localStore.tierName()).isEqualTo("local");
    }

    @Test
    void testLocalStore_resolvePath() {
        Path result = localStore.resolvePath("tenants/abc123", "skills");
        assertThat(result.toString()).contains("tenants").contains("abc123").contains("skills");
        assertThat(result).isAbsolute();
    }

    @Test
    void testLocalStore_resolveDefaultPath() {
        Path result = localStore.resolveDefaultPath("skills");
        assertThat(result).isEqualTo(baseDir.resolve("skills").toAbsolutePath().normalize());
    }

    @Test
    void testLocalStore_createDirectories() {
        localStore.createDirectories("tenants/abc123");
        assertThat(Files.isDirectory(localStore.resolvePath("tenants/abc123", "skills"))).isTrue();
        assertThat(Files.isDirectory(localStore.resolvePath("tenants/abc123", "tmp"))).isTrue();
        assertThat(Files.isDirectory(localStore.resolvePath("tenants/abc123", "checkpoints"))).isTrue();
        assertThat(Files.isDirectory(localStore.resolvePath("tenants/abc123", "team_memory"))).isTrue();
        assertThat(Files.isDirectory(localStore.resolvePath("tenants/abc123", "todo"))).isTrue();
    }

    @Test
    void testObsStore_tierName() {
        assertThat(obsStore.tierName()).isEqualTo("obs");
    }

    @Test
    void testObsStore_resolvePath() {
        Path result = obsStore.resolvePath("tenants/abc123", "skills");
        assertThat(result.toString()).contains("tenants").contains("abc123").contains("skills");
    }

    @Test
    void testObsStore_resolveDefaultPath() {
        Path result = obsStore.resolveDefaultPath("skills");
        assertThat(result.toString()).isEqualTo("skills");
    }

    @Test
    void testWorkspaceStoreFactory_hasLocalProvider() {
        assertThat(WorkspaceStoreFactory.hasProvider("local")).isTrue();
    }

    @Test
    void testWorkspaceStoreFactory_createLocal() {
        WorkspaceStore store = WorkspaceStoreFactory.create("local", Map.of("basePath", baseDir.toString()));
        assertThat(store.tierName()).isEqualTo("local");
    }

    @Test
    void testWorkspaceStoreFactory_unknownProvider_throws() {
        assertThatThrownBy(() -> WorkspaceStoreFactory.create("unknown_tier", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No workspace store provider registered");
    }

    @Test
    void testLocalWorkspaceStoreProvider_typeName() {
        LocalWorkspaceStoreProvider provider = new LocalWorkspaceStoreProvider();
        assertThat(provider.typeName()).isEqualTo("local");
    }

    @Test
    void testObsWorkspaceStoreProvider_typeName() {
        ObsWorkspaceStoreProvider provider = new ObsWorkspaceStoreProvider();
        assertThat(provider.typeName()).isEqualTo("obs");
    }

    @Test
    void testS3WorkspaceStoreProvider_typeName() {
        S3WorkspaceStoreProvider provider = new S3WorkspaceStoreProvider();
        assertThat(provider.typeName()).isEqualTo("s3");
    }

    @Test
    void testHdfsWorkspaceStoreProvider_typeName() {
        HdfsWorkspaceStoreProvider provider = new HdfsWorkspaceStoreProvider();
        assertThat(provider.typeName()).isEqualTo("hdfs");
    }
}
