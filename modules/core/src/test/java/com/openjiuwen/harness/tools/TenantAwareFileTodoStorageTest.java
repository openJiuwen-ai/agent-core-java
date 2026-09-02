package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class TenantAwareFileTodoStorageTest {

    @TempDir
    Path tempDir;

    private FileTodoStorage storage;
    private TenantWorkspaceResolver resolver;
    private TenantContext tenantA;
    private TenantContext tenantB;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        resolver = new TenantWorkspaceResolver(tempDir.toString());
        storage = new FileTodoStorage(tempDir, resolver);
        tenantA = TenantContext.builder().tenantId("alpha").build();
        tenantB = TenantContext.builder().tenantId("beta").build();
        resolver.initializeTenantSpace(tenantA);
        resolver.initializeTenantSpace(tenantB);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testWrite_noTenant_defaultPath() throws IOException {
        List<TodoItem> todos = List.of(TodoItem.create("Default Task"));
        storage.save("session1", todos);

        Path defaultFile = tempDir.resolve("session1").resolve("todo.json");
        assertThat(Files.exists(defaultFile)).isTrue();
        assertThat(Files.readString(defaultFile)).contains("Default Task");
    }

    @Test
    void testWrite_withTenant_tenantPath() throws IOException {
        TenantContextHolder.setCurrentTenant(tenantA);
        List<TodoItem> todos = List.of(TodoItem.create("Alpha Task"));
        storage.save("session1", todos);
        TenantContextHolder.clearCurrentTenant();

        Path tenantFile = resolver.resolveTodoDir(tenantA).resolve("session1").resolve("todo.json");
        assertThat(Files.exists(tenantFile)).isTrue();
        assertThat(Files.readString(tenantFile)).contains("Alpha Task");

        Path defaultFile = tempDir.resolve("session1").resolve("todo.json");
        assertThat(Files.exists(defaultFile)).isFalse();
    }

    @Test
    void testWrite_withTenant_pathContainsTenantsAndTid() throws IOException {
        TenantContextHolder.setCurrentTenant(tenantA);
        storage.save("session-x", List.of(TodoItem.create("Verify Path")));
        TenantContextHolder.clearCurrentTenant();

        Path expectedPath = tempDir.resolve("tenants").resolve("alpha").resolve("todo")
                .resolve("session-x").resolve("todo.json");
        assertThat(expectedPath.toAbsolutePath().normalize())
                .isEqualTo(resolver.resolveTodoDir(tenantA).resolve("session-x").resolve("todo.json").toAbsolutePath().normalize());
        assertThat(Files.exists(expectedPath)).isTrue();
    }

    @Test
    void testRead_noTenant_defaultPath() throws IOException {
        List<TodoItem> todos = List.of(TodoItem.create("Default Read"));
        storage.save("session1", todos);

        List<TodoItem> loaded = storage.load("session1");
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("Default Read");
    }

    @Test
    void testRead_withTenant_tenantPath() throws IOException {
        TenantContextHolder.setCurrentTenant(tenantA);
        List<TodoItem> todos = List.of(TodoItem.create("Alpha Read"));
        storage.save("session1", todos);
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(tenantA);
        List<TodoItem> loaded = storage.load("session1");
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("Alpha Read");
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testDelete_noTenant_defaultPath() throws IOException {
        List<TodoItem> todos = List.of(TodoItem.create("Default Delete"));
        storage.save("session1", todos);
        assertThat(Files.exists(tempDir.resolve("session1").resolve("todo.json"))).isTrue();

        storage.delete("session1");
        assertThat(Files.exists(tempDir.resolve("session1").resolve("todo.json"))).isFalse();
    }

    @Test
    void testDelete_withTenant_tenantPath() throws IOException {
        TenantContextHolder.setCurrentTenant(tenantA);
        storage.save("session1", List.of(TodoItem.create("Alpha Delete")));
        TenantContextHolder.clearCurrentTenant();

        Path tenantSessionDir = resolver.resolveTodoDir(tenantA).resolve("session1");
        assertThat(Files.exists(tenantSessionDir.resolve("todo.json"))).isTrue();

        TenantContextHolder.setCurrentTenant(tenantA);
        storage.delete("session1");
        TenantContextHolder.clearCurrentTenant();

        assertThat(Files.exists(tenantSessionDir)).isFalse();
    }

    @Test
    void testTenantIsolation_todoData() throws IOException {
        TenantContextHolder.setCurrentTenant(tenantA);
        storage.save("session1", List.of(TodoItem.create("Alpha Secret")));
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(tenantB);
        List<TodoItem> betaLoaded = storage.load("session1");
        assertThat(betaLoaded).isEmpty();
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(tenantA);
        List<TodoItem> alphaLoaded = storage.load("session1");
        assertThat(alphaLoaded).hasSize(1);
        assertThat(alphaLoaded.get(0).getContent()).isEqualTo("Alpha Secret");
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testTenantIsolation_differentTenantsDifferentFiles() throws IOException {
        TenantContextHolder.setCurrentTenant(tenantA);
        storage.save("session-shared", List.of(TodoItem.create("Alpha Data")));
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(tenantB);
        storage.save("session-shared", List.of(TodoItem.create("Beta Data")));
        TenantContextHolder.clearCurrentTenant();

        Path alphaFile = resolver.resolveTodoDir(tenantA).resolve("session-shared").resolve("todo.json");
        Path betaFile = resolver.resolveTodoDir(tenantB).resolve("session-shared").resolve("todo.json");
        assertThat(alphaFile.toAbsolutePath().normalize()).isNotEqualTo(betaFile.toAbsolutePath().normalize());
        assertThat(Files.readString(alphaFile)).contains("Alpha Data");
        assertThat(Files.readString(betaFile)).contains("Beta Data");
    }

    @Test
    void testTenantIsolation_deleteDoesNotAffectOtherTenant() throws IOException {
        TenantContextHolder.setCurrentTenant(tenantA);
        storage.save("session-del", List.of(TodoItem.create("Alpha Keep")));
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(tenantB);
        storage.save("session-del", List.of(TodoItem.create("Beta Data")));
        storage.delete("session-del");
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(tenantA);
        List<TodoItem> alphaLoaded = storage.load("session-del");
        assertThat(alphaLoaded).hasSize(1);
        assertThat(alphaLoaded.get(0).getContent()).isEqualTo("Alpha Keep");
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testRead_emptyTenant_returnsDefault() throws IOException {
        List<TodoItem> todos = List.of(TodoItem.create("Default Data"));
        storage.save("session1", todos);

        TenantContext emptyTenant = TenantContext.builder().tenantId("").build();
        assertThat(emptyTenant.isTenantAware()).isFalse();

        TenantContextHolder.setCurrentTenant(emptyTenant);
        List<TodoItem> loaded = storage.load("session1");
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("Default Data");
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testRead_noTenantContext_returnsDefaultWorkspace() throws IOException {
        List<TodoItem> todos = List.of(TodoItem.create("No Context Data"));
        storage.save("session-nc", todos);
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();

        List<TodoItem> loaded = storage.load("session-nc");
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("No Context Data");
    }

    @Test
    void testNoResolver_noTenantContext_fallsBackToWorkspace() throws IOException {
        FileTodoStorage noResolverStorage = new FileTodoStorage(tempDir);
        List<TodoItem> todos = List.of(TodoItem.create("No Resolver"));
        noResolverStorage.save("session-nores", todos);
        assertThat(Files.exists(tempDir.resolve("session-nores").resolve("todo.json"))).isTrue();

        List<TodoItem> loaded = noResolverStorage.load("session-nores");
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getContent()).isEqualTo("No Resolver");
    }
}
