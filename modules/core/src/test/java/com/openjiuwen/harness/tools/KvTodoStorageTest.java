package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;

class KvTodoStorageTest {

    private InMemoryKVStore kvStore;
    private KvTodoStorage storage;

    @BeforeEach
    void setUp() {
        kvStore = new InMemoryKVStore();
        storage = new KvTodoStorage(kvStore);
        TenantContextHolder.clearCurrentTenant();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
    }

    private TenantContext tenant(String tenantId) {
        return TenantContext.builder().tenantId(tenantId).build();
    }

    @Test
    void loadNoDataReturnsEmptyList() throws IOException {
        List<TodoItem> items = storage.load("session-1");
        assertThat(items).isEmpty();
    }

    @Test
    void saveThenLoadReturnsSavedItems() throws IOException {
        List<TodoItem> todos = new ArrayList<>();
        todos.add(TodoItem.create("Task A"));
        todos.add(TodoItem.create("Task B"));

        storage.save("session-1", todos);
        List<TodoItem> loaded = storage.load("session-1");

        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).getContent()).isEqualTo("Task A");
        assertThat(loaded.get(1).getContent()).isEqualTo("Task B");
    }

    @Test
    void saveOverwritesPreviousData() throws IOException {
        storage.save("session-1", List.of(TodoItem.create("Old Task")));
        List<TodoItem> firstLoad = storage.load("session-1");
        assertThat(firstLoad).hasSize(1);
        assertThat(firstLoad.get(0).getContent()).isEqualTo("Old Task");

        storage.save("session-1", List.of(TodoItem.create("New Task 1"), TodoItem.create("New Task 2")));
        List<TodoItem> secondLoad = storage.load("session-1");
        assertThat(secondLoad).hasSize(2);
        assertThat(secondLoad.get(0).getContent()).isEqualTo("New Task 1");
        assertThat(secondLoad.get(1).getContent()).isEqualTo("New Task 2");
    }

    @Test
    void deleteRemovesData() throws IOException {
        storage.save("session-1", List.of(TodoItem.create("Task")));
        assertThat(storage.load("session-1")).hasSize(1);

        storage.delete("session-1");
        assertThat(storage.load("session-1")).isEmpty();
    }

    @Test
    void loadNonexistentSessionReturnsEmptyList() throws IOException {
        List<TodoItem> items = storage.load("nonexistent-session");
        assertThat(items).isEmpty();
    }

    @Test
    void tenantIsolationDataInvisibleToOtherTenant() throws IOException {
        TenantContextHolder.setCurrentTenant(tenant("tenant_a"));
        storage.save("session-1", List.of(TodoItem.create("Tenant A Task")));

        TenantContextHolder.setCurrentTenant(tenant("tenant_b"));
        List<TodoItem> loaded = storage.load("session-1");
        assertThat(loaded).isEmpty();
    }

    @Test
    void tenantIsolationTenantCanSeeOwnData() throws IOException {
        TenantContextHolder.setCurrentTenant(tenant("tenant_a"));
        storage.save("session-1", List.of(TodoItem.create("Tenant A Task")));

        List<TodoItem> loaded = storage.load("session-1");
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("Tenant A Task");
    }

    @Test
    void noTenantBackwardCompatKeyStoredWithoutPrefix() throws IOException {
        storage.save("session-1", List.of(TodoItem.create("Task")));

        assertThat(kvStore.get("session-1:todo")).isNotNull();
    }

    @Test
    void withTenantKeyStoredWithPrefix() throws IOException {
        TenantContextHolder.setCurrentTenant(tenant("tenant_a"));
        storage.save("session-1", List.of(TodoItem.create("Task")));

        assertThat(kvStore.get("tenant_a:session-1:todo")).isNotNull();
    }

    @Test
    void loadBlankJsonReturnsEmptyList() throws IOException {
        kvStore.set("session-1:todo", "");
        List<TodoItem> loaded = storage.load("session-1");
        assertThat(loaded).isEmpty();
    }

    @Test
    void constructorNullKvStoreThrowsNullPointerException() {
        assertThatThrownBy(() -> new KvTodoStorage(null))
                .isInstanceOf(NullPointerException.class);
    }
}
