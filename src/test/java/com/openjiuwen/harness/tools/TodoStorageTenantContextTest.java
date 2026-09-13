package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class TodoStorageTenantContextTest {

    @TempDir
    Path tempDir;

    private FileTodoStorage storage;
    private TenantWorkspaceResolver resolver;
    private TenantContext tenantCtx;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        resolver = new TenantWorkspaceResolver(tempDir.toString());
        storage = new FileTodoStorage(tempDir, resolver);
        tenantCtx = TenantContext.builder().tenantId("acme").build();
        resolver.initializeTenantSpace(tenantCtx);
    }

    @Test
    void testLoadWithTenantContext() throws IOException {
        List<TodoItem> todos = new ArrayList<>();
        todos.add(TodoItem.create("Tenant Task"));
        Path tenantTodoDir = resolver.resolveTodoDir(tenantCtx);
        Path sessionDir = tenantTodoDir.resolve("session1");
        Files.createDirectories(sessionDir);
        Files.writeString(sessionDir.resolve("todo.json"),
            com.openjiuwen.core.common.security.JsonUtils.safeJsonDumps(todos, "[]"));

        List<TodoItem> loaded = storage.load("session1", tenantCtx);
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("Tenant Task");
    }

    @Test
    void testSaveWithTenantContext() throws IOException {
        List<TodoItem> todos = List.of(TodoItem.create("Tenant Save"));
        storage.save("session1", todos, tenantCtx);

        Path tenantTodoDir = resolver.resolveTodoDir(tenantCtx);
        Path file = tenantTodoDir.resolve("session1").resolve("todo.json");
        assertThat(Files.exists(file)).isTrue();

        List<TodoItem> loaded = storage.load("session1", tenantCtx);
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("Tenant Save");
    }

    @Test
    void testDeleteWithTenantContext() throws IOException {
        List<TodoItem> todos = List.of(TodoItem.create("Tenant Delete"));
        storage.save("session1", todos, tenantCtx);

        Path tenantTodoDir = resolver.resolveTodoDir(tenantCtx);
        Path sessionDir = tenantTodoDir.resolve("session1");
        assertThat(Files.exists(sessionDir)).isTrue();

        storage.delete("session1", tenantCtx);
        assertThat(Files.exists(sessionDir)).isFalse();
    }

    @Test
    void testLoadWithNullTenantContext() throws IOException {
        List<TodoItem> todos = new ArrayList<>();
        todos.add(TodoItem.create("Default Task"));
        storage.save("session1", todos);

        List<TodoItem> loaded = storage.load("session1", null);
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("Default Task");
    }

    @Test
    void testTenantContextClearedAfterCall() throws IOException {
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();

        storage.load("nonexistent-session", tenantCtx);
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();

        storage.save("clear-test", List.of(TodoItem.create("Clear")), tenantCtx);
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();

        storage.delete("nonexistent-session", tenantCtx);
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
    }
}
