package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantTodoIsolationTest {

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
    void tenantBWriteDoesNotOverwriteTenantA() throws Exception {
        TenantContextHolder.setCurrentTenant(tenant("tenant_a"));
        storage.save("session-1", List.of(TodoItem.create("task-a")));

        TenantContextHolder.setCurrentTenant(tenant("tenant_b"));
        storage.save("session-1", List.of(TodoItem.create("task-b")));

        TenantContextHolder.setCurrentTenant(tenant("tenant_a"));
        List<TodoItem> loaded = storage.load("session-1");
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("task-a");
    }

    @Test
    void deleteIsTenantScoped() throws Exception {
        TenantContextHolder.setCurrentTenant(tenant("tenant_a"));
        storage.save("session-1", List.of(TodoItem.create("task-a")));

        TenantContextHolder.setCurrentTenant(tenant("tenant_b"));
        storage.save("session-1", List.of(TodoItem.create("task-b")));
        storage.delete("session-1");

        TenantContextHolder.setCurrentTenant(tenant("tenant_a"));
        List<TodoItem> loaded = storage.load("session-1");
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("task-a");
    }

    @Test
    void noTenantCreateAndLoadWithoutPrefix() throws Exception {
        TenantContextHolder.clearCurrentTenant();
        storage.save("session-1", List.of(TodoItem.create("task-1")));
        assertThat(kvStore.get("session-1:todo")).isNotNull();

        TenantContextHolder.clearCurrentTenant();
        List<TodoItem> loaded = storage.load("session-1");
        assertThat(loaded).hasSize(1);
    }

    @Test
    void tenantDeleteDoesNotAffectNoTenantData() throws Exception {
        TenantContextHolder.clearCurrentTenant();
        storage.save("session-1", List.of(TodoItem.create("no-tenant-task")));

        TenantContextHolder.setCurrentTenant(tenant("tenant_a"));
        storage.delete("session-1");

        TenantContextHolder.clearCurrentTenant();
        List<TodoItem> loaded = storage.load("session-1");
        assertThat(loaded).hasSize(1);
    }
}
