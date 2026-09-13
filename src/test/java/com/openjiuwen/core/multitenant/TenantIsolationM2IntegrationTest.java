/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.session.store.FileStore;
import com.openjiuwen.core.session.store.SessionStoreFactory;
import com.openjiuwen.core.session.store.Store;
import com.openjiuwen.harness.tools.FileTodoStorage;
import com.openjiuwen.harness.tools.KvTodoStorage;
import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.harness.tools.TodoStorage;
import com.openjiuwen.harness.tools.TodoStorageFactory;
import com.openjiuwen.harness.tools.TodoStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@DisplayName("ST-M2: Todo + FileStore tenant isolation + SPI integration tests")
class TenantIsolationM2IntegrationTest {

    @TempDir
    Path baseDir;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    @DisplayName("FileTodoStorage tenant path isolation: two tenants store todos in separate directories")
    void testFileTodoStorage_tenantPathIsolation() throws Exception {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        TenantContext tenantA = TenantContext.builder().tenantId("m2_file_a").build();
        TenantContext tenantB = TenantContext.builder().tenantId("m2_file_b").build();

        resolver.initializeTenantSpace(tenantA);
        resolver.initializeTenantSpace(tenantB);

        FileTodoStorage storage = new FileTodoStorage(baseDir, resolver);

        TodoItem itemA = TodoItem.builder().id("task-a-1").content("tenant A task").status(TodoStatus.TODO).build();
        TodoItem itemB = TodoItem.builder().id("task-b-1").content("tenant B task").status(TodoStatus.TODO).build();

        try {
            TenantContextHolder.setCurrentTenant(tenantA);
            storage.save("session-a", List.of(itemA));
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        try {
            TenantContextHolder.setCurrentTenant(tenantB);
            storage.save("session-b", List.of(itemB));
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        Path tenantATodoDir = baseDir.resolve("tenants").resolve("m2_file_a").resolve("todo");
        Path tenantBTodoDir = baseDir.resolve("tenants").resolve("m2_file_b").resolve("todo");
        assertThat(Files.exists(tenantATodoDir)).isTrue();
        assertThat(Files.exists(tenantBTodoDir)).isTrue();
        assertThat(tenantATodoDir).isNotEqualTo(tenantBTodoDir);

        try {
            TenantContextHolder.setCurrentTenant(tenantA);
            List<TodoItem> loadedA = storage.load("session-a");
            assertThat(loadedA).hasSize(1);
            assertThat(loadedA.get(0).getContent()).isEqualTo("tenant A task");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        try {
            TenantContextHolder.setCurrentTenant(tenantB);
            List<TodoItem> loadedB = storage.load("session-b");
            assertThat(loadedB).hasSize(1);
            assertThat(loadedB.get(0).getContent()).isEqualTo("tenant B task");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        try {
            TenantContextHolder.setCurrentTenant(tenantA);
            List<TodoItem> crossLoad = storage.load("session-b");
            assertThat(crossLoad).isEmpty();
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Test
    @DisplayName("KvTodoStorage tenant key isolation: SPI pipeline resolves tenant-prefixed KV keys")
    void testKvTodoStorage_tenantKeyIsolation() throws Exception {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        KvTodoStorage storage = new KvTodoStorage(kvStore);

        TenantContext tenantA = TenantContext.builder().tenantId("m2_kv_a").build();
        TenantContext tenantB = TenantContext.builder().tenantId("m2_kv_b").build();

        TodoItem itemA = TodoItem.builder().id("kv-a-1").content("kv tenant A").status(TodoStatus.TODO).build();
        TodoItem itemB = TodoItem.builder().id("kv-b-1").content("kv tenant B").status(TodoStatus.TODO).build();

        try {
            TenantContextHolder.setCurrentTenant(tenantA);
            storage.save("session-kv", List.of(itemA));
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        try {
            TenantContextHolder.setCurrentTenant(tenantB);
            storage.save("session-kv", List.of(itemB));
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        assertThat(kvStore.getByPrefix("m2_kv_a:")).isNotEmpty();
        assertThat(kvStore.getByPrefix("m2_kv_b:")).isNotEmpty();

        try {
            TenantContextHolder.setCurrentTenant(tenantA);
            List<TodoItem> loadedA = storage.load("session-kv");
            assertThat(loadedA).hasSize(1);
            assertThat(loadedA.get(0).getContent()).isEqualTo("kv tenant A");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        try {
            TenantContextHolder.setCurrentTenant(tenantB);
            List<TodoItem> loadedB = storage.load("session-kv");
            assertThat(loadedB).hasSize(1);
            assertThat(loadedB.get(0).getContent()).isEqualTo("kv tenant B");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Test
    @DisplayName("TodoStorage SPI creates file and kv modes, both work with tenant context")
    void testTodoStorageFactory_spiCreation() throws Exception {
        assertThat(TodoStorageFactory.hasProvider("file")).isTrue();
        assertThat(TodoStorageFactory.hasProvider("kv")).isTrue();

        TodoStorage fileStorage = TodoStorageFactory.create("file", Map.of("basePath", baseDir.toString()));
        assertThat(fileStorage).isInstanceOf(FileTodoStorage.class);

        InMemoryKVStore kvStore = new InMemoryKVStore();
        TodoStorage kvStorage = TodoStorageFactory.create("kv", Map.of("sharedKvStore", kvStore));
        assertThat(kvStorage).isInstanceOf(KvTodoStorage.class);

        TenantContext tenantCtx = TenantContext.builder().tenantId("m2_spi").build();
        TodoItem item = TodoItem.builder().id("spi-task-1").content("spi task").status(TodoStatus.TODO).build();

        try {
            TenantContextHolder.setCurrentTenant(tenantCtx);
            kvStorage.save("spi-session", List.of(item));
            List<TodoItem> loaded = kvStorage.load("spi-session");
            assertThat(loaded).hasSize(1);
            assertThat(loaded.get(0).getContent()).isEqualTo("spi task");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Test
    @DisplayName("FileStore tenant path isolation: session files stored in tenant workspace directory")
    void testFileStore_tenantPathIsolation() throws Exception {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        TenantContext tenantA = TenantContext.builder().tenantId("m2_store_a").build();
        TenantContext tenantB = TenantContext.builder().tenantId("m2_store_b").build();

        resolver.initializeTenantSpace(tenantA);
        resolver.initializeTenantSpace(tenantB);

        FileStore storeA = new FileStore(Path.of("session_store.json"), resolver);
        FileStore storeB = new FileStore(Path.of("session_store.json"), resolver);

        try {
            TenantContextHolder.setCurrentTenant(tenantA);
            storeA.write(Map.of("key_a", "value_a"));
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        try {
            TenantContextHolder.setCurrentTenant(tenantB);
            storeB.write(Map.of("key_b", "value_b"));
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        Path tenantAWorkspace = baseDir.resolve("tenants").resolve("m2_store_a");
        Path tenantBWorkspace = baseDir.resolve("tenants").resolve("m2_store_b");
        assertThat(Files.exists(tenantAWorkspace.resolve("session_store.json"))).isTrue();
        assertThat(Files.exists(tenantBWorkspace.resolve("session_store.json"))).isTrue();

        try {
            TenantContextHolder.setCurrentTenant(tenantA);
            assertThat(storeA.read("key_a")).isEqualTo("value_a");
            assertThat(storeA.read("key_b")).isNull();
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        try {
            TenantContextHolder.setCurrentTenant(tenantB);
            assertThat(storeB.read("key_b")).isEqualTo("value_b");
            assertThat(storeB.read("key_a")).isNull();
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Test
    @DisplayName("SessionStore SPI creates file provider, manually created tenant-aware FileStore isolates data")
    void testSessionStoreFactory_andTenantAwareFileStore() throws Exception {
        assertThat(SessionStoreFactory.hasProvider("file")).isTrue();

        Store plainStore = SessionStoreFactory.create("file", Map.of(
                "storePath", baseDir.resolve("plain_store.json").toString()));
        assertThat(plainStore).isInstanceOf(FileStore.class);

        plainStore.write(Map.of("plain_key", "plain_value"));
        assertThat(plainStore.read("plain_key")).isEqualTo("plain_value");

        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        TenantContext tenantCtx = TenantContext.builder().tenantId("m2_session_spi").build();
        resolver.initializeTenantSpace(tenantCtx);

        FileStore tenantStore = new FileStore(Path.of("tenant_store.json"), resolver);

        try {
            TenantContextHolder.setCurrentTenant(tenantCtx);
            tenantStore.write(Map.of("tenant_key", "tenant_value"));
            assertThat(tenantStore.read("tenant_key")).isEqualTo("tenant_value");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        assertThat(tenantStore.read("tenant_key")).isNull();
    }
}
